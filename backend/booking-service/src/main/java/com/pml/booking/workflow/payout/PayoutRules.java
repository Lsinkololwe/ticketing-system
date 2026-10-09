package com.pml.booking.workflow.payout;

import com.pml.booking.domain.PayoutEligibility;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Set;

/**
 * The payout decisions that need neither a server nor a database.
 *
 * <p>Kept out of the workflow class so each can be tested at layer 1, and so the workflow reads as
 * the sequence it is.
 */
public final class PayoutRules {

    /** The {@code finance.payout.max-attempts} setting. */
    public static final int MAX_ATTEMPTS = 3;

    /** A transfer with no verified answer after this long is escalated, never failed. */
    public static final Duration UNCONFIRMED_AFTER = Duration.ofDays(3);

    /** A failed payout waits this long for a retry before the execution closes and a re-request is possible. */
    public static final Duration RETRY_WINDOW = Duration.ofDays(30);

    /** A started execution that receives no request in this window closes. */
    public static final Duration SUBMIT_WINDOW = Duration.ofMinutes(5);

    /** The {@code finance.payout.retry-backoff} setting. */
    public static final Duration TRANSFER_BACKOFF = Duration.ofMinutes(5);

    static final Duration FIRST_POLL = Duration.ofSeconds(30);
    static final Duration LAST_POLL = Duration.ofHours(1);

    public static final String BAD_ACCOUNT_DETAILS = "BAD_ACCOUNT_DETAILS";
    public static final String TRANSFER_REFUSED = "TRANSFER_REFUSED";
    public static final String TRANSFER_UNAVAILABLE = "TRANSFER_UNAVAILABLE";

    /** Provider codes meaning the destination is wrong; retrying them only repeats the refusal. */
    static final Set<String> BAD_ACCOUNT_CODES = Set.of(
            BAD_ACCOUNT_DETAILS, "INVALID_RECIPIENT", "RECIPIENT_NOT_FOUND", "INVALID_PHONE_NUMBER",
            "RECIPIENT_NOT_ALLOWED_TO_RECEIVE", "INVALID_ACCOUNT");

    private PayoutRules() {
    }

    public static boolean canRetry(int attempts) {
        return attempts < MAX_ATTEMPTS;
    }

    public static boolean isBadAccount(String failureCode) {
        return failureCode != null && BAD_ACCOUNT_CODES.contains(failureCode);
    }

    /** Status polls back off from thirty seconds to an hour, so a week-long stall stays a small history. */
    public static Duration pollDelay(int poll) {
        if (poll >= 7) {
            return LAST_POLL;
        }
        Duration delay = FIRST_POLL.multipliedBy(1L << poll);
        return delay.compareTo(LAST_POLL) > 0 ? LAST_POLL : delay;
    }

    public static boolean unconfirmed(long startedMillis, long nowMillis) {
        return nowMillis - startedMillis >= UNCONFIRMED_AFTER.toMillis();
    }

    /** The refusal an eligibility failure is reported as. */
    public static ErrorCode codeFor(PayoutEligibility.Reason reason) {
        return switch (reason) {
            case HOLD_NOT_ELAPSED, EVENT_NOT_COMPLETED, OPEN_DISPUTES -> ErrorCode.PAYOUT_WINDOW_NOT_OPEN;
            case BELOW_MINIMUM -> ErrorCode.PAYOUT_BELOW_MINIMUM;
            case NO_ESCROW_ACCOUNT -> ErrorCode.ESCROW_ACCOUNT_UNKNOWN;
            case ESCROW_SUSPENDED -> ErrorCode.ESCROW_NOT_ACTIVE;
            case PAYOUT_ALREADY_REQUESTED -> ErrorCode.PAYOUT_STATE_INVALID;
        };
    }

    /** MongoDB writes: retried until they land; a refusal is non-retryable and ends the attempt at once. */
    static ActivityOptions ledgerOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.FINANCE)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    /** Three attempts, exponential from five minutes; bad details and refusals are not retried. */
    static ActivityOptions transferOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.PROVIDER)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(MAX_ATTEMPTS)
                        .setInitialInterval(TRANSFER_BACKOFF)
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(BAD_ACCOUNT_DETAILS, TRANSFER_REFUSED)
                        .build())
                .build();
    }

    /** A status read changes nothing, so it is retried for as long as the provider is unreachable. */
    static ActivityOptions statusOptions() {
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
