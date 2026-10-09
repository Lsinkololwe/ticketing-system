package com.pml.catalog.web.graphql;

import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.StringValue;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape of the operations ET-CAT-004 adds, read from the schema: who may call each, and which
 * client audience each is tagged for. Authorization is a directive in the SDL, so the SDL is where
 * a mistake shows.
 */
@Tag("L4")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R1/R9 · the new operations carry the roles and audiences the spec names")
class CatalogSchemaContractTest {

    private static TypeDefinitionRegistry registry;

    @BeforeAll
    static void parse() throws IOException {
        String sdl = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"), StandardCharsets.UTF_8);
        // Same text the service parses, plus the auth directive's own definition it relies on.
        registry = new SchemaParser().parse(sdl);
    }

    private static List<FieldDefinition> rootFields(String root) {
        List<FieldDefinition> fields = new ArrayList<>();
        registry.getType(root, ObjectTypeDefinition.class).ifPresent(type -> fields.addAll(type.getFieldDefinitions()));
        registry.objectTypeExtensions().getOrDefault(root, List.of())
                .forEach((ObjectTypeExtensionDefinition extension) -> fields.addAll(extension.getFieldDefinitions()));
        return fields;
    }

    private static FieldDefinition field(String root, String name) {
        return rootFields(root).stream().filter(f -> f.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(root + "." + name + " is not in the schema"));
    }

    private static Optional<String> role(FieldDefinition field) {
        return field.getDirectives("auth").stream().findFirst()
                .map(directive -> ((EnumValue) directive.getArgument("requires").getValue()).getName());
    }

    private static List<String> tags(FieldDefinition field) {
        return field.getDirectives("tag").stream()
                .map((Directive d) -> ((StringValue) d.getArgument("name").getValue()).getValue()).toList();
    }

    @Test
    @DisplayName("cancelEvent is for organizers and for the admin audience, and ADMIN is within ORGANIZER's hierarchy")
    void cancelEventIsAnAdminOperationToo() {
        FieldDefinition cancel = field("Mutation", "cancelEvent");

        assertThat(role(cancel)).contains("ORGANIZER");
        assertThat(tags(cancel)).contains("organizer", "admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"flagMedia", "removeMedia", "restoreMedia", "overrideEventBanner",
            "uploadStockImage", "updateStockImage", "deleteStockImage"})
    @DisplayName("moderation and the stock library are administrators' and tagged admin")
    void adminMutations(String name) {
        FieldDefinition mutation = field("Mutation", name);

        assertThat(role(mutation)).contains("ADMIN");
        assertThat(tags(mutation)).contains("admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"uploadMedia", "updateMedia", "deleteMedia", "cancelScheduledPublish"})
    @DisplayName("an organizer's mutations are for organizers")
    void organizerMutations(String name) {
        assertThat(role(field("Mutation", name))).contains("ORGANIZER");
        assertThat(tags(field("Mutation", name))).contains("organizer");
    }

    @Test
    @DisplayName("the moderation queue is an administrators' query")
    void moderationQueue() {
        assertThat(role(field("Query", "mediaAssets"))).contains("ADMIN");
        assertThat(tags(field("Query", "mediaAssets"))).contains("admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"myMedia", "myEventsConnection", "stockImages"})
    @DisplayName("an organizer's lists are for organizers")
    void organizerQueries(String name) {
        assertThat(role(field("Query", name))).contains("ORGANIZER");
    }

    @Test
    @DisplayName("a buyer's operations need a signed-in customer; the trending list needs nothing")
    void buyerOperations() {
        assertThat(role(field("Mutation", "unlockTierWithAccessCode"))).contains("CUSTOMER");
        assertThat(role(field("Query", "recommendedEvents"))).contains("CUSTOMER");
        assertThat(role(field("Query", "trendingEvents"))).contains("PUBLIC");
        assertThat(role(field("Query", "discoverEvents"))).contains("PUBLIC");
    }

    @Test
    @DisplayName("every operation this spec adds is either public on purpose or carries a role")
    void nothingIsAccidentallyOpen() {
        List<String> publicOnPurpose = List.of("trendingEvents", "discoverEvents");
        List<String> added = List.of("trendingEvents", "recommendedEvents", "myEventsConnection", "myMedia", "stockImages",
                "mediaAssets", "unlockTierWithAccessCode", "cancelScheduledPublish", "uploadMedia", "updateMedia",
                "deleteMedia", "flagMedia", "removeMedia", "restoreMedia", "overrideEventBanner", "uploadStockImage",
                "updateStockImage", "deleteStockImage");

        List<String> open = new ArrayList<>();
        for (String name : added) {
            FieldDefinition f = rootFields("Query").stream().filter(x -> x.getName().equals(name)).findFirst()
                    .orElseGet(() -> field("Mutation", name));
            boolean undecided = role(f).isEmpty();
            boolean openWithoutSaying = role(f).filter("PUBLIC"::equals).isPresent() && !publicOnPurpose.contains(name);
            if (undecided || openWithoutSaying) {
                open.add(name);
            }
        }

        assertThat(open).isEmpty();
    }

    @Test
    @DisplayName("the access code and the sales totals are tagged for organizers, and the organization stub stays unresolvable here")
    void sensitiveFields() {
        ObjectTypeDefinition tier = registry.getType("TicketTier", ObjectTypeDefinition.class).orElseThrow();
        ObjectTypeDefinition event = registry.getType("Event", ObjectTypeDefinition.class).orElseThrow();

        assertThat(tags(tier.getFieldDefinitions().stream().filter(f -> f.getName().equals("accessCode")).findFirst().orElseThrow()))
                .contains("organizer");
        for (String money : List.of("grossSales", "commissionAmount", "netSales")) {
            assertThat(tags(event.getFieldDefinitions().stream().filter(f -> f.getName().equals(money)).findFirst().orElseThrow()))
                    .as(money).contains("organizer");
        }
        ObjectTypeDefinition organization = registry.getType("Organization", ObjectTypeDefinition.class).orElseThrow();
        assertThat(organization.getDirectives("key").get(0).getArgument("resolvable").getValue().toString())
                .contains("false");
    }
}
