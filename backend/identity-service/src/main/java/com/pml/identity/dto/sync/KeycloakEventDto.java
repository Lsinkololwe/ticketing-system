package com.pml.identity.dto.sync;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for Keycloak event data sent from the UserSyncEventListener.
 * Contains details about the event that occurred in Keycloak.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeycloakEventDto {

    /**
     * The Keycloak user ID affected by this event.
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * The type of event.
     * For user events: REGISTER, UPDATE_PROFILE, UPDATE_EMAIL, VERIFY_EMAIL, LOGIN, LOGOUT
     * For admin events: ADMIN_EVENT
     */
    @NotBlank(message = "Event type is required")
    private String eventType;

    /**
     * For admin events, the operation type: CREATE, UPDATE, DELETE
     */
    private String operationType;

    /**
     * For admin events, the resource type: USER, REALM_ROLE_MAPPING
     */
    private String resourceType;

    /**
     * For admin events, the resource path (e.g., "users/{userId}")
     */
    private String resourcePath;

    /**
     * Timestamp when the event occurred (epoch milliseconds).
     */
    private Long timestamp;

    /**
     * The realm ID where the event occurred.
     */
    private String realmId;

    /**
     * The client ID that triggered the event (if applicable).
     */
    private String clientId;

    /**
     * IP address of the request that triggered the event.
     */
    private String ipAddress;

    /**
     * Get timestamp as Instant.
     */
    public Instant getTimestampAsInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : Instant.now();
    }

    /**
     * Check if this is a user sync event (vs just a login notification).
     */
    public boolean isSyncEvent() {
        return "REGISTER".equals(eventType)
                || "UPDATE_PROFILE".equals(eventType)
                || "UPDATE_EMAIL".equals(eventType)
                || "VERIFY_EMAIL".equals(eventType)
                || "ADMIN_CREATE".equals(operationType)
                || "ADMIN_UPDATE".equals(operationType)
                || "CREATE".equals(operationType)
                || "UPDATE".equals(operationType);
    }

    /**
     * Check if this is a delete event.
     */
    public boolean isDeleteEvent() {
        return "DELETE".equals(eventType)
                || "ADMIN_DELETE".equals(operationType)
                || "DELETE".equals(operationType);
    }

    /**
     * Check if this is a login event.
     */
    public boolean isLoginEvent() {
        return "LOGIN".equals(eventType);
    }
}
