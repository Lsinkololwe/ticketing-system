package com.pml.identity.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;

/**
 * Domain event published when a new user completes registration.
 *
 * Business Intent: Notify other services that a new user account has been created
 * so they can initialize user-specific resources (e.g., preferences, recommendations).
 *
 * External Listeners (via Azure Service Bus):
 * - Notification Service: Send welcome email/SMS
 * - Analytics: Track new user registrations
 */
@Externalized("user-events::UserRegistered")
public record UserRegisteredEvent(
        String userId,
        String email,
        String phoneNumber,
        String userType,
        Instant occurredAt
) {
    public UserRegisteredEvent(
            String userId,
            String email,
            String phoneNumber,
            String userType
    ) {
        this(userId, email, phoneNumber, userType, Instant.now());
    }
}
