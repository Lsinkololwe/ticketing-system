package com.pml.shared.security.publicop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.publicop.PublicOperationRules.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007-R9 · a tokenless body is admitted only as a single allowlisted query within the limits")
class PublicOperationRulesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final PublicOperationPolicy policy = PublicOperationPolicy.of("t", Set.of("events", "event"))
            .withEntities(Map.of("Organization", Set.of("publishedEventCount")));
    private final PublicOperationRules rules = new PublicOperationRules(policy, JSON);

    private Verdict judge(String query) {
        return judge(query, null);
    }

    private Verdict judge(String query, String variablesJson) {
        String body = "{\"query\":" + JSON.valueToTree(query)
                + (variablesJson == null ? "" : ",\"variables\":" + variablesJson) + "}";
        return rules.judge(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an allowlisted root field, with variables, aliases and nested fragments, is admitted")
    void admitted() {
        assertThat(judge("query Q($id: ID!) { a: event(id: $id) { ...F } events { id } } fragment F on Event { id tiers { ...G } } fragment G on Tier { id }"))
                .isEqualTo(Verdict.ALLOWED);
    }

    @Test
    @DisplayName("everything outside the allowlist is refused, each for its own reason")
    void refused() {
        assertThat(judge("{ me { id } }")).isEqualTo(Verdict.ROOT_NOT_ALLOWED);
        assertThat(judge("{ events { id } me { id } }")).isEqualTo(Verdict.ROOT_NOT_ALLOWED);
        assertThat(judge("mutation { events }")).isEqualTo(Verdict.NOT_A_QUERY);
        assertThat(judge("subscription { events }")).isEqualTo(Verdict.NOT_A_QUERY);
        assertThat(judge("{ __schema { types { name } } }")).isEqualTo(Verdict.INTROSPECTION);
        assertThat(judge("{ __type(name: \"Event\") { name } }")).isEqualTo(Verdict.INTROSPECTION);
        assertThat(judge("{ __typename }")).isEqualTo(Verdict.INTROSPECTION);
        assertThat(judge("{ _service { sdl } }")).isEqualTo(Verdict.ROOT_NOT_ALLOWED);
        assertThat(judge("{ ...F } fragment F on Query { events { id } }")).isEqualTo(Verdict.ROOT_FRAGMENT);
        assertThat(judge("{ ... on Query { events { id } } }")).isEqualTo(Verdict.ROOT_FRAGMENT);
        assertThat(judge("query A { events { id } } query B { me { id } }")).isEqualTo(Verdict.MULTIPLE_OPERATIONS);
        assertThat(judge("not graphql {{")).isEqualTo(Verdict.UNPARSEABLE);
        assertThat(judge("{ }")).isEqualTo(Verdict.UNPARSEABLE);
    }

    @Test
    @DisplayName("batches, persisted queries, empty and non-object bodies are refused")
    void shapes() {
        assertThat(rules.judge("[{\"query\":\"{ events { id } }\"}]".getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.NOT_AN_OBJECT);
        assertThat(rules.judge("{\"query\":\"{ events { id } }\",\"extensions\":{\"persistedQuery\":{\"version\":1}}}"
                .getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.PERSISTED);
        assertThat(rules.judge("{\"operationName\":\"x\"}".getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.NO_QUERY);
        assertThat(rules.judge(new byte[0])).isEqualTo(Verdict.EMPTY);
        assertThat(rules.judge("null".getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.NOT_AN_OBJECT);
    }

    @Test
    @DisplayName("depth and field count are limited through fragments, and a fragment cycle or unknown fragment is refused")
    void limits() {
        StringBuilder deep = new StringBuilder("{ events ");
        for (int i = 0; i < policy.maxDepth() + 1; i++) {
            deep.append("{ a ");
        }
        deep.append("{ id }");
        deep.append(" }".repeat(policy.maxDepth() + 1)).append(" }");
        assertThat(judge(deep.toString())).isEqualTo(Verdict.TOO_DEEP);

        StringBuilder wide = new StringBuilder("{ events { ");
        for (int i = 0; i < policy.maxNodes() + 1; i++) {
            wide.append("f").append(i).append(' ');
        }
        assertThat(judge(wide.append("} }").toString())).isEqualTo(Verdict.TOO_LARGE);

        // a fragment bomb: each level spreads the next one twice, so expansion is exponential
        StringBuilder bomb = new StringBuilder("{ events { ...F0 } } ");
        for (int i = 0; i < 8; i++) {
            bomb.append("fragment F").append(i).append(" on E { ...F").append(i + 1).append(" ...F").append(i + 1)
                    .append(" a b c d e f g h } ");
        }
        bomb.append("fragment F8 on E { id }");
        assertThat(judge(bomb.toString())).isEqualTo(Verdict.TOO_LARGE);

        assertThat(judge("{ events { ...Loop } } fragment Loop on E { ...Loop }")).isEqualTo(Verdict.BAD_FRAGMENT);
        assertThat(judge("{ events { ...Missing } }")).isEqualTo(Verdict.BAD_FRAGMENT);
    }

    @Test
    @DisplayName("more fragment definitions than the policy allows is refused as too large even when each is tiny")
    void fragmentCountLimit() {
        int tooMany = policy.maxFragments() + 1;
        StringBuilder spreads = new StringBuilder();
        StringBuilder definitions = new StringBuilder();
        for (int i = 0; i < tooMany; i++) {
            spreads.append("...F").append(i).append(' ');
            definitions.append("fragment F").append(i).append(" on Event { id } ");
        }
        String query = "{ events { " + spreads + "} } " + definitions;

        assertThat(policy.maxNodes()).as("the node counter cannot be what trips this").isGreaterThan(tooMany);
        assertThat(judge(query)).isEqualTo(Verdict.TOO_LARGE);

        String atTheLimit = query.replace("...F" + (tooMany - 1) + " ", "")
                .replace("fragment F" + (tooMany - 1) + " on Event { id } ", "");
        assertThat(judge(atTheLimit)).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    @DisplayName("_entities is admitted only for the named entity type and leaf fields, over bare representations")
    void entities() {
        String query = "query($representations:[_Any!]!){_entities(representations:$representations){...on Organization{publishedEventCount}}}";
        assertThat(judge(query, "{\"representations\":[{\"__typename\":\"Organization\",\"id\":\"o1\"}]}")).isEqualTo(Verdict.ALLOWED);
        assertThat(judge(query, "{\"representations\":[{\"__typename\":\"User\",\"id\":\"u1\"}]}")).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        assertThat(judge(query, "{\"representations\":[{\"__typename\":\"Organization\",\"id\":\"o1\",\"email\":\"x\"}]}"))
                .isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        assertThat(judge(query, null)).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        assertThat(judge("query($r:[_Any!]!){_entities(representations:$r){...on Organization{ownerId}}}",
                "{\"r\":[]}".replace("\"r\"", "\"representations\""))).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        assertThat(judge("query($r:[_Any!]!){_entities(representations:$r){...on User{id}}}",
                "{\"representations\":[]}")).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        assertThat(judge("query($r:[_Any!]!){_entities(representations:$r){...on Organization{owner{id}}} events{id}}",
                "{\"representations\":[]}")).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
        // a service with no entity allowlist refuses _entities outright
        PublicOperationRules closed = new PublicOperationRules(PublicOperationPolicy.of("t", Set.of("events")), JSON);
        assertThat(closed.judge(("{\"query\":" + JSON.valueToTree(query) + ",\"variables\":{\"representations\":[]}}")
                .getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
    }
}
