package com.pml.catalog.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain event published when an event is submitted for approval.
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Notify admins of pending approval
 * - Notification Service: Send submission confirmation to organizer
 */
@Externalized("event-events::EventSubmitted")
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
