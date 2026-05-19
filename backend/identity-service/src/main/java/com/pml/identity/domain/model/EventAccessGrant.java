package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.valueobject.EventRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Event Access Grant Model
 *
 * Grants a user access to a specific event with a role.
 * Event-level roles OVERRIDE organization-level permissions for that event.
 *
 * USE CASES:
 * =========
 * 1. External scanner staff for a single event
 * 2. Guest editor for event content
 * 3. Temporary admin access for event management
 * 4. Time-limited access that expires after the event
 *
 * PERMISSION RESOLUTION:
 * =====================
 * When checking permissions for an event:
 * 1. Check if user has EventAccessGrant for this event
 * 2. If yes, use event role permissions (overrides org role)
 * 3. If no, fall back to organization role permissions
 */
@Document(collection = "event_access_grants")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "user_event_idx", def = "{'userId': 1, 'eventId': 1}", unique = true),
    @CompoundIndex(name = "event_status_idx", def = "{'eventId': 1, 'status': 1}")
})
public class EventAccessGrant {

    @Id
    private String id;

    /**
     * User ID who has access
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * Event ID (references Event in Catalog Service)
     */
    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    /**
     * Organization ID (for scoping queries)
     */
    @NotBlank(message = "Organization ID is required")
    @Indexed
    private String organizationId;

    /**
     * Role for this specific event
     */
    @NotNull(message = "Event role is required")
    private EventRole eventRole;

    /**
     * Custom permissions for this event
     */
    @Builder.Default
    private Set<String> customPermissions = new HashSet<>();

    /**
     * User ID of who granted this access
     */
    @NotBlank(message = "Granter is required")
    private String grantedById;

    /**
     * Reason for granting access
     */
    private String reason;

    /**
     * Access status
     */
    @Builder.Default
    private AccessGrantStatus status = AccessGrantStatus.ACTIVE;

    /**
     * When access expires (null = never expires)
     */
    private Instant expiresAt;

    /**
     * Who revoked the access (if revoked)
     */
    private String revokedById;

    /**
     * When access was revoked
     */
    private Instant revokedAt;

    /**
     * Reason for revocation
     */
    private String revocationReason;

    /**
     * When access was granted
     */
    private Instant grantedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Check if access is currently valid
     */
    public boolean isValid() {
        if (status != AccessGrantStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return false;
        }
        return true;
    }

    /**
     * Check if access has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Check if user has a specific permission for this event
     */
    public boolean hasPermission(String permission) {
        // Custom permissions take precedence
        if (customPermissions.contains(permission)) {
            return true;
        }
        // Fall back to role-based check
        return false; // Actual implementation would check role permissions
    }
}
