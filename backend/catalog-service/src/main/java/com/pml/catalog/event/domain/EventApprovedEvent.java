package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;

/**
 * Domain event published when an admin approves an event for publishing.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Notifies organizer that their event was approved
 */
@Externalized("event-events::EventApproved")
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
