package com.pml.catalog.event.domain;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an event goes live and tickets become available for purchase.
 *
 * Listeners (Internal - @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)):
 * - None in catalog service
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Creates escrow account for the event
 * - Identity Service: Notifies organizer that event is live
 *
 * This event triggers the creation of financial infrastructure (escrow accounts)
 * needed to handle ticket sales and eventual payout to organizers.
 */
/** Cross-service wire name (ET-PLT-003 §4): event-events::EventPublished — staged into the outbox. */
public record EventPublishedEvent(
        String eventId,
        String organizerId,
        String organizerName,
        String eventTitle,
        LocalDateTime eventDate,
        LocalDateTime endDate,
        int totalCapacity,
        String currency,
        BigDecimal commissionRate,
        Instant occurredAt
) {
    public EventPublishedEvent(
            String eventId,
            String organizerId,
            String organizerName,
            String eventTitle,
            LocalDateTime eventDate,
            LocalDateTime endDate,
            int totalCapacity,
            String currency,
            BigDecimal commissionRate
    ) {
        this(eventId, organizerId, organizerName, eventTitle, eventDate, endDate,
                totalCapacity, currency, commissionRate, Instant.now());
    }

    public static EventPublishedEvent of(
            String eventId,
            String organizerId,
            String organizerName,
            String eventTitle,
            LocalDateTime eventDate,
            LocalDateTime endDate,
            int totalCapacity
    ) {
        return new EventPublishedEvent(
                eventId, organizerId, organizerName, eventTitle, eventDate, endDate,
                totalCapacity, "ZMW", new BigDecimal("0.05")
        );
    }
}
