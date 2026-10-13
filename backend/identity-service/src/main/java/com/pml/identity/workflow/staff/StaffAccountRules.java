package com.pml.identity.workflow.staff;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/** The decisions of the staff-creation process that need neither a server nor a database. */
public final class StaffAccountRules {

    /** Version marker of the step sequence, declared from the first release. */
    public static final String STEPS = "staff-account-steps";

    /**
     * How many times a step is attempted before the creation is undone. About half a minute of waiting
     * in all: long enough to ride out a Keycloak restart, short enough that an administrator whose
     * request cannot succeed (a password the realm's policy refuses) is told rather than kept waiting.
     */
    static final int ATTEMPTS = 6;

    private StaffAccountRules() {
    }

    /** Writes and the Keycloak calls: a few attempts with backoff, then the creation is undone. */
    static ActivityOptions options() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build())
                .build();
    }

    /**
     * Undoing is retried until it lands: giving up would leave exactly what the undo exists to remove,
     * and nothing else would come back for it.
     */
    static ActivityOptions undoOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** Telling the person is never worth holding the creation open for. */
    static ActivityOptions noticeOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }
}
