package com.pml.catalog.web.graphql;

import com.pml.shared.testing.GraphQlSchemaParity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DGS matches schema fields to Java properties by name, and a mismatch fails silently: an
 * output field reads null, an input field is dropped. This holds every GraphQL DTO to the
 * schema type of the same name.
 *
 * <p>The mismatches already known are listed below. A new one fails the build; so does a listed
 * one that has been fixed, so the list only shrinks.
 */
@Tag("L1")
@Tag("ET-PLT-004")
@DisplayName("Every GraphQL DTO has exactly the fields of its schema type")
class GraphQlDtoSchemaParityTest {

    /** Empty: every known mismatch has been fixed. A new one is added here only with a reason. */
    private static final Set<String> KNOWN = Set.of();

    private final List<String> drift = GraphQlSchemaParity.drift(
            "classpath*:graphql/**/*.graphqls",
            "com.pml.catalog.web.graphql.dto",
            "com.pml.catalog.web.graphql");

    @Test
    @DisplayName("no field is added to either side without the other")
    void noNewDrift() {
        assertThat(drift).filteredOn(line -> !KNOWN.contains(GraphQlSchemaParity.key(line))).isEmpty();
    }

    @Test
    @DisplayName("a fixed mismatch is taken off the known list")
    void knownListHasNoFixedEntries() {
        Set<String> current = new TreeSet<>();
        drift.forEach(line -> current.add(GraphQlSchemaParity.key(line)));
        assertThat(new TreeSet<>(KNOWN)).isSubsetOf(current);
    }
}
