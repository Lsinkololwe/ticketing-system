package com.pml.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The permission grammar is closed: one colon, a known verb, nothing that reads as a wildcard. */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Permission codes follow one grammar and a closed verb vocabulary")
class PermissionGrammarTest {

    private static final Pattern GRAMMAR = Pattern.compile("^[a-z][a-z0-9_]+:[a-z][a-z0-9_]+$");

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("Every code matches the grammar, splits into exactly two parts and has a verb from the vocabulary")
    void everyCodeIsWellFormed(Permission permission) {
        assertThat(GRAMMAR.matcher(permission.code()).matches()).isTrue();
        assertThat(permission.code().split(":")).hasSize(2);
        assertThat(permission.code()).doesNotContain(".").doesNotContain("*");
        assertThat(permission.module()).isEqualTo(permission.code().split(":")[0]);
        assertThat(permission.action().verb()).isEqualTo(permission.code().split(":")[1]);
    }

    @Test
    @DisplayName("Every verb in the vocabulary is used by at least one permission")
    void noVerbIsUnused() {
        assertThat(Arrays.stream(Permission.values()).map(Permission::action).distinct().toList())
                .containsExactlyInAnyOrder(Permission.Action.values());
    }

    @ParameterizedTest
    @ValueSource(strings = {"event:*", "*:view", "*", "event.view", "event:view:extra", "Event:view",
            "event:view ", "event:", ":view", "event:archive", "", " "})
    @DisplayName("Wildcards, dotted keys, extra parts, other casings and unknown verbs are not in the catalogue")
    void malformedCodesAreNotCatalogued(String code) {
        assertThat(Permission.fromCode(code)).isEmpty();
    }

    @Test
    @DisplayName("A null code names nothing")
    void nullNamesNothing() {
        assertThat(Permission.fromCode(null)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    @DisplayName("A code round-trips to the same constant")
    void roundTrips(Permission permission) {
        assertThat(Permission.fromCode(permission.code())).contains(permission);
    }
}
