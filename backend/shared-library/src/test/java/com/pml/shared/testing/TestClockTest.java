package com.pml.shared.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The clock that makes every boundary in the corpus assertable on both sides.
 *
 * <p>ET-PLT-006 R3. The corpus specifies its time boundaries in pairs — live at 9:59 and
 * expired at 10:01 (ET-TKT-001 R4), 4:59/5:01 and 59s/61s and 14:59/15:01 (ET-IDN-001 R2),
 * refuse at {@code salesStartAt − 1s} and succeed at {@code salesStartAt} (ET-CAT-002 R3) —
 * because a one-sided test passes on an implementation with the wrong window entirely.
 */
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006-R3 · a frozen clock, movable only by the test")
class TestClockTest {

    private static final Instant T0 = Instant.parse("2026-03-01T18:00:00Z");

    @Test
    @DisplayName("does not move on its own")
    void isFrozen() throws InterruptedException {
        TestClock clock = TestClock.frozenAt(T0);
        Instant first = clock.instant();
        Thread.sleep(20);
        assertThat(clock.instant()).isEqualTo(first).isEqualTo(T0);
    }

    @Test
    @DisplayName("moves exactly as far as the test asks")
    void advancesDeterministically() {
        TestClock clock = TestClock.frozenAt(T0);
        clock.advance(Duration.ofMinutes(10));
        assertThat(clock.instant()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("expresses both sides of a ten-minute hold, which is the shape every expiry test needs")
    void bothSidesOfABoundary() {
        Instant expiresAt = T0.plus(Duration.ofMinutes(10));
        TestClock clock = TestClock.frozenAt(T0);

        clock.justBefore(expiresAt);
        assertThat(clock.instant()).isBefore(expiresAt);

        clock.justAfter(expiresAt);
        assertThat(clock.instant()).isAfter(expiresAt);
    }

    @Test
    @DisplayName("refuses to rewind, because a service may already have read it")
    void refusesToGoBackwards() {
        TestClock clock = TestClock.frozenAt(T0);
        assertThatThrownBy(() -> clock.advance(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only moves forward");
    }
}
