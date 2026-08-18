package com.pml.catalog.event.domain;


import java.time.Instant;

/**
 * Domain event published when an admin rejects an event.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Notify organizer of rejection with reason
 * - Notification Service: Send rejection notification
 */
/** Cross-service wire name (ET-PLT-003 §4): event-events::EventRejected — staged into the outbox. */
public record EventRejectedEvent(
        String eventId,
        String organizerId,
        String organizationId,
        String eventTitle,
        String rejectedBy,
        String rejectionReason,
        Instant occurredAt
) {
    public EventRejectedEvent(
            String eventId,
            String organizerId,
            String organizationId,
            String eventTitle,
            String rejectedBy,
            String rejectionReason
    ) {
        this(eventId, organizerId, organizationId, eventTitle, rejectedBy,
             rejectionReason, Instant.now());
    }
}
