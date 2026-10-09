package com.pml.booking.workflow.refund;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;

/**
 * When a refund waiting for a person is escalated, and how long a refund waits on its provider.
 */
public final class RefundRules {

    /**
     * A refund waiting for a person is escalated to finance after each of these,
     * counted from when it began waiting. Only an event cancellation's refund approves itself.
     */
    public static final List<Duration> REVIEW_ESCALATIONS = List.of(Duration.ofDays(2), Duration.ofDays(5));

    /** An unanswered provider refund is polled this long before the execution stops waiting. */
    public static final Duration ANSWER_LIMIT = Duration.ofDays(30);

    public static final Duration SUBMIT_WINDOW = Duration.ofMinutes(5);

    public static final String SYSTEM_ACTOR = "SYSTEM";

    static final Duration FIRST_POLL = Duration.ofSeconds(30);
    static final Duration LAST_POLL = Duration.ofMinutes(10);

    private RefundRules() {
    }

    /** Whether an ask for {@code asked} names a different sum than the request already open for {@code open}; an ask with no amount names none. */
    public static boolean asksForDifferentAmount(java.math.BigDecimal asked, java.math.BigDecimal open) {
        return asked != null && open != null && asked.compareTo(open) != 0;
    }

    /** What a caller is told when they ask for a different sum while a refund is already open for the ticket. */
    public static String openRefundMessage(java.math.BigDecimal open) {
        return "this ticket already has an open refund request for " + open.toPlainString()
                + "; withdraw it before asking for a different amount";
    }

    /** How long a request has waited when escalation {@code level} (1-based) fires; an unknown level is refused. */
    public static Duration reviewEscalation(int level) {
        if (level < 1 || level > REVIEW_ESCALATIONS.size()) {
            throw new IllegalArgumentException("no refund review escalation " + level);
        }
        return REVIEW_ESCALATIONS.get(level - 1);
    }

    public static Duration pollDelay(int poll) {
        if (poll >= 5) {
            return LAST_POLL;
        }
        Duration delay = FIRST_POLL.multipliedBy(1L << poll);
        return delay.compareTo(LAST_POLL) > 0 ? LAST_POLL : delay;
    }

    static ActivityOptions refundOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.FINANCE)
                .setStartToCloseTimeout(Duration.ofSeconds(60))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(2))
                        .build())
                .build();
    }

    static ActivityOptions providerOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.PROVIDER)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(30))
                        .setMaximumInterval(Duration.ofMinutes(10))
                        .build())
                .build();
    }
}
