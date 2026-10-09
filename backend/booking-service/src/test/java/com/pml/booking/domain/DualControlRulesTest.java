package com.pml.booking.domain;

import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The maker-checker rule, role matrix and expiry boundary, as plain tests. */
@Tag("L1")
@Tag("ET-ADM-003")
@DisplayName("ET-ADM-003 · dual control: who may propose, who may confirm, and until when")
class DualControlRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private static RecoveryProposal proposal(RecoveryAction action, RecoveryProposalStatus status, Instant expiresAt) {
        return RecoveryProposal.builder().action(action).status(status).proposedById("maker").expiresAt(expiresAt).build();
    }

    @Test
    @DisplayName("role matrix: SUPER_ADMIN holds everything; ADMIN and FINANCE hold FINANCE actions; only SUPER_ADMIN holds a force-complete; others hold nothing")
    void roles() {
        for (RecoveryAction action : RecoveryAction.values()) {
            assertThat(DualControlRules.holdsRole(List.of("ROLE_SUPER_ADMIN"), action)).as(action + " super").isTrue();
            assertThat(DualControlRules.holdsRole(List.of("ROLE_ORGANIZER", "ROLE_CUSTOMER"), action)).as(action + " organizer").isFalse();
            assertThat(DualControlRules.holdsRole(List.of(), action)).isFalse();
        }
        assertThat(DualControlRules.holdsRole(List.of("ROLE_FINANCE"), RecoveryAction.WRITE_OFF_CHARGEBACK)).isTrue();
        assertThat(DualControlRules.holdsRole(List.of("ROLE_ADMIN"), RecoveryAction.TRANSFER_PLATFORM_FUNDS)).isTrue();
        assertThat(DualControlRules.holdsRole(List.of("ROLE_FINANCE"), RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS)).isFalse();
        assertThat(DualControlRules.holdsRole(List.of("ROLE_ADMIN"), RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS)).isFalse();
        assertThat(DualControlRules.holdsRole(List.of("FINANCE"), RecoveryAction.WRITE_OFF_CHARGEBACK)).as("a bare role name is not a Spring authority").isFalse();
    }

    @Test
    @DisplayName("a reason is at least twenty real characters")
    void reasons() {
        assertThat(DualControlRules.checkReason("x".repeat(20), "a proposal")).isNull();
        assertThat(DualControlRules.checkReason("x".repeat(19), "a proposal").errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(DualControlRules.checkReason("   " + "x".repeat(10) + "   ", "a proposal")).as("padding does not count").isNotNull();
        assertThat(DualControlRules.checkReason(null, "a proposal")).isNotNull();
    }

    @Test
    @DisplayName("confirmation is refused in the order of disclosure: your identity, then your role, then the proposal's state")
    void confirmationOrder() {
        RecoveryProposal live = proposal(RecoveryAction.WRITE_OFF_CHARGEBACK, RecoveryProposalStatus.PENDING, NOW.plusSeconds(60));
        assertThat(DualControlRules.checkConfirmation(live, "checker", List.of("ROLE_FINANCE"), NOW)).isNull();
        assertThat(DualControlRules.checkConfirmation(live, "maker", List.of("ROLE_FINANCE"), NOW).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(DualControlRules.checkConfirmation(live, null, List.of("ROLE_FINANCE"), NOW).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(DualControlRules.checkConfirmation(live, "checker", List.of("ROLE_ORGANIZER"), NOW).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);

        RecoveryProposal stale = proposal(RecoveryAction.WRITE_OFF_CHARGEBACK, RecoveryProposalStatus.PENDING, NOW.minusSeconds(1));
        var expired = DualControlRules.checkConfirmation(stale, "checker", List.of("ROLE_FINANCE"), NOW);
        assertThat(expired.errorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(expired.details()).containsEntry("currentStatus", "EXPIRED");
        assertThat(DualControlRules.checkConfirmation(stale, "maker", List.of("ROLE_FINANCE"), NOW).errorCode())
                .as("a stranger is told nothing about the state").isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
    }

    @Test
    @DisplayName("a proposal expires at its instant, not a moment after; one already decided stays what it is")
    void expiryBoundary() {
        RecoveryProposal p = proposal(RecoveryAction.TRANSFER_PLATFORM_FUNDS, RecoveryProposalStatus.PENDING, NOW);
        assertThat(p.statusAt(NOW.minusMillis(1))).isEqualTo(RecoveryProposalStatus.PENDING);
        assertThat(p.statusAt(NOW)).isEqualTo(RecoveryProposalStatus.EXPIRED);
        for (RecoveryProposalStatus decided : new RecoveryProposalStatus[]{RecoveryProposalStatus.CONFIRMED, RecoveryProposalStatus.WITHDRAWN, RecoveryProposalStatus.FAILED}) {
            assertThat(proposal(RecoveryAction.TRANSFER_PLATFORM_FUNDS, decided, NOW).statusAt(NOW.plusSeconds(1))).isEqualTo(decided);
        }
        assertThat(DualControlRules.PROPOSAL_TTL.toHours()).isEqualTo(2);
    }
}
