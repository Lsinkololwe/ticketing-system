package com.pml.identity.service;

import com.pml.identity.dto.sync.KeycloakEventDto;
import com.pml.identity.dto.sync.SyncResponse;
import com.pml.identity.domain.model.User;
import reactor.core.publisher.Mono;

/**
 * Service for synchronizing user data between Keycloak and MongoDB.
 *
 * Handles:
 * - Syncing user data from Keycloak to MongoDB
 * - Processing Keycloak events (login, profile updates, etc.)
 * - Bulk sync operations for recovery scenarios
 *
 * Architecture:
 * - Keycloak is the source of truth for authentication and user identity
 * - MongoDB stores business-specific user data that extends Keycloak
 * - This service ensures MongoDB stays in sync with Keycloak changes
 */
public interface UserSyncService {

    /**
     * Sync a single user from Keycloak to MongoDB.
     * Fetches user data from Keycloak Admin API and upserts into MongoDB.
     *
     * @param keycloakUserId The Keycloak user ID (sub claim)
     * @return The synced user or error if sync failed
     */
    Mono<User> syncUserFromKeycloak(String keycloakUserId);

    /**
     * Sync all users from Keycloak to MongoDB.
     * Used for initial setup or recovery scenarios.
     * This operation can take significant time for large user bases.
     *
     * @return Mono<Void> that completes when all users are synced
     */
    Mono<Void> syncAllUsersFromKeycloak();

    /**
     * Handle a Keycloak event.
     * Processes the event and takes appropriate action (sync, update login, delete, etc.)
     *
     * @param event The Keycloak event data
     * @return SyncResponse indicating the result
     */
    Mono<SyncResponse> handleKeycloakEvent(KeycloakEventDto event);

    /**
     * Delete a user from MongoDB by Keycloak user ID.
     * Called when a user is deleted in Keycloak.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return Mono<Void> that completes when deletion is done
     */
    Mono<Void> deleteUserByKeycloakId(String keycloakUserId);

    /**
     * Update the last login timestamp for a user.
     *
     * @param keycloakUserId The Keycloak user ID
     * @return The updated user
     */
    Mono<User> updateLastLogin(String keycloakUserId);
}
