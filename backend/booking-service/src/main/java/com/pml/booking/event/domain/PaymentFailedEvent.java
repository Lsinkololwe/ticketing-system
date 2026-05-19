package com.pml.booking.event.domain;

import org.springframework.modulith.events.Externalized;

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
@Externalized("payment-events::PaymentFailed")
public record PaymentFailedEvent(
        String paymentIntentId,
        String ticketId,
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
            String ticketId,
            String eventId,
            String buyerId,
            BigDecimal amount,
            String currency,
            String paymentProvider,
            String failureReason,
            String failureCode
    ) {
        this(paymentIntentId, ticketId, eventId, buyerId, amount, currency,
                paymentProvider, failureReason, failureCode, Instant.now());
    }
}
