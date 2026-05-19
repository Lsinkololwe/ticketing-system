package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an organizer cancels their event.
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Initiates automatic refunds for all tickets
 * - Identity Service: Notifies all ticket holders about cancellation
 *
 * This triggers the refund saga which will:
 * 1. Mark all tickets as REFUND_PENDING
 * 2. Create automatic refund requests
 * 3. Cancel pending commission (not earned yet)
 * 4. Process refunds via payment provider
 */
@Externalized("event-events::EventCancelled")
public record EventCancelledEvent(
        String eventId,
        String organizerId,
        String eventTitle,
        LocalDateTime eventDate,
        String cancellationReason,
        String cancelledBy,
        int totalTicketsSold,
        Instant occurredAt
) {
    public EventCancelledEvent(
            String eventId,
            String organizerId,
            String eventTitle,
            LocalDateTime eventDate,
            String cancellationReason,
            String cancelledBy,
            int totalTicketsSold
    ) {
        this(eventId, organizerId, eventTitle, eventDate, cancellationReason,
                cancelledBy, totalTicketsSold, Instant.now());
    }
}
