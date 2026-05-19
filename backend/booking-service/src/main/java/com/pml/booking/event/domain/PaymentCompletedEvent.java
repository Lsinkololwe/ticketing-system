package com.pml.booking.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when payment is successfully processed.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Updates user payment history
 */
@Externalized("payment-events::PaymentCompleted")
public record PaymentCompletedEvent(
        String paymentIntentId,
        String ticketId,
        String eventId,
        String buyerId,
        BigDecimal amount,
        String currency,
        String paymentProvider,
        String correspondent,
        String providerTransactionId,
        String phoneNumber,
        Instant processedAt,
        Instant occurredAt
) {
    public PaymentCompletedEvent(
            String paymentIntentId,
            String ticketId,
            String eventId,
            String buyerId,
            BigDecimal amount,
            String currency,
            String paymentProvider,
            String correspondent,
            String providerTransactionId,
            String phoneNumber,
            Instant processedAt
    ) {
        this(paymentIntentId, ticketId, eventId, buyerId, amount, currency,
                paymentProvider, correspondent, providerTransactionId, phoneNumber,
                processedAt, Instant.now());
    }
}
