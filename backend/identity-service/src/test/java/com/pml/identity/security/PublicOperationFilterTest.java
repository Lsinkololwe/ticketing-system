package com.pml.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("L1")
@Tag("ET-ADM-002")
@DisplayName("ET-ADM-002-R10 · a tokenless caller reaches only the allowlisted query, rate limited")
class PublicOperationFilterTest {

    private ReactiveStringRedisTemplate redis;
    private PublicOperationFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.just(1L));
        filter = new PublicOperationFilter(redis, new ObjectMapper());
    }

    private static String body(String query) {
        return "{\"query\":" + new ObjectMapper().valueToTree(query) + "}";
    }

    private static MockServerWebExchange post(String json, String... header) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post("/graphql")
                .contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "9.9.9.9, 10.0.0.7");
        if (header.length == 2) {
            builder.header(header[0], header[1]);
        }
        return MockServerWebExchange.from(builder.body(json));
    }

    /** Runs the filter; returns whether the downstream chain saw the exchange marked public. */
    private Boolean run(MockServerWebExchange exchange) {
        AtomicReference<Boolean> marked = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            marked.set(Boolean.TRUE.equals(ex.getAttribute(PublicOperationFilter.PUBLIC_MARK)));
            return Mono.empty();
        }).block();
        return marked.get();
    }

    @Test
    @DisplayName("the public query is marked public")
    void publicQuery() {
        assertThat(run(post(body("query Rules { publicPlatformRules { reservationHoldMinutes currency } }"))))
                .isTrue();
    }

    @Test
    @DisplayName("anything else is not marked: other queries, mixed selections, mutations, entities, introspection, batches")
    void everythingElse() {
        for (String query : new String[] {
                "{ platformRules { commissionRate } }",
                "{ publicPlatformRules { currency } me { id } }",
                "{ _entities(representations: []) { __typename } }",
                "{ _service { sdl } }",
                "{ __schema { types { name } } }",
                "mutation { logout }",
                "subscription { publicPlatformRules { currency } }",
                "{ ...F } fragment F on Query { publicPlatformRules { currency } }",
                "query A { publicPlatformRules { currency } } query B { me { id } }",
                "not graphql {{" }) {
            assertThat(run(post(body(query)))).as(query).isFalse();
        }
        assertThat(run(post("[" + body("{ publicPlatformRules { currency } }") + "]"))).as("batch").isFalse();
        assertThat(run(post("{\"query\":\"{ publicPlatformRules { currency } }\","
                + "\"extensions\":{\"persistedQuery\":{\"version\":1}}}"))).as("persisted").isFalse();
        assertThat(run(post(""))).as("empty").isFalse();
    }

    @Test
    @DisplayName("the router may resolve the organization's verified badge, and no other organization field or entity")
    void organizationBadge() {
        String badge = "query($representations:[_Any!]!){_entities(representations:$representations){...on Organization{verified}}}";
        String vars = "\"variables\":{\"representations\":[{\"__typename\":\"Organization\",\"id\":\"o1\"}]}";
        assertThat(run(post("{\"query\":" + new ObjectMapper().valueToTree(badge) + "," + vars + "}"))).isTrue();
        String taxId = badge.replace("verified", "taxId");
        assertThat(run(post("{\"query\":" + new ObjectMapper().valueToTree(taxId) + "," + vars + "}"))).isFalse();
        String user = badge.replace("Organization{verified}", "User{email}");
        assertThat(run(post("{\"query\":" + new ObjectMapper().valueToTree(user) + "," + vars + "}"))).isFalse();
    }

    @Test
    @DisplayName("a request that carries a token is left alone, and the body still reaches the chain")
    void tokenIsUntouched() {
        assertThat(run(post(body("{ publicPlatformRules { currency } }"), HttpHeaders.AUTHORIZATION, "Bearer x")))
                .isFalse();
    }

    @Test
    @DisplayName("the body is replayed to the chain after the filter has read it")
    void bodyReplayed() {
        String json = body("{ me { id } }");
        AtomicReference<String> seen = new AtomicReference<>();
        filter.filter(post(json), (ServerWebExchange ex) -> ex.getRequest().getBody()
                .map(b -> b.toString(java.nio.charset.StandardCharsets.UTF_8)).collectList()
                .doOnNext(parts -> seen.set(String.join("", parts))).then()).block();
        assertThat(seen.get()).isEqualTo(json);
    }

    @Test
    @DisplayName("the 121st request in a window is refused with 429 and Retry-After, and never reaches the chain")
    @SuppressWarnings("unchecked")
    void rateLimited() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.just(121L));
        MockServerWebExchange exchange = post(body("{ publicPlatformRules { currency } }"));
        AtomicReference<Boolean> reached = new AtomicReference<>(false);

        filter.filter(exchange, ex -> { reached.set(true); return Mono.empty(); }).block();

        assertThat(reached.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    @DisplayName("the 120th request is still served")
    @SuppressWarnings("unchecked")
    void limitIsInclusive() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.just(120L));
        assertThat(run(post(body("{ publicPlatformRules { currency } }")))).isTrue();
    }

    @Test
    @DisplayName("public data is still served when Redis is down")
    @SuppressWarnings("unchecked")
    void failsOpen() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.error(new IllegalStateException("redis down")));
        assertThat(run(post(body("{ publicPlatformRules { currency } }")))).isTrue();
    }

    @Test
    @DisplayName("the limit is keyed on the address the gateway appended, not the one the caller wrote first")
    void keyedOnLastHop() {
        var trusted = com.pml.shared.security.publicop.TrustedProxies.parse("10.0.0.0/8");
        assertThat(PublicOperationFilter.clientAddress(
                MockServerHttpRequest.post("/graphql").remoteAddress(new java.net.InetSocketAddress("10.0.0.7", 1))
                        .header("X-Forwarded-For", "6.6.6.6, 10.0.0.9").build(), trusted))
                .isEqualTo("6.6.6.6");
    }

    @Test
    @DisplayName("the matcher follows the mark")
    void matcher() {
        MockServerWebExchange plain = post(body("{ me { id } }"));
        assertThat(PublicOperationFilter.isPublicOperation().matches(plain).block().isMatch()).isFalse();
        plain.getAttributes().put(PublicOperationFilter.PUBLIC_MARK, Boolean.TRUE);
        assertThat(PublicOperationFilter.isPublicOperation().matches(plain).block().isMatch()).isTrue();
    }
}
