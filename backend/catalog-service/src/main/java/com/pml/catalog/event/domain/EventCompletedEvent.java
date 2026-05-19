package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an event is marked as completed (event date has passed).
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Locks escrow account, starts 7-day hold period
 *
 * After 7 days:
 * - Escrow becomes PAYOUT_ELIGIBLE
 * - Pending commission becomes EARNED
 * - Organizer can request payout
 */
@Externalized("event-events::EventCompleted")
public record EventCompletedEvent(
        String eventId,
        String organizerId,
        String eventTitle,
        LocalDateTime eventDate,
        int totalTicketsSold,
        int totalTicketsValidated,
        BigDecimal grossRevenue,
        BigDecimal pendingCommission,
        LocalDateTime holdUntil,
        Instant occurredAt
) {
    public EventCompletedEvent(
            String eventId,
            String organizerId,
            String eventTitle,
            LocalDateTime eventDate,
            int totalTicketsSold,
            int totalTicketsValidated,
            BigDecimal grossRevenue,
            BigDecimal pendingCommission
    ) {
        this(eventId, organizerId, eventTitle, eventDate, totalTicketsSold,
                totalTicketsValidated, grossRevenue, pendingCommission,
                eventDate.plusDays(7), // 7-day hold period
                Instant.now());
    }
}
