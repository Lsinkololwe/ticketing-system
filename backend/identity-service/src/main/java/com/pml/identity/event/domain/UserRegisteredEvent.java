package com.pml.identity.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event published when a new user completes registration.
 *
 * Business Intent: Notify other services that a new user account has been created
 * so they can initialize user-specific resources (e.g., preferences, recommendations).
 *
 * External Listeners (via Azure Service Bus):
 * - Notification Service: Send welcome email/SMS
 * - Analytics: Track new user registrations
 *
 * <h2>Multi-Role Support</h2>
 * <p>The {@code roles} field contains all user roles. Users always have at least
 * the CUSTOMER role.</p>
 */
@Externalized("user-events::UserRegistered")
public record UserRegisteredEvent(
        String userId,
        String email,
        String phoneNumber,
        Set<String> roles,
        Instant occurredAt
) {
    /**
     * Constructor with roles and auto-generated timestamp.
     */
    public UserRegisteredEvent(
            String userId,
            String email,
            String phoneNumber,
            Set<String> roles
    ) {
        this(userId, email, phoneNumber, roles != null ? roles : Set.of("CUSTOMER"), Instant.now());
    }

    /**
     * Check if user has a specific role.
     *
     * @param role the role to check
     * @return true if user has the role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
