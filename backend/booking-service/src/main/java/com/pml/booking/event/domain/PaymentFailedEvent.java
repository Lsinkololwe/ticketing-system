package com.pml.booking.event.domain;


import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when payment fails.
 *
 * Internal Listeners:
 * - ReservationReleaseListener: Releases ticket reservation, restores inventory
 *
 * External Listeners (via Azure Service Bus):
 * - Catalog Service: Increments available tickets
 * - Identity Service: Notifies buyer of failed payment
 */
/** Cross-service wire name (ET-PLT-003 §4): payment-events::PaymentFailed — staged into the outbox. */
public record PaymentFailedEvent(
        String paymentIntentId,
        String reservationId,
        String eventId,
        String buyerId,
        BigDecimal amount,
        String currency,
        String paymentProvider,
        String failureReason,
        String failureCode,
        Instant occurredAt
) {
    public PaymentFailedEvent(
            String paymentIntentId,
            String reservationId,
            String eventId,
            String buyerId,
            BigDecimal amount,
            String currency,
            String paymentProvider,
            String failureReason,
            String failureCode
    ) {
        this(paymentIntentId, reservationId, eventId, buyerId, amount, currency,
                paymentProvider, failureReason, failureCode, Instant.now());
    }
}
