package com.pml.catalog.event.domain;


import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an event is submitted for approval.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Notify admins of pending approval
 * - Notification Service: Send submission confirmation to organizer
 */
/** Cross-service wire name (ET-PLT-003 §4): event-events::EventSubmitted — staged into the outbox. */
public record EventSubmittedEvent(
        String eventId,
        String organizerId,
        String organizationId,
        String eventTitle,
        LocalDateTime eventDateTime,
        int submissionCount,
        LocalDateTime approvalDeadline,
        Instant occurredAt
) {
    public EventSubmittedEvent(
            String eventId,
            String organizerId,
            String organizationId,
            String eventTitle,
            LocalDateTime eventDateTime,
            int submissionCount,
            LocalDateTime approvalDeadline
    ) {
        this(eventId, organizerId, organizationId, eventTitle, eventDateTime,
             submissionCount, approvalDeadline, Instant.now());
    }
}
