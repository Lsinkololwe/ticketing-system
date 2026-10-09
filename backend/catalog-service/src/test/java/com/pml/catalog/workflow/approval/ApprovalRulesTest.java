package com.pml.catalog.workflow.approval;

import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-ADM-001")
@DisplayName("ET-ADM-001-R2/R3/R5 · review rules: escalation schedule, claim contention, decision preconditions")
class ApprovalRulesTest {

    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final String ALICE = "reviewer-alice";
    private static final String BOB = "reviewer-bob";
    private static final String REASON = "The venue capacity does not match the tiers";

    @Test
    @DisplayName("R3 · levels fire at 1×, 2× and 4× the 24-hour SLA, and there are exactly three")
    void thresholds() {
        assertThat(ApprovalRules.threshold(1)).isEqualTo(Duration.ofHours(24));
        assertThat(ApprovalRules.threshold(2)).isEqualTo(Duration.ofHours(48));
        assertThat(ApprovalRules.threshold(3)).isEqualTo(Duration.ofHours(96));
        assertThatThrownBy(() -> ApprovalRules.threshold(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalRules.threshold(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("R3 · the next level is due only past its threshold, one level at a time, never past three")
    void levelsFireOnceInOrder() {
        assertThat(ApprovalRules.levelDue(0L, 0)).isZero();
        assertThat(ApprovalRules.levelDue(24 * HOUR - 1, 0)).isZero();
        assertThat(ApprovalRules.levelDue(24 * HOUR, 0)).isEqualTo(1);
        assertThat(ApprovalRules.levelDue(100 * HOUR, 0)).as("a long silence still fires level 1 first").isEqualTo(1);
        assertThat(ApprovalRules.levelDue(47 * HOUR, 1)).isEqualTo(1);
        assertThat(ApprovalRules.levelDue(100 * HOUR, 1)).isEqualTo(2);
        assertThat(ApprovalRules.levelDue(100 * HOUR, 2)).isEqualTo(3);
        assertThat(ApprovalRules.levelDue(1_000 * HOUR, 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("R2, R3 · the workflow wakes at the next threshold or the claim's expiry, whichever is sooner")
    void nextWake() {
        SlaClock counting = SlaClock.startedAt(0L);
        assertThat(ApprovalRules.nextWake(10 * HOUR, counting, 0, null)).isEqualTo(Duration.ofHours(14));
        assertThat(ApprovalRules.nextWake(10 * HOUR, counting, 0, 10 * HOUR + HOUR / 2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(ApprovalRules.nextWake(30 * HOUR, counting, 0, null)).as("an overdue level is due now").isEqualTo(Duration.ZERO);
        assertThat(ApprovalRules.nextWake(10 * HOUR, counting, 3, null)).as("nothing left to escalate").isNull();

        SlaClock paused = SlaClock.of(20 * HOUR, false, 0L);
        assertThat(ApprovalRules.nextWake(500 * HOUR, paused, 0, null)).as("a paused clock sets no timer").isNull();
        assertThat(ApprovalRules.nextWake(10 * HOUR, paused, 0, 11 * HOUR)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("R2 · a live claim held by somebody else refuses; the holder or an expired claim does not")
    void claimContention() {
        long now = 10 * HOUR;
        long live = now + HOUR / 4;
        assertThat(code(ApprovalRules.claimRefusal(EventStatus.PENDING_APPROVAL, ALICE, live, BOB, now)))
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
        assertThat(ApprovalRules.claimRefusal(EventStatus.PENDING_APPROVAL, ALICE, live, BOB, now).orElseThrow().message())
                .as("the refusal says who holds it").contains(ALICE);
        assertThat(ApprovalRules.claimRefusal(EventStatus.PENDING_APPROVAL, ALICE, live, ALICE, now)).isEmpty();
        assertThat(ApprovalRules.claimRefusal(EventStatus.PENDING_APPROVAL, ALICE, now, BOB, now)).isEmpty();
        assertThat(code(ApprovalRules.claimRefusal(EventStatus.CHANGES_REQUESTED, null, 0L, BOB, now)))
                .isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(code(ApprovalRules.claimRefusal(EventStatus.PENDING_APPROVAL, null, 0L, " ", now)))
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(code(ApprovalRules.releaseRefusal(null, 0L, now))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(code(ApprovalRules.releaseRefusal(ALICE, now, now))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(ApprovalRules.releaseRefusal(ALICE, live, now)).isEmpty();
    }

    @Test
    @DisplayName("R5 · a decision refuses another's claim, a short reason, or an item not awaiting review")
    void decisionPreconditions() {
        long now = 10 * HOUR;
        long live = now + HOUR / 4;
        assertThat(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, null, 0L, ALICE, null, false, now))
                .as("an unclaimed item is decidable, so a reviewer is never trapped").isEmpty();
        assertThat(code(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, ALICE, live, BOB, REASON, true, now)))
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, ALICE, live, ALICE, REASON, true, now)).isEmpty();
        assertThat(code(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, null, 0L, ALICE, "Too short", true, now)))
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, null, 0L, ALICE, "x".repeat(20), true, now)).isEmpty();
        assertThat(code(ApprovalRules.decisionRefusal(EventStatus.APPROVED, null, 0L, ALICE, REASON, true, now)))
                .isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(code(ApprovalRules.decisionRefusal(EventStatus.PENDING_APPROVAL, null, 0L, null, REASON, true, now)))
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("R5 · a reason of nineteen characters is short, twenty is enough, and padding does not count")
    void reasonLength() {
        assertThat(ApprovalRules.reasonSufficient("x".repeat(19))).isFalse();
        assertThat(ApprovalRules.reasonSufficient("x".repeat(20))).isTrue();
        assertThat(ApprovalRules.reasonSufficient("   " + "x".repeat(19) + "   ")).isFalse();
        assertThat(ApprovalRules.reasonSufficient(null)).isFalse();
    }

    @Test
    @DisplayName("submission and resubmission are refused outside their states")
    void submissionStates() {
        assertThat(ApprovalRules.submitRefusal(null)).isEmpty();
        assertThat(ApprovalRules.submitRefusal(EventStatus.CHANGES_REQUESTED)).isEmpty();
        assertThat(code(ApprovalRules.submitRefusal(EventStatus.APPROVED))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(ApprovalRules.resubmitRefusal(EventStatus.CHANGES_REQUESTED)).isEmpty();
        assertThat(code(ApprovalRules.resubmitRefusal(EventStatus.PENDING_APPROVAL))).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
        assertThat(ApprovalRules.isOpen(EventStatus.PENDING_APPROVAL)).isTrue();
        assertThat(ApprovalRules.isOpen(EventStatus.CHANGES_REQUESTED)).isTrue();
        assertThat(ApprovalRules.isOpen(EventStatus.REJECTED)).isFalse();
        assertThat(ApprovalRules.isOpen(null)).isFalse();
    }

    @Test
    @DisplayName("R3 · level 1 notifies the holder or the queue, level 2 a supervisor, level 3 leadership")
    void recipients() {
        assertThat(ApprovalRules.recipient(1, null)).isEqualTo(ApprovalRules.QUEUE);
        assertThat(ApprovalRules.recipient(1, ALICE)).isEqualTo(ALICE);
        assertThat(ApprovalRules.recipient(2, ALICE)).isEqualTo(ApprovalRules.SUPERVISOR);
        assertThat(ApprovalRules.recipient(3, ALICE)).isEqualTo(ApprovalRules.LEADERSHIP);
    }

    @Test
    @DisplayName("R3 · an adopted review's clock counts from submission, and stops where changes were requested")
    void adoptedClock() {
        SlaClock pending = ApprovalRules.adoptedClock(EventStatus.PENDING_APPROVAL, 100 * HOUR, 0L, 110 * HOUR);
        assertThat(pending.running()).isTrue();
        assertThat(pending.elapsed(110 * HOUR)).isEqualTo(10 * HOUR);

        SlaClock waiting = ApprovalRules.adoptedClock(EventStatus.CHANGES_REQUESTED, 100 * HOUR, 106 * HOUR, 150 * HOUR);
        assertThat(waiting.running()).isFalse();
        assertThat(waiting.elapsed(150 * HOUR)).isEqualTo(6 * HOUR);

        SlaClock unstamped = ApprovalRules.adoptedClock(EventStatus.PENDING_APPROVAL, 0L, 0L, 110 * HOUR);
        assertThat(unstamped.elapsed(110 * HOUR)).as("no submission stamp starts the clock at adoption").isZero();
    }

    private static ErrorCode code(Optional<Refusal> refusal) {
        return refusal.map(Refusal::code).orElse(null);
    }
}
