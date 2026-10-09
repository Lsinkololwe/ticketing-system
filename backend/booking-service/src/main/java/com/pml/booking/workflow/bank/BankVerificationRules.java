package com.pml.booking.workflow.bank;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * The micro-deposit rules, testable without a server.
 */
public final class BankVerificationRules {

    /** The {@code finance.payout.micro-deposit} setting. */
    public static final BigDecimal BASE_DEPOSIT = new BigDecimal("0.50");

    public static final int MAX_ATTEMPTS = 3;

    /** The {@code finance.payout.verification-lockout} setting. */
    public static final Duration LOCKOUT = Duration.ofHours(24);

    /** An unconfirmed deposit expires, and the owner starts again with a new one. */
    public static final Duration EXPIRY = Duration.ofDays(7);

    private BankVerificationRules() {
    }

    /**
     * K0.50 moved by one to nine ngwee in either direction, so the owner has to read the amount rather
     * than guess the advertised one. {@code draw} comes from the workflow's replay-safe random.
     */
    public static BigDecimal depositFor(int draw) {
        int cents = Math.floorMod(draw, 9) + 1;
        BigDecimal delta = BigDecimal.valueOf(cents, 2);
        return (Math.floorMod(draw, 2) == 0 ? BASE_DEPOSIT.add(delta) : BASE_DEPOSIT.subtract(delta)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A sent deposit's status is first asked after this, then at doubling intervals. */
    static final Duration FIRST_DEPOSIT_POLL = Duration.ofSeconds(30);

    /** The longest wait between two questions about one deposit. */
    static final Duration LAST_DEPOSIT_POLL = Duration.ofHours(1);

    public static Duration depositPollDelay(int poll) {
        if (poll >= 7) {
            return LAST_DEPOSIT_POLL;
        }
        Duration delay = FIRST_DEPOSIT_POLL.multipliedBy(1L << poll);
        return delay.compareTo(LAST_DEPOSIT_POLL) > 0 ? LAST_DEPOSIT_POLL : delay;
    }

    public static boolean matches(BigDecimal sent, BigDecimal confirmed) {
        return sent != null && confirmed != null && sent.compareTo(confirmed) == 0;
    }

    public static boolean locksAfter(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    static ActivityOptions accountOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.FINANCE)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    static ActivityOptions depositOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.PROVIDER)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(30))
                        .setMaximumInterval(Duration.ofMinutes(10))
                        .setMaximumAttempts(5)
                        .build())
                .build();
    }
}
