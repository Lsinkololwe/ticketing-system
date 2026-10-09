package com.pml.catalog.workflow.lifecycle;

import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-CAT-001")
@DisplayName("ET-CAT-001-R4/R5/R6/R7 · lifecycle rules: completion time, reschedule cap, legal commands")
class LifecycleRulesTest {

    private static final Instant START = Instant.parse("2026-12-01T18:00:00Z");
    private static final long NOW = START.minus(Duration.ofDays(10)).toEpochMilli();

    @Test
    @DisplayName("R6 · an event ends at its declared end, or a day after it starts when it declares none")
    void anEventEndsAtItsEnd() {
        assertThat(LifecycleRules.endsAt(START, START.plus(Duration.ofHours(4)))).isEqualTo(START.plus(Duration.ofHours(4)));
        assertThat(LifecycleRules.endsAt(START, null)).isEqualTo(START.plus(Duration.ofDays(1)));
        assertThatThrownBy(() -> LifecycleRules.endsAt(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R6 · the completion timer counts down to the end and is never negative")
    void theTimerIsNeverNegative() {
        assertThat(LifecycleRules.untilCompletion(1_000L, 400L)).isEqualTo(Duration.ofMillis(600));
        assertThat(LifecycleRules.untilCompletion(1_000L, 5_000L)).isEqualTo(Duration.ZERO);
        assertThat(LifecycleRules.completionDue(1_000L, 999L)).isFalse();
        assertThat(LifecycleRules.completionDue(1_000L, 1_000L)).isTrue();
    }

    @Test
    @DisplayName("§4 · an event is rescheduled at most three times")
    void threeReschedules() {
        assertThat(LifecycleRules.canReschedule(0)).isTrue();
        assertThat(LifecycleRules.canReschedule(2)).isTrue();
        assertThat(LifecycleRules.canReschedule(3)).isFalse();
    }

    @Test
    @DisplayName("R5 · a rescheduled event keeps its duration")
    void aRescheduleKeepsTheDuration() {
        Instant moved = START.plus(Duration.ofDays(7));
        assertThat(LifecycleRules.shiftedEnd(START, START.plus(Duration.ofHours(4)), moved))
                .isEqualTo(moved.plus(Duration.ofHours(4)));
        assertThat(LifecycleRules.shiftedEnd(START, null, moved)).isNull();
        assertThat(LifecycleRules.shiftedEnd(START, START.minus(Duration.ofHours(1)), moved)).isEqualTo(moved);
    }

    @Test
    @DisplayName("R5 · a reschedule needs a reason, a future start, a published event and an unspent cap")
    void rescheduleRefusals() {
        long future = START.plus(Duration.ofDays(7)).toEpochMilli();
        assertThat(code(LifecycleRules.rescheduleRefusal(EventStatus.PUBLISHED, 0, future, " ", NOW)))
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(code(LifecycleRules.rescheduleRefusal(EventStatus.PUBLISHED, 0, NOW, "Headliner delayed", NOW)))
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(code(LifecycleRules.rescheduleRefusal(EventStatus.APPROVED, 0, future, "Headliner delayed", NOW)))
                .isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(code(LifecycleRules.rescheduleRefusal(EventStatus.PUBLISHED, 3, future, "Headliner delayed", NOW)))
                .isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(LifecycleRules.rescheduleRefusal(EventStatus.PUBLISHED, 2, future, "Headliner delayed", NOW)).isEmpty();
        assertThat(LifecycleRules.rescheduleRefusal(null, 9, future, "Headliner delayed", NOW))
                .as("before the event is loaded, the write decides the state question")
                .isEmpty();
    }

    @Test
    @DisplayName("R7 · cancellation needs a reason and is legal from APPROVED and PUBLISHED only")
    void cancellation() {
        assertThat(LifecycleRules.CANCELLABLE).containsExactlyInAnyOrder(EventStatus.APPROVED, EventStatus.PUBLISHED);
        for (EventStatus status : EventStatus.values()) {
            boolean legal = EnumSet.of(EventStatus.APPROVED, EventStatus.PUBLISHED).contains(status);
            assertThat(LifecycleRules.cancelRefusal(status, "Venue flooded").isEmpty()).as(status.name()).isEqualTo(legal);
        }
        assertThat(code(LifecycleRules.cancelRefusal(EventStatus.PUBLISHED, ""))).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("R4 · only a published event is unpublished, and only an approved one is published")
    void publishAndUnpublish() {
        assertThat(code(LifecycleRules.unpublishRefusal(EventStatus.APPROVED))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(LifecycleRules.unpublishRefusal(EventStatus.PUBLISHED)).isEmpty();
        assertThat(LifecycleRules.unpublishRefusal(null)).isEmpty();
        assertThat(code(LifecycleRules.publishRefusal(EventStatus.PUBLISHED))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(code(LifecycleRules.publishRefusal(EventStatus.CANCELLED))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(LifecycleRules.publishRefusal(EventStatus.APPROVED)).isEmpty();
    }

    private static ErrorCode code(Optional<Refusal> refusal) {
        return refusal.map(Refusal::code).orElse(null);
    }
}
