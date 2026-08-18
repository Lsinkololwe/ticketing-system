package com.pml.catalog.event.domain;


import java.time.Instant;

/**
 * Domain event published when an admin approves an event for publishing.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Notifies organizer that their event was approved
 */
/** Cross-service wire name (ET-PLT-003 §4): event-events::EventApproved — staged into the outbox. */
public record EventApprovedEvent(
        String eventId,
        String organizerId,
        String eventTitle,
        String approvedBy,
        String comments,
        Instant occurredAt
) {
    public EventApprovedEvent(
            String eventId,
            String organizerId,
            String eventTitle,
            String approvedBy,
            String comments
    ) {
        this(eventId, organizerId, eventTitle, approvedBy, comments, Instant.now());
    }
}
