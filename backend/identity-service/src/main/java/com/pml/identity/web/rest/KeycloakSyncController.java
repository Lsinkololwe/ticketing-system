package com.pml.identity.web.rest;

import com.pml.identity.dto.sync.KeycloakEventDto;
import com.pml.identity.dto.sync.SyncResponse;
import com.pml.identity.dto.sync.SyncUserRequest;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.UserSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST Controller for Keycloak synchronization webhooks.
 *
 * Endpoints are called by the Keycloak UserSyncEventListener to notify the
 * Identity Service of user changes in Keycloak.
 *
 * Security:
 * - All endpoints require internal service authentication
 * - Uses OAuth2 client credentials flow from Keycloak EventListener
 * - Full sync endpoint additionally requires ADMIN role
 *
 * These endpoints enable Keycloak → MongoDB synchronization, ensuring that
 * changes made in Keycloak (via Admin Console, custom authenticators, or
 * user self-service) are reflected in MongoDB.
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/keycloak/sync")
@RequiredArgsConstructor
public class KeycloakSyncController {

    private final UserSyncService userSyncService;

    /**
     * Sync a single user from Keycloak to MongoDB.
     *
     * Called by UserSyncEventListener when user data changes in Keycloak:
     * - User registration
     * - Profile updates
     * - Email/phone verification
     * - Admin creates/updates user
     *
     * @param request The sync request containing Keycloak user ID
     * @return SyncResponse with the result
     */
    @PostMapping("/user")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<SyncResponse>> syncUser(@Valid @RequestBody SyncUserRequest request) {
        log.info("Received sync request for user: {} (event: {})",
                request.getKeycloakUserId(), request.getEventType());

        return userSyncService.syncUserFromKeycloak(request.getKeycloakUserId())
                .map(user -> {
                    SyncResponse response = SyncResponse.success(
                            user.getId(),
                            "SYNCED",
                            "User synced successfully from Keycloak"
                    );
                    return ResponseEntity.ok(response);
                })
                .switchIfEmpty(Mono.just(
                        ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(SyncResponse.error(
                                        request.getKeycloakUserId(),
                                        "User not found in Keycloak"
                                ))
                ))
                .onErrorResume(e -> {
                    log.error("Failed to sync user {}: {}", request.getKeycloakUserId(), e.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(SyncResponse.error(
                                            request.getKeycloakUserId(),
                                            "Sync failed: " + e.getMessage()
                                    ))
                    );
                });
    }

    /**
     * Handle a Keycloak event notification.
     *
     * Generic endpoint for processing various Keycloak events.
     * The event type determines what action is taken:
     * - Sync events: Fetch user from Keycloak and update MongoDB
     * - Login events: Update lastLoginAt timestamp
     * - Delete events: Remove user from MongoDB
     *
     * @param event The Keycloak event data
     * @return SyncResponse with the result
     */
    @PostMapping("/event")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<SyncResponse>> handleEvent(@Valid @RequestBody KeycloakEventDto event) {
        log.info("Received Keycloak event: type={}, userId={}",
                event.getEventType(), event.getUserId());

        return userSyncService.handleKeycloakEvent(event)
                .map(response -> {
                    if (response.isSuccess()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to handle event for user {}: {}", event.getUserId(), e.getMessage());
                    return Mono.just(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(SyncResponse.error(
                                            event.getUserId(),
                                            "Event handling failed: " + e.getMessage()
                                    ))
                    );
                });
    }

    /**
     * Sync all users from Keycloak to MongoDB.
     *
     * Full synchronization operation for recovery scenarios:
     * - Initial setup
     * - Data recovery after MongoDB restore
     * - Resolving sync drift
     *
     * Warning: This operation can be slow for large user bases.
     * Consider running during maintenance windows.
     *
     * @return 202 Accepted with message (async operation)
     */
    @PostMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public Mono<ResponseEntity<SyncResponse>> syncAllUsers() {
        log.info("Received request to sync all users from Keycloak");

        // Start async sync and return immediately
        userSyncService.syncAllUsersFromKeycloak()
                .subscribe(
                        v -> log.info("Full sync completed successfully"),
                        e -> log.error("Full sync failed: {}", e.getMessage())
                );

        return Mono.just(ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(SyncResponse.builder()
                        .success(true)
                        .action("STARTED")
                        .message("Full sync started. Check logs for progress.")
                        .build()
                ));
    }

    /**
     * Health check endpoint for the sync service.
     *
     * Can be used by monitoring to verify the sync endpoint is available.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Keycloak sync service is healthy"));
    }

    /**
     * Get sync status for a specific user.
     *
     * Returns whether the user exists in MongoDB and when they were last synced.
     *
     * @param userId The Keycloak user ID
     * @return User details if found
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<ResponseEntity<User>> getUserSyncStatus(@PathVariable String userId) {
        log.debug("Checking sync status for user: {}", userId);

        return userSyncService.syncUserFromKeycloak(userId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
