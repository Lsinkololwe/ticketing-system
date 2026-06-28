package com.pml.identity.service.impl;

import com.pml.identity.dto.sync.KeycloakEventDto;
import com.pml.identity.dto.sync.KeycloakUserDataDto;
import com.pml.identity.dto.sync.SyncResponse;
import com.pml.identity.event.domain.UserRegisteredEvent;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.constants.UserType;
import com.pml.shared.util.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of UserSyncService.
 *
 * Handles synchronization of user data from Keycloak to MongoDB.
 * This service is called by:
 * - KeycloakSyncController (webhook from Keycloak EventListener)
 * - GraphQL mutations (manual sync operations)
 *
 * PROGRESSIVE ONBOARDING (Industry Standard - Eventbrite/Stripe Connect model):
 * - User registration creates ONLY the User entity
 * - Organization is created LAZILY when user attempts to create their first event
 * - KYB verification is required to PUBLISH events and receive payouts
 *
 * @see com.pml.identity.service.OrganizationOnboardingService For lazy organization creation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;
    private final ReactiveMongoTemplate mongoTemplate;

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
    public Mono<User> syncUserFromData(KeycloakUserDataDto userData) {
        log.info("Syncing user from data: {} (event: {})", userData.getId(), userData.getEventType());

        return userRepository.findById(userData.getId())
                .flatMap(existingUser -> {
                    // Update existing user
                    log.debug("Updating existing user from data: {}", userData.getId());
                    updateUserFromData(existingUser, userData);
                    // Better Auth may have created the row first; if this is a
                    // REGISTER/ADMIN_CREATE landing on the update branch, still
                    // publish (idempotently) so onboarding is never skipped.
                    return userRepository.save(existingUser)
                            .flatMap(saved -> publishRegistrationIfNeeded(saved, userData.isRegistrationEvent()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Create new user
                    // Note: Organization is NOT created here - uses lazy creation on first event
                    log.info("Creating new user from data: {}", userData.getId());
                    User newUser = createUserFromData(userData);
                    return userRepository.save(newUser)
                            .flatMap(saved -> publishRegistrationIfNeeded(saved, true));
                }))
                .doOnSuccess(user -> log.info("User synced successfully from data: {}", userData.getId()));
    }

    /**
     * Create a new User entity from Keycloak user data DTO.
     */
    private User createUserFromData(KeycloakUserDataDto data) {
        Set<UserType> roles = extractRolesFromData(data);
        String e164 = PhoneNumbers.toE164(data.getPhoneNumber());

        return User.builder()
                .id(data.getId())
                .username(data.getUsername())
                .email(data.getEmail())
                .firstName(data.getFirstName() != null ? data.getFirstName() : "")
                .lastName(data.getLastName() != null ? data.getLastName() : "")
                .phoneNumber(e164)
                .phoneCountry(e164 != null ? PhoneNumbers.regionFor(e164) : null)
                .phoneVerified(data.isPhoneVerified())
                .emailVerified(data.isEmailVerified())
                .active(data.isEnabled())
                .roles(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Update an existing User entity from Keycloak user data DTO.
     *
     * <p>SHARED COLLECTION OWNERSHIP: the {@code users} collection is shared with Better
     * Auth. Better Auth owns its core fields ({@code email}, {@code name},
     * {@code emailVerified}, {@code image}, {@code createdAt}) and re-asserts them on every
     * login. This sync therefore updates ONLY the business fields it owns
     * (username, firstName, lastName, phoneNumber/phoneVerified, roles, active) and must
     * NOT touch the Better-Auth-owned fields, otherwise the two writers would fight.</p>
     */
    private void updateUserFromData(User user, KeycloakUserDataDto data) {
        user.setUsername(data.getUsername());
        user.setFirstName(data.getFirstName() != null ? data.getFirstName() : user.getFirstName());
        user.setLastName(data.getLastName() != null ? data.getLastName() : user.getLastName());
        // NOTE: email + emailVerified are Better-Auth-owned and intentionally not set here.
        user.setActive(data.isEnabled());
        user.setUpdatedAt(Instant.now());

        if (data.getPhoneNumber() != null) {
            applyNormalizedPhone(user, data.getPhoneNumber());
            user.setPhoneVerified(data.isPhoneVerified());
        }

        // Extract and update roles
        Set<UserType> roles = extractRolesFromData(data);
        if (!roles.isEmpty()) {
            user.getRoles().clear();
            user.getRoles().addAll(roles);
        }
    }

    /**
     * Extract roles from Keycloak user data DTO.
     */
    private Set<UserType> extractRolesFromData(KeycloakUserDataDto data) {
        Set<UserType> roles = EnumSet.of(UserType.CUSTOMER); // Always include CUSTOMER

        // Add roles from the roles set
        if (data.getRoles() != null) {
            for (String roleName : data.getRoles()) {
                UserType role = parseUserType(roleName);
                if (role != null) {
                    roles.add(role);
                }
            }
        }

        // Add roles from accountTypes (from registration form)
        if (data.getAccountTypes() != null) {
            for (String accountType : data.getAccountTypes()) {
                UserType role = parseUserType(accountType);
                if (role != null) {
                    roles.add(role);
                }
            }
        }

        log.debug("Extracted roles from data for user {}: {}", data.getId(), roles);
        return roles;
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
     *
     * <p>PROGRESSIVE ONBOARDING: Organization is NOT created during user sync.
     * Organization is created lazily when the user attempts to create their first event.
     * This follows industry standard patterns (Eventbrite, Stripe Connect).</p>
     *
     * @see com.pml.identity.service.OrganizationOnboardingService#getOrCreateOrganization
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
                    // Note: Organization is NOT created here - uses lazy creation on first event
                    log.info("Creating new user from Keycloak: {}", keycloakUserId);
                    User newUser = createUserFromKeycloak(keycloakUser);
                    return userRepository.save(newUser)
                            .flatMap(saved -> publishRegistrationIfNeeded(saved, true));
                }))
                .doOnSuccess(user -> log.debug("User synced successfully: {}", keycloakUserId));
    }

    /**
     * Create a new User entity from Keycloak UserRepresentation.
     */
    private User createUserFromKeycloak(UserRepresentation keycloakUser) {
        Map<String, List<String>> attributes = keycloakUser.getAttributes();

        // Extract roles from Keycloak
        Set<UserType> roles = extractRolesFromKeycloak(keycloakUser, attributes);

        User user = User.builder()
                .id(keycloakUser.getId())  // Use Keycloak ID as MongoDB ID
                .username(keycloakUser.getUsername())
                .email(keycloakUser.getEmail())
                .firstName(keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "")
                .lastName(keycloakUser.getLastName() != null ? keycloakUser.getLastName() : "")
                .emailVerified(keycloakUser.isEmailVerified())
                .active(keycloakUser.isEnabled())
                .roles(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Extract custom attributes
        // Note: Business fields (companyName, etc.) belong to Organization, not User
        if (attributes != null) {
            extractAttribute(attributes, "phoneNumber")
                    .ifPresent(p -> applyNormalizedPhone(user, p));
            extractAttribute(attributes, "phoneVerified")
                    .ifPresent(v -> user.setPhoneVerified(Boolean.parseBoolean(v)));
        }

        return user;
    }

    /**
     * Extract roles from Keycloak user representation.
     * Checks realm roles, 'roles' attribute, and 'accountType' attribute.
     *
     * <p>Priority order:</p>
     * <ol>
     *   <li>Keycloak realm roles (most authoritative)</li>
     *   <li>'roles' attribute (set by AccountTypeRoleMapper)</li>
     *   <li>'accountType' attribute (from registration form)</li>
     * </ol>
     */
    private Set<UserType> extractRolesFromKeycloak(UserRepresentation keycloakUser, Map<String, List<String>> attributes) {
        Set<UserType> roles = EnumSet.of(UserType.CUSTOMER); // Always include CUSTOMER

        // 1. Try to get roles from Keycloak realm roles (most authoritative)
        if (keycloakUser.getRealmRoles() != null) {
            for (String roleName : keycloakUser.getRealmRoles()) {
                UserType role = parseUserType(roleName);
                if (role != null && role != UserType.CUSTOMER) {
                    roles.add(role);
                    log.debug("Added role from realmRoles: {}", role);
                }
            }
        }

        // 2. Check attributes for roles
        if (attributes != null) {
            // Check for 'roles' attribute (set by AccountTypeRoleMapper)
            if (attributes.containsKey("roles")) {
                List<String> roleValues = attributes.get("roles");
                for (String roleValue : roleValues) {
                    // Handle comma-separated values
                    for (String roleName : roleValue.split(",")) {
                        UserType role = parseUserType(roleName.trim());
                        if (role != null && role != UserType.CUSTOMER) {
                            roles.add(role);
                            log.debug("Added role from 'roles' attribute: {}", role);
                        }
                    }
                }
            }

            // 3. Check for 'accountType' attribute (from registration form)
            // This is the primary source during registration
            if (attributes.containsKey("accountType")) {
                List<String> accountTypes = attributes.get("accountType");
                for (String accountType : accountTypes) {
                    // Handle comma-separated values (just in case)
                    for (String typeName : accountType.split(",")) {
                        UserType role = parseUserType(typeName.trim());
                        if (role != null) {
                            roles.add(role);
                            log.debug("Added role from 'accountType' attribute: {}", role);
                        }
                    }
                }
            }
        }

        log.info("Extracted roles for user {}: {}", keycloakUser.getUsername(), roles);
        return roles;
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

        // Extract and update roles from Keycloak
        Set<UserType> keycloakRoles = extractRolesFromKeycloak(keycloakUser, attributes);
        if (!keycloakRoles.isEmpty()) {
            // Merge Keycloak roles with existing roles (Keycloak is authoritative for realm roles)
            user.getRoles().clear();
            user.getRoles().addAll(keycloakRoles);
        }

        // Extract and update custom attributes
        // Note: Business fields (companyName, etc.) belong to Organization, not User
        if (attributes != null) {
            extractAttribute(attributes, "phoneNumber")
                    .ifPresent(p -> applyNormalizedPhone(user, p));
            extractAttribute(attributes, "phoneVerified")
                    .ifPresent(v -> user.setPhoneVerified(Boolean.parseBoolean(v)));
        }
    }

    /**
     * Normalize a raw phone number to E.164 and set it (with derived ISO country)
     * on the user. A value that cannot be made valid E.164 is stored as {@code null}
     * rather than persisting a malformed number that would fail the {@code users}
     * validator. This is the single phone-write funnel for the Keycloak→Mongo sync.
     */
    private void applyNormalizedPhone(User user, String rawPhone) {
        String e164 = PhoneNumbers.toE164(rawPhone);
        user.setPhoneNumber(e164);
        user.setPhoneCountry(e164 != null ? PhoneNumbers.regionFor(e164) : null);
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
     * Idempotently publish {@link UserRegisteredEvent} for a registration.
     *
     * <p>The shared {@code users} document can be created by EITHER Better Auth
     * (web OIDC sign-in) OR this sync (mobile/OTP, admin), so the event must not
     * be tied to "who created the row". It is published exactly once, guarded by
     * an atomic compare-and-set on {@code registrationEventPublished}: only the
     * writer that flips the flag false→true publishes, so concurrent create/update
     * paths and retries can never double-publish.</p>
     *
     * @param user                the synced user
     * @param registrationTrigger whether this sync represents a registration
     *                            (a fresh create, or a REGISTER/ADMIN_CREATE event)
     */
    private Mono<User> publishRegistrationIfNeeded(User user, boolean registrationTrigger) {
        if (!registrationTrigger) {
            return Mono.just(user);
        }
        Query claim = Query.query(Criteria.where("_id").is(user.getId())
                .and("registrationEventPublished").ne(true));
        Update markPublished = new Update().set("registrationEventPublished", true);
        return mongoTemplate.findAndModify(claim, markPublished,
                        FindAndModifyOptions.options().returnNew(true), User.class)
                .doOnNext(this::publishUserRegisteredEvent) // emits only if the claim won
                .thenReturn(user);
    }

    /**
     * Publish UserRegisteredEvent when a new user is synced.
     */
    private void publishUserRegisteredEvent(User user) {
        try {
            // Convert EnumSet<UserType> to Set<String> for the event
            Set<String> roleNames = user.getRoles() != null && !user.getRoles().isEmpty()
                    ? user.getRoles().stream().map(Enum::name).collect(Collectors.toSet())
                    : Set.of("CUSTOMER");

            UserRegisteredEvent event = new UserRegisteredEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getPhoneNumber(),
                    roleNames
            );

            boolean sent = streamBridge.send("userOutput-out-0", event);
            if (sent) {
                log.info("Published UserRegisteredEvent for user: {} with roles: {}", user.getId(), roleNames);
            } else {
                log.warn("Failed to publish UserRegisteredEvent for user: {}", user.getId());
            }
        } catch (Exception e) {
            log.error("Error publishing UserRegisteredEvent for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
