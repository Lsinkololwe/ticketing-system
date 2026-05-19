package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an event is rescheduled to a new date.
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Updates escrow lock date
 * - Identity Service: Notifies all ticket holders with refund option
 *
 * Reschedule opens a 7-day refund window where ticket holders can request
 * full refunds regardless of normal refund policy.
 */
@Externalized("event-events::EventRescheduled")
public record EventRescheduledEvent(
        String eventId,
        String organizerId,
        String eventTitle,
        LocalDateTime originalDate,
        LocalDateTime newDate,
        LocalDateTime originalEndDate,
        LocalDateTime newEndDate,
        String reason,
        int totalTicketsSold,
        Instant refundWindowEnds,
        Instant occurredAt
) {
    public EventRescheduledEvent(
            String eventId,
            String organizerId,
            String eventTitle,
            LocalDateTime originalDate,
            LocalDateTime newDate,
            LocalDateTime originalEndDate,
            LocalDateTime newEndDate,
            String reason,
            int totalTicketsSold
    ) {
        this(eventId, organizerId, eventTitle, originalDate, newDate, originalEndDate,
                newEndDate, reason, totalTicketsSold,
                Instant.now().plusSeconds(7 * 24 * 60 * 60), // 7 days refund window
                Instant.now());
    }
}
