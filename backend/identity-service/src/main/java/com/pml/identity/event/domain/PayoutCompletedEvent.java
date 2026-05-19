package com.pml.identity.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a payout to an organizer is successfully completed.
 *
 * Business Intent: Confirm that funds have been successfully transferred to the
 * organizer's mobile money account. Update organizer dashboard and records.
 *
 * External Listeners (via Azure Service Bus):
 * - Notification Service: Send payout confirmation to organizer
 * - Finance Audit: Record completed payout for reconciliation
 */
@Externalized("payment-events::PayoutCompleted")
public record PayoutCompletedEvent(
        String payoutRequestId,
        String organizerId,
        String eventId,
        BigDecimal amount,
        String currency,
        String providerTransactionId,
        Instant occurredAt
) {
    public PayoutCompletedEvent(
            String payoutRequestId,
            String organizerId,
            String eventId,
            BigDecimal amount,
            String currency,
            String providerTransactionId
    ) {
        this(payoutRequestId, organizerId, eventId, amount, currency,
                providerTransactionId, Instant.now());
    }
}
