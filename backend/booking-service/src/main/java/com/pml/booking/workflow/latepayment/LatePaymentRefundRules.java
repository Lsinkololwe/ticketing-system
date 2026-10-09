package com.pml.booking.workflow.latepayment;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * How long late money waits for the provider before it is handed to a person.
 */
public final class LatePaymentRefundRules {

    /** An unanswered provider refund is polled this long before it is escalated. */
    public static final Duration ANSWER_LIMIT = Duration.ofHours(72);

    /** The provider is asked this many times, over about ten minutes, before the refund is escalated. */
    static final int SUBMIT_ATTEMPTS = 6;

    static final Duration FIRST_POLL = Duration.ofSeconds(30);
    static final Duration LAST_POLL = Duration.ofMinutes(10);

    private LatePaymentRefundRules() {
    }

    /** Thirty seconds doubling to ten minutes. */
    public static Duration pollDelay(int poll) {
        if (poll >= 5) {
            return LAST_POLL;
        }
        Duration delay = FIRST_POLL.multipliedBy(1L << poll);
        return delay.compareTo(LAST_POLL) > 0 ? LAST_POLL : delay;
    }

    static ActivityOptions writeOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.CHECKOUT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build())
                .build();
    }

    /** Bounded: a provider that stays silent ends in the escalation, not in an endless retry. */
    static ActivityOptions providerOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.PROVIDER)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(10))
                        .setBackoffCoefficient(2)
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .setMaximumAttempts(SUBMIT_ATTEMPTS)
                        .build())
                .build();
    }
}
