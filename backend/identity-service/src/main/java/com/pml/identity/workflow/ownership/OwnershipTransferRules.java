package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Optional;

/**
 * The transfer decisions that need neither a server nor a database.
 */
public final class OwnershipTransferRules {

    /** {@code identity.transfer.ttl}. */
    public static final Duration TTL = Duration.ofDays(3);

    /** An execution started with no command closes after this long. */
    public static final Duration FIRST_COMMAND_WINDOW = Duration.ofMinutes(1);

    /** Attempts at the Keycloak mirror before the memberships are marked for the repair Schedule. */
    public static final int MIRROR_ATTEMPTS = 10;

    static final int RECORD_ATTEMPTS = 5;

    private OwnershipTransferRules() {
    }

    /** Expiry is inclusive: at {@code expiresAt} exactly, the transfer can no longer be confirmed. */
    public static boolean expired(long expiresAtMillis, long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public static Duration remaining(long expiresAtMillis, long nowMillis) {
        return Duration.ofMillis(Math.max(0L, expiresAtMillis - nowMillis));
    }

    public static Optional<Refusal> nominationRefusal(Nomination nomination) {
        if (blank(nomination.transferId()) || blank(nomination.organizationId())
                || blank(nomination.currentOwnerId()) || blank(nomination.newOwnerId())) {
            return Optional.of(new Refusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a nomination names its transfer, organization, owner and nominee"));
        }
        if (nomination.currentOwnerId().equals(nomination.newOwnerId())) {
            return Optional.of(new Refusal(ErrorCode.TRANSFER_TO_SELF, "an owner cannot nominate themselves"));
        }
        return Optional.empty();
    }

    /** Only the nominee confirms, only while pending, and only before expiry. */
    public static Optional<Refusal> acceptRefusal(View view, String actorId, long nowMillis) {
        Optional<Refusal> refusal = partyRefusal(view, actorId, view == null ? null : view.newOwnerId(), "nominee");
        if (refusal.isPresent()) {
            return refusal;
        }
        if (expired(view.expiresAtMillis(), nowMillis)) {
            return Optional.of(new Refusal(ErrorCode.TRANSFER_NOT_PENDING, "the transfer has expired"));
        }
        return Optional.empty();
    }

    public static Optional<Refusal> declineRefusal(View view, String actorId) {
        return partyRefusal(view, actorId, view == null ? null : view.newOwnerId(), "nominee");
    }

    /** The initiating owner cancels while pending. */
    public static Optional<Refusal> cancelRefusal(View view, String actorId) {
        return partyRefusal(view, actorId, view == null ? null : view.currentOwnerId(), "initiating owner");
    }

    private static Optional<Refusal> partyRefusal(View view, String actorId, String party, String partyName) {
        if (view == null || view.status() == null) {
            return Optional.of(new Refusal(ErrorCode.TRANSFER_NOT_PENDING, "no such transfer"));
        }
        if (view.status() != TransferStatus.PENDING) {
            return Optional.of(new Refusal(ErrorCode.TRANSFER_NOT_PENDING, "the transfer is " + view.status()));
        }
        if (actorId == null || !actorId.equals(party)) {
            return Optional.of(new Refusal(ErrorCode.ACTOR_NOT_PERMITTED, "only the " + partyName + " may do this"));
        }
        return Optional.empty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static ActivityOptions recordOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(RECORD_ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build())
                .build();
    }

    /** MongoDB wins: the mirror is retried, and a mirror that never lands is left to the repair Schedule. */
    static ActivityOptions mirrorOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(MIRROR_ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }

    static ActivityOptions patientOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
