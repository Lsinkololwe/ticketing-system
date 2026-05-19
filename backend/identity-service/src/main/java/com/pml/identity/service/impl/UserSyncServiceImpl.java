package com.pml.identity.service.impl;

import com.pml.identity.dto.sync.KeycloakEventDto;
import com.pml.identity.dto.sync.SyncResponse;
import com.pml.identity.event.domain.UserRegisteredEvent;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implementation of UserSyncService.
 *
 * Handles synchronization of user data from Keycloak to MongoDB.
 * This service is called by:
 * - KeycloakSyncController (webhook from Keycloak EventListener)
 * - GraphQL mutations (manual sync operations)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;

    @Override
    public Mono<User> syncUserFromKeycloak(String keycloakUserId) {
        log.info("Syncing user from Keycloak: {}", keycloakUserId);

        return keycloakService.findUserById(keycloakUserId)
                .flatMap(optionalUser -> {
                    if (optionalUser.isEmpty()) {
                        log.warn("User not found in Keycloak: {}", keycloakUserId);
                        return Mono.empty();
                    }

                    UserRepresentation keycloakUser = optionalUser.get();
                    return syncKeycloakUserToMongo(keycloakUser);
                });
    }

    @Override
    public Mono<Void> syncAllUsersFromKeycloak() {
        log.info("Starting full user sync from Keycloak");

        return keycloakService.countUsers()
                .flatMap(totalUsers -> {
                    log.info("Found {} users in Keycloak to sync", totalUsers);

                    // Process in batches of 100
                    int batchSize = 100;
                    int totalBatches = (totalUsers + batchSize - 1) / batchSize;

                    return Mono.just(totalBatches)
                            .flatMapMany(batches -> {
                                // Create a stream of batch numbers
                                return reactor.core.publisher.Flux.range(0, batches);
                            })
                            .flatMap(batchNum -> {
                                int offset = batchNum * batchSize;
                                log.info("Processing batch {} of {} (offset: {})",
                                        batchNum + 1, totalBatches, offset);

                                return keycloakService.getAllUsers(offset, batchSize)
                                        .flatMap(this::syncKeycloakUserToMongo)
                                        .doOnError(e -> log.error("Error syncing user in batch {}: {}",
                                                batchNum + 1, e.getMessage()));
                            }, 4) // Process 4 batches concurrently
                            .then();
                })
                .doOnSuccess(v -> log.info("Full user sync completed"))
                .doOnError(e -> log.error("Full user sync failed: {}", e.getMessage()));
    }

    @Override
    public Mono<SyncResponse> handleKeycloakEvent(KeycloakEventDto event) {
        log.info("Handling Keycloak event: type={}, userId={}", event.getEventType(), event.getUserId());

        if (event.isDeleteEvent()) {
            return deleteUserByKeycloakId(event.getUserId())
                    .then(Mono.just(SyncResponse.success(event.getUserId(), "DELETED", "User deleted from MongoDB")))
                    .onErrorResume(e -> {
                        log.error("Failed to delete user {}: {}", event.getUserId(), e.getMessage());
                        return Mono.just(SyncResponse.error(event.getUserId(), e.getMessage()));
                    });
        }

        if (event.isLoginEvent()) {
            return updateLastLogin(event.getUserId())
                    .map(user -> SyncResponse.success(user.getId(), "LOGIN_UPDATED", "Last login updated"))
                    .onErrorResume(e -> {
                        log.error("Failed to update last login for {}: {}", event.getUserId(), e.getMessage());
                        return Mono.just(SyncResponse.error(event.getUserId(), e.getMessage()));
                    });
        }

        if (event.isSyncEvent()) {
            return syncUserFromKeycloak(event.getUserId())
                    .map(user -> SyncResponse.success(user.getId(), "SYNCED", "User synced from Keycloak"))
                    .switchIfEmpty(Mono.just(SyncResponse.skipped(event.getUserId(), "User not found in Keycloak")))
                    .onErrorResume(e -> {
                        log.error("Failed to sync user {}: {}", event.getUserId(), e.getMessage());
                        return Mono.just(SyncResponse.error(event.getUserId(), e.getMessage()));
                    });
        }

        // Unknown event type
        log.debug("Ignoring unhandled event type: {}", event.getEventType());
        return Mono.just(SyncResponse.skipped(event.getUserId(), "Event type not handled: " + event.getEventType()));
    }

    @Override
    public Mono<Void> deleteUserByKeycloakId(String keycloakUserId) {
        log.info("Deleting user from MongoDB: {}", keycloakUserId);

        // In our architecture, MongoDB document ID = Keycloak user ID
        return userRepository.findById(keycloakUserId)
                .flatMap(user -> {
                    log.info("Deleting user: {} (email: {})", user.getId(), user.getEmail());
                    return userRepository.delete(user);
                })
                .doOnSuccess(v -> log.info("User deleted from MongoDB: {}", keycloakUserId));
    }

    @Override
    public Mono<User> updateLastLogin(String keycloakUserId) {
        log.debug("Updating last login for user: {}", keycloakUserId);

        return userRepository.findById(keycloakUserId)
                .flatMap(user -> {
                    user.setLastLoginAt(Instant.now());
                    return userRepository.save(user);
                })
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.debug("Last login updated for user: {}", keycloakUserId);
                    }
                });
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Sync a Keycloak user representation to MongoDB.
     * Creates or updates the user document.
     */
    private Mono<User> syncKeycloakUserToMongo(UserRepresentation keycloakUser) {
        String keycloakUserId = keycloakUser.getId();

        return userRepository.findById(keycloakUserId)
                .flatMap(existingUser -> {
                    // Update existing user
                    log.debug("Updating existing user: {}", keycloakUserId);
                    updateUserFromKeycloak(existingUser, keycloakUser);
                    return userRepository.save(existingUser);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Create new user
                    log.info("Creating new user from Keycloak: {}", keycloakUserId);
                    User newUser = createUserFromKeycloak(keycloakUser);
                    return userRepository.save(newUser)
                            .doOnSuccess(user -> publishUserRegisteredEvent(user));
                }))
                .doOnSuccess(user -> log.debug("User synced successfully: {}", keycloakUserId));
    }

    /**
     * Create a new User entity from Keycloak UserRepresentation.
     */
    private User createUserFromKeycloak(UserRepresentation keycloakUser) {
        Map<String, List<String>> attributes = keycloakUser.getAttributes();

        User user = User.builder()
                .id(keycloakUser.getId())  // Use Keycloak ID as MongoDB ID
                .username(keycloakUser.getUsername())
                .email(keycloakUser.getEmail())
                .firstName(keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "")
                .lastName(keycloakUser.getLastName() != null ? keycloakUser.getLastName() : "")
                .emailVerified(keycloakUser.isEmailVerified())
                .active(keycloakUser.isEnabled())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Extract custom attributes
        // Note: Business fields (companyName, etc.) belong to OrganizerProfile, not User
        if (attributes != null) {
            extractAttribute(attributes, "phoneNumber").ifPresent(user::setPhoneNumber);
            extractAttribute(attributes, "phoneVerified")
                    .ifPresent(v -> user.setPhoneVerified(Boolean.parseBoolean(v)));
            extractAttribute(attributes, "userType")
                    .ifPresent(v -> user.setUserType(parseUserType(v)));
        }

        // Default user type if not set
        if (user.getUserType() == null) {
            user.setUserType(UserType.CUSTOMER);
        }

        return user;
    }

    /**
     * Update an existing User entity from Keycloak UserRepresentation.
     */
    private void updateUserFromKeycloak(User user, UserRepresentation keycloakUser) {
        Map<String, List<String>> attributes = keycloakUser.getAttributes();

        // Update basic fields
        user.setUsername(keycloakUser.getUsername());
        user.setEmail(keycloakUser.getEmail());
        user.setFirstName(keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : user.getFirstName());
        user.setLastName(keycloakUser.getLastName() != null ? keycloakUser.getLastName() : user.getLastName());
        user.setEmailVerified(keycloakUser.isEmailVerified());
        user.setActive(keycloakUser.isEnabled());
        user.setUpdatedAt(Instant.now());

        // Extract and update custom attributes
        // Note: Business fields (companyName, etc.) belong to OrganizerProfile, not User
        if (attributes != null) {
            extractAttribute(attributes, "phoneNumber").ifPresent(user::setPhoneNumber);
            extractAttribute(attributes, "phoneVerified")
                    .ifPresent(v -> user.setPhoneVerified(Boolean.parseBoolean(v)));
            extractAttribute(attributes, "userType")
                    .ifPresent(v -> user.setUserType(parseUserType(v)));
        }
    }

    /**
     * Extract a single-valued attribute from the attributes map.
     */
    private java.util.Optional<String> extractAttribute(Map<String, List<String>> attributes, String key) {
        if (attributes == null || !attributes.containsKey(key)) {
            return java.util.Optional.empty();
        }
        List<String> values = attributes.get(key);
        if (values == null || values.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(values.get(0));
    }

    /**
     * Parse user type from string, defaulting to CUSTOMER if invalid.
     */
    private UserType parseUserType(String value) {
        if (value == null || value.isEmpty()) {
            return UserType.CUSTOMER;
        }
        try {
            return UserType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown user type: {}, defaulting to CUSTOMER", value);
            return UserType.CUSTOMER;
        }
    }

    /**
     * Publish UserRegisteredEvent when a new user is synced.
     */
    private void publishUserRegisteredEvent(User user) {
        try {
            UserRegisteredEvent event = new UserRegisteredEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getPhoneNumber(),
                    user.getUserType() != null ? user.getUserType().name() : "CUSTOMER"
            );

            boolean sent = streamBridge.send("userOutput-out-0", event);
            if (sent) {
                log.info("Published UserRegisteredEvent for user: {}", user.getId());
            } else {
                log.warn("Failed to publish UserRegisteredEvent for user: {}", user.getId());
            }
        } catch (Exception e) {
            log.error("Error publishing UserRegisteredEvent for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
