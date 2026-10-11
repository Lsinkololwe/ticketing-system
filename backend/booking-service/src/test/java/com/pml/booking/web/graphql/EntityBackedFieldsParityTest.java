package com.pml.booking.web.graphql;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.beans.Introspector;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A GraphQL type served straight from an entity answers each field from a property of that entity
 * or from a field resolver. A non-null field with neither returns null, and graphql-java then
 * fails the whole object ("declared non-null but returned null"): the escrow list that read
 * {@code totalDeposits} after the ledger renamed it to {@code totalCredited} is the case this guards.
 * {@link GraphQlDtoSchemaParityTest} does the same for the DTO types; entities were not covered.
 * Flat methods: F-055.
 */
@Tag("L4")
@Tag("ET-PLT-007")
@DisplayName("Every non-null field of an entity-backed type has a property or a resolver")
class EntityBackedFieldsParityTest {

    private static final Path SCHEMA = Path.of("src/main/resources/graphql/schema.graphqls");
    private static final Pattern FIELD = Pattern.compile("^\\s+(\\w+)\\s*(?:\\([^)]*\\))?\\s*:\\s*([^\\s#@]+)", Pattern.MULTILINE);

    /** GraphQL type name -> the entity it is served from. */
    private static final Map<String, Class<?>> BACKED = Map.of(
            "EventEscrowAccount", com.pml.booking.domain.model.EventEscrowAccount.class,
            "PayoutRequest", com.pml.booking.domain.model.PayoutRequest.class,
            "BankAccount", com.pml.booking.domain.model.BankAccount.class,
            "TicketReservation", com.pml.booking.domain.model.TicketReservation.class,
            "Ticket", com.pml.booking.domain.model.Ticket.class);

    @Test
    @DisplayName("no non-null field is left without a source")
    void everyNonNullFieldHasASource() throws IOException {
        String schema = Files.readString(SCHEMA);
        Set<String> resolved = resolvedFields();
        List<String> orphaned = new ArrayList<>();

        BACKED.forEach((type, entity) -> {
            Set<String> properties = propertiesOf(entity);
            for (String field : nonNullFieldsOf(schema, type)) {
                if (!properties.contains(field) && !resolved.contains(type + "." + field)) {
                    orphaned.add(type + "." + field);
                }
            }
        });

        assertThat(orphaned).as("non-null fields served from an entity that has no such property and no resolver").isEmpty();
    }

    private static List<String> nonNullFieldsOf(String schema, String type) {
        int start = schema.indexOf("type " + type + " ");
        if (start < 0) {
            start = schema.indexOf("type " + type + "{");
        }
        assertThat(start).as("type %s is declared", type).isGreaterThanOrEqualTo(0);
        int end = schema.indexOf("\n}", start);
        List<String> fields = new ArrayList<>();
        Matcher match = FIELD.matcher(schema.substring(start, end));
        while (match.find()) {
            if (match.group(2).endsWith("!")) {
                fields.add(match.group(1));
            }
        }
        return fields;
    }

    private static Set<String> propertiesOf(Class<?> entity) {
        Set<String> names = new HashSet<>();
        for (Method method : entity.getMethods()) {
            if (method.getParameterCount() == 0) {
                // graphql-java also reads a field through an accessor named exactly like it (isDefault()).
                names.add(method.getName());
            }
            if (method.getParameterCount() == 0 && (method.getName().startsWith("get") || method.getName().startsWith("is"))) {
                String base = method.getName().startsWith("get") ? method.getName().substring(3) : method.getName().substring(2);
                if (!base.isEmpty()) {
                    names.add(Introspector.decapitalize(base));
                }
            }
        }
        return names;
    }

    /** {@code Type.field} for every {@code @DgsData} resolver in the service. */
    private static Set<String> resolvedFields() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(DgsComponent.class));
        Set<String> resolved = new HashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.pml.booking")) {
            try {
                for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                    for (DgsData data : method.getAnnotationsByType(DgsData.class)) {
                        resolved.add(data.parentType() + "." + (data.field().isEmpty() ? method.getName() : data.field()));
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return resolved;
    }
}
