package com.pml.identity.security;

import com.pml.shared.testing.OperationGates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every identity query and mutation records an access decision.
 *
 * <p>A root field with neither {@code @auth} in the schema nor {@code @PreAuthorize} on its
 * resolver answers any caller holding a token. That is the shape of every missing-authorization
 * defect found so far: the check is written on the operation somebody was thinking about and
 * forgotten on the one added later. A field that is meant to be open says so with
 * {@code @auth(requires: PUBLIC)}, which this test accepts and a reviewer can see.</p>
 */
@Tag("L4")
@Tag("ET-PLT-007")
@DisplayName("every identity query and mutation records an access decision")
class OperationGateLintTest {

    @Test
    @DisplayName("no root field is left without @auth or @PreAuthorize")
    void everyRootFieldIsDecided() {
        List<String> undecided = OperationGates.undecided(
                Path.of("src/main/resources/graphql/schema.graphqls"),
                Path.of("src/main/java/com/pml/identity/web/graphql"),
                Path.of("src/main/java"));

        assertThat(undecided)
                .as("add @auth(requires: ...) to the schema field, or @auth(requires: PUBLIC) if it is "
                        + "meant to be open to every caller")
                .isEmpty();
    }
}
