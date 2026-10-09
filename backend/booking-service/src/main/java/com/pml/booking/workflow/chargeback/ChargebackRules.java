package com.pml.booking.workflow.chargeback;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.constants.ChargebackStatus;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which chargeback decisions are open from which status, and the workflow's budgets.
 */
public final class ChargebackRules {

    public static final Duration SUBMIT_WINDOW = Duration.ofMinutes(5);

    /** A disputed chargeback whose provider outcome never arrives stops waiting after this long. */
    public static final Duration OUTCOME_LIMIT = Duration.ofDays(90);

    /** An undecided chargeback is escalated to finance this long before its response deadline. */
    public static final Duration ESCALATE_BEFORE_DEADLINE = Duration.ofHours(24);

    public static final String SYSTEM_ACTOR = "SYSTEM";

    static final Set<ChargebackStatus> UNDECIDED = EnumSet.of(ChargebackStatus.RECEIVED, ChargebackStatus.UNDER_REVIEW);

    private ChargebackRules() {
    }

    public static boolean canReview(ChargebackStatus status) {
        return status == ChargebackStatus.RECEIVED;
    }

    public static boolean canDecide(ChargebackStatus status) {
        return UNDECIDED.contains(status);
    }

    public static boolean awaitsOutcome(ChargebackStatus status) {
        return status == ChargebackStatus.DISPUTED;
    }

    static ActivityOptions options() {
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
