package com.pml.booking.workflow.purchase;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * The checkout decisions that need neither a server nor a database.
 */
public final class PurchaseRules {

    /** The {@code booking.payment.max-pending} setting: a payment pending this long is escalated. */
    public static final Duration MAX_PENDING = Duration.ofMinutes(30);

    /**
     * With a payment in flight the seats are held this long past {@code expiresAt}, so a buyer
     * approving the prompt in the last seconds is not refused; after it they return to the pool and a
     * late payment is escalated for refund rather than sold seats being taken back.
     */
    public static final Duration SEAT_GRACE = Duration.ofMinutes(5);

    /** An unanswered payment stops being polled after a week; the escalation already stands. */
    public static final Duration GIVE_UP = Duration.ofDays(7);

    /** A started execution that receives no reservation in this window closes. */
    public static final Duration RESERVE_WINDOW = Duration.ofMinutes(5);

    static final Duration FIRST_POLL = Duration.ofSeconds(10);
    static final Duration LAST_POLL = Duration.ofMinutes(5);
    static final Duration ESCALATED_POLL = Duration.ofHours(1);

    private PurchaseRules() {
    }

    /**
     * The reservation id for a buyer's idempotency key. The same buyer and key always name the
     * same reservation, so every retry reaches one execution; two buyers reusing a key never collide.
     */
    public static String reservationIdFor(String userId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes(("purchase:" + userId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Ten, twenty, forty seconds and so on to five minutes; hourly once the payment is escalated. */
    public static Duration pollDelay(int poll, boolean escalated) {
        if (escalated) {
            return ESCALATED_POLL;
        }
        if (poll >= 5) {
            return LAST_POLL;
        }
        Duration delay = FIRST_POLL.multipliedBy(1L << poll);
        return delay.compareTo(LAST_POLL) > 0 ? LAST_POLL : delay;
    }

    static ActivityOptions checkoutOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.CHECKOUT)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build())
                .build();
    }

    /** A buyer is waiting on the hold, so it gets a short budget; the compensation returns anything taken. */
    static ActivityOptions holdOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.CHECKOUT)
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofMillis(500))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }
}
