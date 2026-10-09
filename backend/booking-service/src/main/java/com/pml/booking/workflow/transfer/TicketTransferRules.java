package com.pml.booking.workflow.transfer;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

public final class TicketTransferRules {

    /** How long a started execution waits for its {@code begin}. */
    public static final Duration BEGIN_WINDOW = Duration.ofMinutes(5);

    public static final String SYSTEM_ACTOR = "SYSTEM";

    private TicketTransferRules() {
    }

    /** Database writes: retried generously, since each is idempotent. */
    static ActivityOptions writeOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .setMaximumAttempts(8)
                        .setDoNotRetry("TICKET_STATE_INVALID", "TRANSFER_NOT_PENDING", "TICKET_UNKNOWN", "TICKET_TRANSFER_UNKNOWN",
                                "ACTOR_NOT_PERMITTED", "TRANSFER_TO_SELF", "TICKET_NOT_TRANSFERABLE")
                        .build())
                .build();
    }

    /** Notifications: a few tries, then the offer carries on without them. */
    static ActivityOptions notifyOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(20))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(2))
                        .setMaximumAttempts(4)
                        .build())
                .build();
    }
}
