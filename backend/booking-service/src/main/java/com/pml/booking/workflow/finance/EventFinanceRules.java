package com.pml.booking.workflow.finance;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * The hold arithmetic and the workflow's budgets.
 */
public final class EventFinanceRules {

    /** The {@code finance.escrow.hold-period} setting. */
    public static final Duration HOLD_PERIOD = Duration.ofDays(7);

    /** With disputes open, eligibility is re-checked at least this often. */
    public static final Duration DISPUTE_RECHECK = Duration.ofDays(1);

    /** An execution started by a fact that never leads anywhere closes after this long. */
    public static final Duration IDLE_LIMIT = Duration.ofDays(60);

    /** Refunds are batched this many tickets at a time, then the execution continues as new. */
    public static final int REFUND_BATCH = 200;

    private EventFinanceRules() {
    }

    public static long holdUntil(long endMillis) {
        return endMillis + HOLD_PERIOD.toMillis();
    }

    public static ActivityOptions financeOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.FINANCE)
                .setStartToCloseTimeout(Duration.ofSeconds(60))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(2))
                        .build())
                .build();
    }
}
