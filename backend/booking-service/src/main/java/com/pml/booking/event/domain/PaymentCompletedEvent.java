package com.pml.booking.event.domain;


import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when payment is successfully processed.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Updates user payment history
 */
/** Cross-service wire name (ET-PLT-003 §4): payment-events::PaymentCompleted — staged into the outbox. */
public record PaymentCompletedEvent(
        String paymentIntentId,
        String reservationId,
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
            String reservationId,
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
        this(paymentIntentId, reservationId, eventId, buyerId, amount, currency,
                paymentProvider, correspondent, providerTransactionId, phoneNumber,
                processedAt, Instant.now());
    }
}
