package com.pml.catalog.security;

import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.shared.security.publicop.PublicOperationPolicy;
import com.pml.shared.security.publicop.PublicOperationRules;
import com.pml.shared.security.publicop.PublicOperationRules.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-CAT-004-R13 · what a signed-out buyer can reach, read off the schema and the resolvers rather than
 * trusted to a comment: the allowed root fields, and every field reachable from them.
 */
@Tag("L4")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R13 · the anonymous surface is exactly the allowlisted roots, and no restricted field hangs off them")
class CatalogPublicSurfaceTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Set<String> RESTRICTED_TAGS = Set.of("organizer", "admin", "internal");

    /** The allowlist, written out: changing it is a spec change (ET-CAT-004-R13). */
    private static final Set<String> EXPECTED_ROOTS = Set.of(
            "discoverEvents", "trendingEvents", "event", "categories", "provinces", "cities", "citiesWithEvents",
            "referenceData", "referenceDataByParent");

    /** The one gate an allowlisted root may carry: a per-type check that admits any signed-in caller. */
    private static final String REFERENCE_GATE = "isAuthenticated() or @referenceAccess.isPublic(#type)";

    /**
     * Tier inventory the buyer's event page reads, tagged for organizers in the contract but public on a
     * published event's open tiers (hidden tiers never reach an anonymous caller, see TicketTier resolution).
     */
    private static final Set<String> DOCUMENTED_PUBLIC_DESPITE_TAG = Set.of(
            "TicketTier.quantity", "TicketTier.soldQuantity", "TicketTier.sortOrder", "TicketTier.isActive", "TicketTier.isHidden");

    private static TypeDefinitionRegistry registry() throws IOException {
        return new SchemaParser().parse(Files.readString(SDL));
    }

    private static boolean hasDirective(FieldDefinition field, String name) {
        return field.getDirectives().stream().anyMatch(d -> d.getName().equals(name));
    }

    private static Set<String> tagsOf(FieldDefinition field) {
        Set<String> tags = new LinkedHashSet<>();
        for (Directive d : field.getDirectives("tag")) {
            tags.add(((graphql.language.StringValue) d.getArgument("name").getValue()).getValue());
        }
        return tags;
    }

    private static String named(Type<?> type) {
        if (type instanceof NonNullType n) {
            return named(n.getType());
        }
        if (type instanceof ListType l) {
            return named(l.getType());
        }
        return ((TypeName) type).getName();
    }

    /** Query root fields, extension blocks included. */
    private static List<FieldDefinition> queryFields(TypeDefinitionRegistry registry) {
        List<FieldDefinition> fields = new ArrayList<>(((ObjectTypeDefinition) registry.getType("Query").orElseThrow()).getFieldDefinitions());
        registry.objectTypeExtensions().getOrDefault("Query", List.of()).forEach(e -> fields.addAll(e.getFieldDefinitions()));
        return fields;
    }

    @Test
    @DisplayName("the allowlist is exactly the nine buyer reads, and the policy enforces that list")
    void allowlistIsExact() {
        assertThat(PublicDiscoveryConfig.PUBLIC_ROOT_FIELDS).containsExactlyInAnyOrderElementsOf(EXPECTED_ROOTS);
        assertThat(PublicDiscoveryConfig.PUBLIC_ENTITY_FIELDS)
                .containsOnlyKeys("Organization")
                .containsEntry("Organization", Set.of("publishedEventCount", "completedEventCount"));
    }

    @Test
    @DisplayName("every allowed root exists, is declared @auth(requires: PUBLIC), and has no role gate on its resolver (only the per-type reference gate)")
    void rootsArePublicInSchemaAndCode() throws Exception {
        TypeDefinitionRegistry registry = registry();
        List<FieldDefinition> roots = queryFields(registry);
        for (String name : EXPECTED_ROOTS) {
            FieldDefinition field = roots.stream().filter(f -> f.getName().equals(name)).findFirst()
                    .orElseThrow(() -> new AssertionError("allowlisted root " + name + " is not in the schema"));
            assertThat(field.getDirectives("auth").stream()
                    .map(d -> ((graphql.language.EnumValue) d.getArgument("requires").getValue()).getName()))
                    .as(name + " @auth: an open root says so explicitly, except the reference-data roots "
                            + "whose per-type gate is on the resolver")
                    .containsExactlyElementsOf(name.startsWith("referenceData") ? List.of() : List.of("PUBLIC"));
            assertThat(tagsOf(field)).as(name + " tags").isEmpty();
        }
        List<String> guarded = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES.resolve("com/pml/catalog/web/graphql/query"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Class<?> type = Class.forName(SOURCES.relativize(file).toString().replace('/', '.').replaceAll("\\.java$", ""));
                for (Method method : type.getDeclaredMethods()) {
                    String field = method.isAnnotationPresent(DgsQuery.class)
                            ? (method.getAnnotation(DgsQuery.class).field().isEmpty() ? method.getName() : method.getAnnotation(DgsQuery.class).field())
                            : null;
                    boolean roleGated = method.isAnnotationPresent(PreAuthorize.class)
                            && !REFERENCE_GATE.equals(method.getAnnotation(PreAuthorize.class).value());
                    if (field != null && EXPECTED_ROOTS.contains(field)
                            && (roleGated || type.isAnnotationPresent(PreAuthorize.class))) {
                        guarded.add(field);
                    }
                }
            }
        }
        assertThat(guarded).as("an allowlisted root with a role gate would answer 403 to the buyer it was opened for").isEmpty();
    }

    @Test
    @DisplayName("every organizer, admin or internal field reachable from an allowed root carries @auth, bar the documented tier counts")
    void reachableRestrictedFieldsAreGated() throws Exception {
        TypeDefinitionRegistry registry = registry();
        Map<String, FieldDefinition> roots = new java.util.HashMap<>();
        queryFields(registry).forEach(f -> roots.put(f.getName(), f));

        Set<String> seen = new TreeSet<>();
        Deque<String> work = new ArrayDeque<>();
        EXPECTED_ROOTS.forEach(r -> work.add(named(roots.get(r).getType())));
        List<String> ungated = new ArrayList<>();
        List<String> gated = new ArrayList<>();
        while (!work.isEmpty()) {
            String typeName = work.poll();
            if (!seen.add(typeName)) {
                continue;
            }
            var type = registry.getType(typeName);
            if (type.isEmpty() || !(type.get() instanceof ObjectTypeDefinition || type.get() instanceof InterfaceTypeDefinition)) {
                continue; // scalar, enum
            }
            List<FieldDefinition> fields = type.get() instanceof ObjectTypeDefinition o ? o.getFieldDefinitions()
                    : ((InterfaceTypeDefinition) type.get()).getFieldDefinitions();
            List<FieldDefinition> all = new ArrayList<>(fields);
            registry.objectTypeExtensions().getOrDefault(typeName, List.of()).forEach(e -> all.addAll(e.getFieldDefinitions()));
            for (FieldDefinition field : all) {
                work.add(named(field.getType()));
                String coordinate = typeName + "." + field.getName();
                boolean tagged = !Set.copyOf(tagsOf(field)).stream().filter(RESTRICTED_TAGS::contains).toList().isEmpty();
                boolean typeTagged = ((graphql.language.DirectivesContainer<?>) type.get()).getDirectives("tag").stream().anyMatch(d ->
                        RESTRICTED_TAGS.contains(((graphql.language.StringValue) d.getArgument("name").getValue()).getValue()));
                if ((tagged || typeTagged) && !hasDirective(field, "auth") && !DOCUMENTED_PUBLIC_DESPITE_TAG.contains(coordinate)) {
                    ungated.add(coordinate);
                }
                if (hasDirective(field, "auth")) {
                    gated.add(coordinate);
                }
            }
        }
        assertThat(seen).as("the walk must reach the event page's types").contains("Event", "TicketTier", "EventConnection", "EventCategory", "City", "Province");
        assertThat(ungated).as("fields tagged organizer/admin/internal that an anonymous caller could select").isEmpty();
        assertThat(gated).as("what the walk found gated").contains(
                "Event.rejectionReason", "Event.approvedBy", "Event.grossSales", "Event.commissionAmount", "Event.netSales",
                "Event.createdBy", "Event.version", "Event.isActive", "Event.organizer", "Event.organizerId",
                "Event.virtualEventUrl", "TicketTier.accessCode", "TicketTier.organizationId");
    }

    @Test
    @DisplayName("the documented exceptions are real tier fields, so the list cannot rot")
    void exceptionsExist() throws Exception {
        ObjectTypeDefinition tier = (ObjectTypeDefinition) registry().getType("TicketTier").orElseThrow();
        for (String coordinate : DOCUMENTED_PUBLIC_DESPITE_TAG) {
            String field = coordinate.substring("TicketTier.".length());
            FieldDefinition definition = tier.getFieldDefinitions().stream().filter(f -> f.getName().equals(field)).findFirst().orElseThrow();
            assertThat(tagsOf(definition)).as(coordinate).isNotEmpty();
        }
    }
}
