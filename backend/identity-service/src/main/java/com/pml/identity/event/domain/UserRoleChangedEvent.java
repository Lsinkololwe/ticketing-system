package com.pml.identity.event.domain;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event published when a user's roles are changed.
 *
 * Business Intent: Audit trail for role changes and notify services that need
 * to update user permissions/access based on role changes.
 *
 * External Listeners (via Azure Service Bus):
 * - Audit Service: Log role change for compliance
 * - Notification Service: Notify user of role change
 * - Other Services: Update cached user permissions
 *
 * OWASP Compliance:
 * - A09:2021 - Security Logging: All role changes are logged with who/what/when
 * - A01:2021 - Broken Access Control: Role changes require admin authority
 */
@Externalized("user-events::UserRoleChanged")
public record UserRoleChangedEvent(
        String userId,
        String email,
        Set<String> oldRoles,
        Set<String> newRoles,
        String action,        // ADD, REMOVE, SET
        String changedRole,   // The specific role that was changed (null for SET action)
        String changedBy,     // User ID of the person who made the change
        Instant occurredAt
) {
    /**
     * Constructor with automatic timestamp.
     */
    public UserRoleChangedEvent(
            String userId,
            String email,
            Set<String> oldRoles,
            Set<String> newRoles,
            String action,
            String changedRole,
            String changedBy
    ) {
        this(userId, email, oldRoles, newRoles, action, changedRole, changedBy, Instant.now());
    }

    /**
     * Get roles that were added.
     *
     * @return set of role names that were added
     */
    public Set<String> addedRoles() {
        if (oldRoles == null || newRoles == null) {
            return Set.of();
        }
        return newRoles.stream()
                .filter(role -> !oldRoles.contains(role))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get roles that were removed.
     *
     * @return set of role names that were removed
     */
    public Set<String> removedRoles() {
        if (oldRoles == null || newRoles == null) {
            return Set.of();
        }
        return oldRoles.stream()
                .filter(role -> !newRoles.contains(role))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Check if this was a role addition.
     *
     * @return true if action was ADD
     */
    public boolean isAddAction() {
        return "ADD".equals(action);
    }

    /**
     * Check if this was a role removal.
     *
     * @return true if action was REMOVE
     */
    public boolean isRemoveAction() {
        return "REMOVE".equals(action);
    }

    /**
     * Check if this was a full role set operation.
     *
     * @return true if action was SET
     */
    public boolean isSetAction() {
        return "SET".equals(action);
    }

    /**
     * Get a human-readable description of the change.
     *
     * @return description of what changed
     */
    public String getChangeDescription() {
        return switch (action) {
            case "ADD" -> String.format("Role %s added to user", changedRole);
            case "REMOVE" -> String.format("Role %s removed from user", changedRole);
            case "SET" -> String.format("Roles changed from %s to %s", oldRoles, newRoles);
            default -> String.format("Unknown role action: %s", action);
        };
    }
}
