package com.pml.identity.workflow.contactchange;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/** The decisions of the contact-change process that need neither a server nor a database. */
public final class ContactChangeRules {

    /** Version marker of the step sequence, declared from the first release. */
    public static final String STEPS = "contact-change-steps";

    /** How long a change may wait for its codes (spec 004 R5, repair D8 alerts at 48 h). */
    public static final Duration EXPIRY = Duration.ofHours(48);

    /** Wrong or expired codes tolerated before the change is abandoned. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private ContactChangeRules() {
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

    /** Cleanup after a refusal: a few tries, then the repair schedule (D8) takes it. */
    static ActivityOptions cleanupOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ACCOUNT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumAttempts(6)
                        .build())
                .build();
    }
}
