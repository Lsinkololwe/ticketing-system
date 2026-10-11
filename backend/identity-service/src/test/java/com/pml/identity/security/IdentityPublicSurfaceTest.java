package com.pml.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.publicop.PublicOperationRules;
import com.pml.shared.security.publicop.PublicOperationRules.Verdict;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a signed-out caller can reach in identity, read off the schema and the policy rather than trusted to a
 * comment: the allowlisted roots, every type reachable from them, and the one federated entity the router may
 * resolve with leaf fields only.
 */
@Tag("L4")
@Tag("ET-PLT-007")
@DisplayName("the signed-out identity surface is exactly the allowlisted roots and entity leaves; everything else is gated or unreachable")
class IdentityPublicSurfaceTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Written out so that widening what a signed-out caller can read changes this test as well as the policy. */
    private static final Set<String> EXPECTED_PUBLIC_TYPES = Set.of("PublicPlatformRules", "RulesRefundPolicy", "RulesRefundTier");

    private static final PublicOperationPolicy POLICY = PublicOperationPolicy
            .of("identity", PublicOperationFilter.PUBLIC_FIELDS)
            .withEntities(PublicOperationFilter.PUBLIC_ENTITY_FIELDS);
    private static final PublicOperationRules RULES = new PublicOperationRules(POLICY, JSON);

    private static TypeDefinitionRegistry registry() throws IOException {
        return new SchemaParser().parse(Files.readString(SDL));
    }

    private static String named(Type<?> type) {
        if (type instanceof NonNullType nonNull) {
            return named(nonNull.getType());
        }
        if (type instanceof ListType list) {
            return named(list.getType());
        }
        return ((TypeName) type).getName();
    }

    private static boolean gated(FieldDefinition field) {
        return field.getDirectives().stream().anyMatch(d -> d.getName().equals("auth"));
    }

    /** A type's fields, extension blocks included. */
    private static List<FieldDefinition> fieldsOf(TypeDefinitionRegistry registry, String typeName) {
        List<FieldDefinition> fields = new ArrayList<>();
        TypeDefinition<?> type = registry.getType(typeName).orElseThrow(() -> new AssertionError("no type " + typeName));
        if (type instanceof ObjectTypeDefinition object) {
            fields.addAll(object.getFieldDefinitions());
            registry.objectTypeExtensions().getOrDefault(typeName, List.of()).forEach(e -> fields.addAll(e.getFieldDefinitions()));
        } else if (type instanceof InterfaceTypeDefinition iface) {
            fields.addAll(iface.getFieldDefinitions());
        }
        return fields;
    }

    private static Verdict judgeEntity(String field, String typeName) {
        String query = "query($representations:[_Any!]!){_entities(representations:$representations){...on "
                + typeName + "{" + field + "}}}";
        String body = "{\"query\":" + JSON.valueToTree(query) + ",\"variables\":{\"representations\":"
                + "[{\"__typename\":\"" + typeName + "\",\"id\":\"o1\"}]}}";
        return RULES.judge(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("every allowlisted root exists and is declared @auth(requires: PUBLIC); every other root is refused by the policy")
    void rootsAreExactlyTheAllowlist() throws Exception {
        TypeDefinitionRegistry registry = registry();
        List<FieldDefinition> roots = fieldsOf(registry, "Query");

        for (String allowed : PublicOperationFilter.PUBLIC_FIELDS) {
            FieldDefinition root = roots.stream().filter(f -> f.getName().equals(allowed)).findFirst()
                    .orElseThrow(() -> new AssertionError("allowlisted root " + allowed + " is not in the schema"));
            assertThat(gated(root)).as(allowed + " declares @auth explicitly").isTrue();
            assertThat(root.getDirectives("auth").get(0).getArgument("requires").getValue().toString())
                    .as(allowed + " is open to a caller with no token").contains("PUBLIC");
            assertThat(RULES.judge(("{\"query\":\"{ " + allowed + " { version } }\"}").getBytes(StandardCharsets.UTF_8)))
                    .isEqualTo(Verdict.ALLOWED);
        }

        List<String> admittedByMistake = new ArrayList<>();
        for (FieldDefinition root : roots) {
            if (PublicOperationFilter.PUBLIC_FIELDS.contains(root.getName())) {
                continue;
            }
            Verdict verdict = RULES.judge(("{\"query\":\"{ " + root.getName() + " }\"}").getBytes(StandardCharsets.UTF_8));
            if (verdict == Verdict.ALLOWED) {
                admittedByMistake.add(root.getName());
            }
        }
        assertThat(roots).as("the walk saw the whole Query type").hasSizeGreaterThan(PublicOperationFilter.PUBLIC_FIELDS.size());
        assertThat(admittedByMistake).as("roots the policy would let a signed-out caller run").isEmpty();
    }

    @Test
    @DisplayName("the types reachable from an allowlisted root are exactly the public set; any other reachable field carries @auth")
    void reachableSurfaceIsClosed() throws Exception {
        TypeDefinitionRegistry registry = registry();
        List<FieldDefinition> roots = fieldsOf(registry, "Query");

        Set<String> reachable = new TreeSet<>();
        Deque<String> work = new ArrayDeque<>();
        for (String allowed : PublicOperationFilter.PUBLIC_FIELDS) {
            work.add(named(roots.stream().filter(f -> f.getName().equals(allowed)).findFirst().orElseThrow().getType()));
        }
        List<String> unprotected = new ArrayList<>();
        while (!work.isEmpty()) {
            String typeName = work.poll();
            if (!reachable.add(typeName)) {
                continue;
            }
            TypeDefinition<?> type = registry.getType(typeName).orElse(null);
            if (type == null || type instanceof ScalarTypeDefinition || type instanceof EnumTypeDefinition) {
                continue;
            }
            if (type instanceof UnionTypeDefinition union) {
                union.getMemberTypes().forEach(member -> work.add(named(member)));
                continue;
            }
            for (FieldDefinition field : fieldsOf(registry, typeName)) {
                work.add(named(field.getType()));
                if (!EXPECTED_PUBLIC_TYPES.contains(typeName) && !gated(field)) {
                    unprotected.add(typeName + "." + field.getName());
                }
            }
        }

        assertThat(reachable).as("object types a signed-out caller can walk into")
                .containsAll(EXPECTED_PUBLIC_TYPES);
        assertThat(unprotected)
                .as("reachable from a public root, outside the public set, and carrying no @auth")
                .isEmpty();
        assertThat(reachable).as("no restricted entity hangs off a public root")
                .doesNotContain("User", "Organization", "PlatformRules");
    }

    @Test
    @DisplayName("the Organization entity is admitted only for its allowlisted leaf fields; every other field is refused")
    void entityLeavesAreExactlyTheAllowlist() throws Exception {
        TypeDefinitionRegistry registry = registry();
        assertThat(PublicOperationFilter.PUBLIC_ENTITY_FIELDS).containsOnlyKeys("Organization");
        Set<String> allowed = PublicOperationFilter.PUBLIC_ENTITY_FIELDS.get("Organization");
        List<FieldDefinition> fields = fieldsOf(registry, "Organization");

        for (String leaf : allowed) {
            FieldDefinition definition = fields.stream().filter(f -> f.getName().equals(leaf)).findFirst()
                    .orElseThrow(() -> new AssertionError("allowlisted entity field " + leaf + " is not on Organization"));
            TypeDefinition<?> target = registry.getType(named(definition.getType())).orElse(null);
            assertThat(target == null || target instanceof ScalarTypeDefinition || target instanceof EnumTypeDefinition)
                    .as(leaf + " is a leaf, so nothing nests below it").isTrue();
            assertThat(judgeEntity(leaf, "Organization")).as(leaf).isEqualTo(Verdict.ALLOWED);
        }

        List<String> admittedByMistake = new ArrayList<>();
        for (FieldDefinition field : fields) {
            if (!allowed.contains(field.getName()) && judgeEntity(field.getName(), "Organization") == Verdict.ALLOWED) {
                admittedByMistake.add(field.getName());
            }
        }
        assertThat(fields).as("the walk saw the whole Organization type").hasSizeGreaterThan(allowed.size());
        assertThat(admittedByMistake).as("Organization fields the policy would resolve for a signed-out caller").isEmpty();
        assertThat(judgeEntity("id", "User")).isEqualTo(Verdict.ENTITIES_NOT_ALLOWED);
    }
}
