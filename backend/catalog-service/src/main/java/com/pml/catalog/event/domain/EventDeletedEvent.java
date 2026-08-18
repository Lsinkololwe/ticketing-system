package com.pml.catalog.event.domain;


import java.time.Instant;

/**
 * Domain event published when an event is soft deleted.
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Clean up escrow account, handle any pending tickets
 * - Identity Service: Update organizer statistics
 */
/** Cross-service wire name (ET-PLT-003 §4): event-events::EventDeleted — staged into the outbox. */
public record EventDeletedEvent(
        String eventId,
        String organizerId,
        String organizationId,
        String eventTitle,
        String deletedBy,
        String deletionReason,
        int ticketsSold,
        Instant occurredAt
) {
    public EventDeletedEvent(
            String eventId,
            String organizerId,
            String organizationId,
            String eventTitle,
            String deletedBy,
            String deletionReason,
            int ticketsSold
    ) {
        this(eventId, organizerId, organizationId, eventTitle, deletedBy,
             deletionReason, ticketsSold, Instant.now());
    }
}
