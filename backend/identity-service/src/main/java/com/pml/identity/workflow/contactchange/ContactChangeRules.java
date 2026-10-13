package com.pml.identity.workflow.contactchange;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/** The decisions of the contact-change process that need neither a server nor a database. */
public final class ContactChangeRules {

    /** Version marker of the step sequence, declared from the first release. */
    public static final String STEPS = "contact-change-steps";

    /** How long a change may wait for its codes (spec 004 R5). */
    public static final Duration EXPIRY = Duration.ofHours(48);

    /**
     * The longest a change execution may live: the wait for codes plus a day to finish applying. The
     * execution is started with this as its ceiling, so the server closes it at that point whatever it
     * is doing, and the marker it sets records the same ceiling as its expiry.
     */
    public static final Duration MAX_LIFETIME = EXPIRY.plus(Duration.ofHours(24));

    /** Wrong or expired codes tolerated before the change is abandoned. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private ContactChangeRules() {
    }

    /**
     * Whether a marker is past the time its change could still be open. A marker written before expiries
     * were recorded has none of its own, so its age is measured from when it was set; one with neither is
     * never "expired", and only the server's word on whether its execution is open can release it.
     */
    public static boolean expired(java.time.Instant expiresAt, java.time.Instant since, java.time.Instant now) {
        if (expiresAt != null) {
            return !now.isBefore(expiresAt);
        }
        return since != null && !now.isBefore(since.plus(MAX_LIFETIME));
    }

    public static int attemptsRemaining(int failures) {
        return Math.max(0, MAX_FAILED_ATTEMPTS - failures);
    }

    /** As the ensure process: retried without a limit, because Keycloak or Mongo being away is a reason to wait. */
    static ActivityOptions options() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ACCOUNT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** Telling people is never worth holding the change open for. */
    static ActivityOptions noticeOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ACCOUNT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }

    /**
     * Cleanup after a refusal: retried until it lands, backing off to a minute between tries. The marker blocks
     * the account's next contact change, so giving up would leave it blocked with nothing left to clear it.
     */
    static ActivityOptions cleanupOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ACCOUNT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
