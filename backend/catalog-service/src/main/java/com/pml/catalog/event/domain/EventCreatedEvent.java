package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when a new event is created.
 *
 * External Listeners (via Azure Service Bus):
 * - Booking Service: Pre-create escrow account setup
 * - Identity Service: Track organizer activity
 */
@Externalized("event-events::EventCreated")
public record EventCreatedEvent(
        String eventId,
        String organizerId,
        String organizationId,
        String eventTitle,
        LocalDateTime eventDateTime,
        int totalCapacity,
        String createdBy,
        Instant occurredAt
) {
    public EventCreatedEvent(
            String eventId,
            String organizerId,
            String organizationId,
            String eventTitle,
            LocalDateTime eventDateTime,
            int totalCapacity,
            String createdBy
    ) {
        this(eventId, organizerId, organizationId, eventTitle, eventDateTime,
             totalCapacity, createdBy, Instant.now());
    }
}
