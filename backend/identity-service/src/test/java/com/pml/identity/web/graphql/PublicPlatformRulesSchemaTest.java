package com.pml.identity.web.graphql;

import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.identity.security.PublicOperationFilter;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L4")
@Tag("ET-ADM-002")
@DisplayName("ET-ADM-002-R10 · the public rules type holds no organizer-only field and is the only open operation")
class PublicPlatformRulesSchemaTest {

    private static final Path SDL = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Path SOURCES = Path.of("src/main/java");

    @Test
    @DisplayName("PublicPlatformRules declares exactly the buyer-facing fields")
    void publicTypeShape() throws IOException {
        TypeDefinitionRegistry registry = new SchemaParser().parse(Files.readString(SDL));
        ObjectTypeDefinition type = (ObjectTypeDefinition) registry.getType("PublicPlatformRules").orElseThrow();
        assertThat(type.getFieldDefinitions().stream().map(FieldDefinition::getName)).containsExactlyInAnyOrder(
                "version", "updatedAt", "currency", "reservationHoldMinutes", "reservationGraceMinutes",
                "maxTicketsPerBooking", "refundCutoffHours", "rescheduleLimit", "refundPolicies");
        assertThat(PublicOperationFilter.PUBLIC_FIELDS).containsExactly("publicPlatformRules");
    }

    @Test
    @DisplayName("every identity query and mutation requires authorization except the allowlisted public query")
    void onlyTheAllowlistedOperationIsOpen() throws Exception {
        List<String> open = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES.resolve("com/pml/identity/web/graphql"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = SOURCES.relativize(file).toString().replace('/', '.').replaceAll("\\.java$", "");
                Class<?> type = Class.forName(name);
                for (Method method : type.getDeclaredMethods()) {
                    boolean operation = method.isAnnotationPresent(DgsQuery.class)
                            || method.isAnnotationPresent(DgsMutation.class);
                    boolean guarded = method.isAnnotationPresent(PreAuthorize.class)
                            || type.isAnnotationPresent(PreAuthorize.class);
                    if (operation && !guarded) {
                        open.add(method.getName());
                    }
                }
            }
        }
        // The invitation preview is guarded by an unguessable token in the argument, not by a role;
        // it stays unreachable without a JWT because /graphql is authenticated and only
        // PUBLIC_FIELDS are admitted tokenless. A new open operation must be added here deliberately.
        Set<String> expected = new java.util.HashSet<>(PublicOperationFilter.PUBLIC_FIELDS);
        expected.add("invitationByToken");
        assertThat(open).containsExactlyInAnyOrderElementsOf(expected);
    }
}
