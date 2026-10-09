package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Optional;

/**
 * The onboarding decisions that need neither a server nor a database.
 *
 * <p>Kept out of the workflow class so each is tested at layer 1 and the workflow reads as the
 * sequence it is. The validators and the handlers ask the same questions here, so a refusal a
 * validator raises and one a handler raises cannot drift apart.
 */
public final class OnboardingRules {

    /** How many times a Keycloak step is attempted before the approval compensates. */
    public static final int KEYCLOAK_ATTEMPTS = 3;

    /** The first wait between Keycloak attempts, doubling. */
    public static final Duration KEYCLOAK_BACKOFF = Duration.ofSeconds(2);

    /** An execution started with no command closes after this long if nothing waits on a person. */
    public static final Duration FIRST_COMMAND_WINDOW = Duration.ofMinutes(1);

    /** A MongoDB step that cannot land in this many attempts fails its command rather than hanging it. */
    static final int RECORD_ATTEMPTS = 5;

    private OnboardingRules() {
    }

    /** The two states in which the organization waits on a person, and so the execution stays open. */
    public static boolean awaitingPerson(OrganizationStatus status) {
        return status == OrganizationStatus.PENDING_REVIEW || status == OrganizationStatus.CHANGES_REQUESTED;
    }

    /** Approve is legal only from {@code PENDING_REVIEW}, and one approval runs at a time. */
    public static Optional<Refusal> approvalRefusal(OrganizationStatus status, boolean approving) {
        if (status == null) {
            return Optional.of(new Refusal(ErrorCode.ORGANIZATION_UNKNOWN, "no such organization"));
        }
        if (approving) {
            return Optional.of(new Refusal(ErrorCode.ORGANIZATION_STATE_INVALID, "an approval is already running"));
        }
        if (status != OrganizationStatus.PENDING_REVIEW) {
            return Optional.of(new Refusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                    "the organization is " + status + ", not PENDING_REVIEW"));
        }
        return Optional.empty();
    }

    /** Reject and request-changes need a reason and the same state approval does. */
    public static Optional<Refusal> decisionRefusal(OrganizationStatus status, boolean approving, String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.of(new Refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a review decision needs a reason"));
        }
        return approvalRefusal(status, approving);
    }

    /** A submission is legal from {@code DRAFT} and {@code CHANGES_REQUESTED}. */
    public static Optional<Refusal> submissionRefusal(OrganizationStatus status) {
        if (status == null) {
            return Optional.of(new Refusal(ErrorCode.ORGANIZATION_UNKNOWN, "no such organization"));
        }
        if (status != OrganizationStatus.DRAFT && status != OrganizationStatus.CHANGES_REQUESTED) {
            return Optional.of(new Refusal(ErrorCode.ORGANIZATION_STATE_INVALID,
                    "an organization that is " + status + " cannot be submitted for review"));
        }
        return Optional.empty();
    }

    /** What compensation records, naming the step that failed. */
    public static String compensationReason(OnboardingStep failedAt, String cause) {
        return "approval step " + failedAt.marker() + " (" + failedAt.description()
                + ") failed and the approval was reversed: " + (cause == null ? "no cause reported" : cause);
    }

    /** MongoDB writes a command waits on: retried briefly, refusals not at all. */
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

    /** The Keycloak steps: three attempts, from two seconds, doubling; then compensation begins. */
    static ActivityOptions keycloakOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(KEYCLOAK_ATTEMPTS)
                        .setInitialInterval(KEYCLOAK_BACKOFF)
                        .setBackoffCoefficient(2.0)
                        .build())
                .build();
    }

    /**
     * Compensations and reads the workflow cannot proceed without: retried until they land, because
     * a compensation that gives up leaves an organization partially approved.
     */
    static ActivityOptions patientOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(KEYCLOAK_BACKOFF)
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
