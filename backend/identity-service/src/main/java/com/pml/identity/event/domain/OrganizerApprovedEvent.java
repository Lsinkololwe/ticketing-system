package com.pml.identity.event.domain;


import java.time.Instant;

/**
 * Domain event published when an organizer profile is approved by admin.
 *
 * Business Intent: Organizer can now create and publish events. Notify organizer
 * of their approved status and unlock event creation functionality.
 *
 * External Listeners (via Azure Service Bus):
 * - Notification Service: Send approval confirmation
 * - Catalog Service: Enable event creation for this organizer
 */
/** Cross-service wire name (ET-PLT-003 §4): user-events::OrganizerApproved — staged into the outbox. */
public record OrganizerApprovedEvent(
        String organizerId,
        String userId,
        String organizerName,
        String businessName,
        String approvedBy,
        Instant occurredAt
) {
    public OrganizerApprovedEvent(
            String organizerId,
            String userId,
            String organizerName,
            String businessName,
            String approvedBy
    ) {
        this(organizerId, userId, organizerName, businessName, approvedBy, Instant.now());
    }
}
