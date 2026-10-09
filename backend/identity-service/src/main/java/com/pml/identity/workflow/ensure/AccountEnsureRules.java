package com.pml.identity.workflow.ensure;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/** The decisions of the ensure process that need neither a server nor a database. */
public final class AccountEnsureRules {

    /** Version marker of the step sequence, declared from the first release so a later step can be added safely. */
    public static final String STEPS = "account-ensure-steps";

    private AccountEnsureRules() {
    }

    /**
     * Retried without a limit: Keycloak being down or Mongo failing over is the normal reason an
     * account is not finished, and the answer to it is to wait, never to give up and delete. The
     * interval is capped so recovery is noticed within a minute. A refusal is non-retryable by
     * construction and ends the activity at once.
     */
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
}
