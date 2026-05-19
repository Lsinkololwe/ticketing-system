package com.pml.identity.dto.sync;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for syncing a user from Keycloak to MongoDB.
 * Sent by the Keycloak UserSyncEventListener when user data changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncUserRequest {

    /**
     * The Keycloak user ID (sub claim from JWT).
     * This is used to fetch the user from Keycloak Admin API.
     */
    @NotBlank(message = "Keycloak user ID is required")
    private String keycloakUserId;

    /**
     * The type of event that triggered the sync.
     * Examples: REGISTER, UPDATE_PROFILE, ADMIN_CREATE, ADMIN_UPDATE, VERIFY_EMAIL
     */
    @NotBlank(message = "Event type is required")
    private String eventType;

    /**
     * Whether to force a full sync even if the user already exists.
     * If false, only missing users will be created.
     */
    @Builder.Default
    private boolean forceUpdate = true;
}
