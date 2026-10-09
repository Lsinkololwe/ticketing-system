package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.enums.TransferStatus;
import com.pml.shared.workflow.Refusal;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R7 · transfer rules: the expiry, and which party may answer")
class OwnershipTransferRulesTest {

    private static final long EXPIRES = 1_000_000L;

    private static View pending() {
        return new View("t-1", "org-1", "owner", "nominee", TransferStatus.PENDING, EXPIRES);
    }

    @Test
    @DisplayName("§4 · the TTL is three days, and expiry is inclusive of its instant")
    void expiry() {
        assertThat(OwnershipTransferRules.TTL).isEqualTo(Duration.ofDays(3));
        assertThat(OwnershipTransferRules.expired(EXPIRES, EXPIRES - 1)).isFalse();
        assertThat(OwnershipTransferRules.expired(EXPIRES, EXPIRES)).isTrue();
        assertThat(OwnershipTransferRules.remaining(EXPIRES, EXPIRES - 500)).isEqualTo(Duration.ofMillis(500));
        assertThat(OwnershipTransferRules.remaining(EXPIRES, EXPIRES + 500)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("R7 · only the nominee accepts, only while pending, only before expiry")
    void accept() {
        assertThat(OwnershipTransferRules.acceptRefusal(pending(), "nominee", EXPIRES - 1)).isEmpty();
        assertThat(OwnershipTransferRules.acceptRefusal(pending(), "owner", EXPIRES - 1))
                .map(Refusal::code).contains(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(OwnershipTransferRules.acceptRefusal(pending(), "nominee", EXPIRES))
                .map(Refusal::code).contains(ErrorCode.TRANSFER_NOT_PENDING);
        View cancelled = new View("t-1", "org-1", "owner", "nominee", TransferStatus.CANCELLED, EXPIRES);
        assertThat(OwnershipTransferRules.acceptRefusal(cancelled, "nominee", 0))
                .map(Refusal::code).contains(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(OwnershipTransferRules.acceptRefusal(null, "nominee", 0))
                .map(Refusal::code).contains(ErrorCode.TRANSFER_NOT_PENDING);
    }

    @Test
    @DisplayName("R7 · the nominee declines; the initiating owner cancels; neither does the other's")
    void declineAndCancel() {
        assertThat(OwnershipTransferRules.declineRefusal(pending(), "nominee")).isEmpty();
        assertThat(OwnershipTransferRules.declineRefusal(pending(), "owner")).map(Refusal::code).contains(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(OwnershipTransferRules.cancelRefusal(pending(), "owner")).isEmpty();
        assertThat(OwnershipTransferRules.cancelRefusal(pending(), "nominee")).map(Refusal::code).contains(ErrorCode.ACTOR_NOT_PERMITTED);
    }

    @Test
    @DisplayName("R7 · a nomination names every party, and an owner cannot nominate themselves")
    void nomination() {
        assertThat(OwnershipTransferRules.nominationRefusal(new Nomination("t", "o", "a", "b", null))).isEmpty();
        assertThat(OwnershipTransferRules.nominationRefusal(new Nomination("t", "o", "a", "a", null)))
                .map(Refusal::code).contains(ErrorCode.TRANSFER_TO_SELF);
        assertThat(OwnershipTransferRules.nominationRefusal(new Nomination("t", " ", "a", "b", null)))
                .map(Refusal::code).contains(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("R8 · the Keycloak mirror is retried ten times before it is left to the repair Schedule")
    void mirrorBudget() {
        assertThat(OwnershipTransferRules.mirrorOptions().getRetryOptions().getMaximumAttempts())
                .isEqualTo(OwnershipTransferRules.MIRROR_ATTEMPTS)
                .isEqualTo(10);
    }
}
