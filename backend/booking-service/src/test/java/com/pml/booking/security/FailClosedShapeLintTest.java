package com.pml.booking.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pml.shared.testing.FailClosedOnRevocationShape;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A mutation marked {@code @FailClosedOnRevocation} must return {@code Mono}: the aspect that
 * enforces the annotation throws {@code IllegalStateException} for anything else, on every call,
 * whatever the token or the store says.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("Every @FailClosedOnRevocation mutation returns Mono, so the aspect can actually guard it")
class FailClosedShapeLintTest {

    @Test
    @DisplayName("no mutation is annotated with a return type the revocation aspect cannot wrap")
    void everyGuardedMutationReturnsMono() {
        List<String> offenders = FailClosedOnRevocationShape.violations(
                Path.of("src/main/java/com/pml/booking/web/graphql"),
                Path.of("src/main/java"));

        assertThat(offenders).isEmpty();
    }
}
