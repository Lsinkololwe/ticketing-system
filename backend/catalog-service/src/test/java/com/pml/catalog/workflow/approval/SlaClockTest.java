package com.pml.catalog.workflow.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ADM-001")
@DisplayName("ET-ADM-001-R3/R7 · the SLA clock counts platform time and pauses while changes are requested")
class SlaClockTest {

    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final Duration SLA = Duration.ofHours(24);

    @Test
    @DisplayName("a clock counts from submission")
    void countsFromSubmission() {
        SlaClock clock = SlaClock.startedAt(0L);
        assertThat(clock.running()).isTrue();
        assertThat(clock.elapsed(5 * HOUR)).isEqualTo(5 * HOUR);
        assertThat(clock.remaining(SLA, 5 * HOUR)).isEqualTo(19 * HOUR);
    }

    @Test
    @DisplayName("a paused clock stands still however long the applicant takes")
    void pauseStops() {
        SlaClock clock = SlaClock.startedAt(0L);
        clock.pause(5 * HOUR);
        assertThat(clock.running()).isFalse();
        assertThat(clock.elapsed(500 * HOUR)).isEqualTo(5 * HOUR);
        assertThat(clock.remaining(SLA, 500 * HOUR)).isEqualTo(19 * HOUR);
    }

    @Test
    @DisplayName("resumption continues from what was spent, across any number of rounds")
    void resumptionContinues() {
        SlaClock clock = SlaClock.startedAt(0L);
        clock.pause(5 * HOUR);
        clock.resume(50 * HOUR);
        assertThat(clock.elapsed(52 * HOUR)).isEqualTo(7 * HOUR);
        clock.pause(53 * HOUR);
        clock.resume(90 * HOUR);
        assertThat(clock.elapsed(91 * HOUR)).isEqualTo(9 * HOUR);
    }

    @Test
    @DisplayName("pausing or resuming twice changes nothing")
    void repeatedCallsAreHarmless() {
        SlaClock clock = SlaClock.startedAt(0L);
        clock.pause(5 * HOUR);
        clock.pause(9 * HOUR);
        assertThat(clock.elapsed(9 * HOUR)).isEqualTo(5 * HOUR);
        clock.resume(10 * HOUR);
        clock.resume(12 * HOUR);
        assertThat(clock.elapsed(12 * HOUR)).isEqualTo(7 * HOUR);
    }

    @Test
    @DisplayName("the remaining budget is zero once breached, never negative")
    void remainingIsNeverNegative() {
        SlaClock clock = SlaClock.startedAt(0L);
        assertThat(clock.remaining(SLA, 30 * HOUR)).isZero();
        assertThat(SlaClock.of(-5L, true, 0L).elapsed(0L)).isZero();
    }
}
