package com.pml.identity.service.impl;

import com.pml.identity.event.domain.UserRegisteredEvent;
import com.pml.identity.event.domain.UserRoleChangedEvent;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.UserService;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service Implementation
 *
 * ARCHITECTURE NOTE (Phase 1 Migration):
 * ======================================
 * This service manages user profile data stored in MongoDB.
 * Keycloak is the SINGLE SOURCE OF TRUTH for:
 * - Authentication credentials (password)
 * - Account status (enabled, locked)
 * - Brute force protection (failed login attempts, lockout)
 *
 * REMOVED methods (now handled by Keycloak):
 * - changePassword() → Use KeycloakService.updatePassword()
 * - incrementFailedLoginAttempts() → Keycloak brute force protection
 * - resetFailedLoginAttempts() → Keycloak handles automatically
 * - lockAccount() → Keycloak handles via brute force protection
 * - unlockAccount() → Use Keycloak Admin Console or Admin API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final StreamBridge streamBridge;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<User> findById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public Mono<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Mono<User> findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public Flux<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Flux<User> findByRole(UserType role) {
        return userRepository.findByRole(role);
    }

    @Override
    public Flux<User> findActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<User> createUser(User user) {
        log.info("Creating new user profile with email: {}", user.getEmail());
        // NOTE: Password is NOT stored in MongoDB - Keycloak is the source of truth
        // The user should already be created in Keycloak before calling this method
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user)
                .doOnSuccess(u -> {
                    log.info("User profile created successfully: {}", u.getId());
                    publishUserRegisteredEvent(u);
                });
    }

    /**
     * Publish UserRegisteredEvent to Azure Service Bus.
     * Used to notify other services of new user registrations.
     *
     * <p>Multi-role support: Publishes all user roles in the event.</p>
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
            // Don't fail user creation if event publishing fails
            log.error("Error publishing UserRegisteredEvent for user {}: {}", user.getId(), e.getMessage());
        }
    }

    @Override
    public Mono<User> updateUser(String id, User user) {
        return userRepository.findById(id)
                .flatMap(existingUser -> {
                    existingUser.setFirstName(user.getFirstName());
                    existingUser.setLastName(user.getLastName());
                    existingUser.setPhoneNumber(user.getPhoneNumber());
                    // Update roles if provided (multi-role support)
                    if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                        existingUser.setRoles(user.getRoles());
                    }
                    existingUser.setUpdatedAt(Instant.now());
                    return userRepository.save(existingUser)
                            .flatMap(savedUser ->
                                // Sync changes to Keycloak
                                keycloakService.updateUser(savedUser)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync user update to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                });
    }

    @Override
    public Mono<User> updateProfile(String id, User profileData) {
        return userRepository.findById(id)
                .flatMap(existingUser -> {
                    if (profileData.getFirstName() != null) {
                        existingUser.setFirstName(profileData.getFirstName());
                    }
                    if (profileData.getLastName() != null) {
                        existingUser.setLastName(profileData.getLastName());
                    }
                    if (profileData.getPhoneNumber() != null) {
                        existingUser.setPhoneNumber(profileData.getPhoneNumber());
                    }
                    existingUser.setUpdatedAt(Instant.now());
                    return userRepository.save(existingUser)
                            .flatMap(savedUser ->
                                // Sync changes to Keycloak
                                keycloakService.updateUser(savedUser)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync profile update to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                });
    }

    @Override
    public Mono<Void> deleteUser(String id) {
        return userRepository.findById(id)
                .flatMap(user ->
                    // Delete from Keycloak first
                    keycloakService.deleteUser(user.getEmail())
                            .onErrorResume(e -> {
                                log.warn("Failed to delete user from Keycloak: {}", e.getMessage());
                                return Mono.empty();
                            })
                            // Then delete from MongoDB
                            .then(userRepository.deleteById(id))
                );
    }

    @Override
    public Mono<Void> deactivateUser(String id) {
        return userRepository.findById(id)
                .flatMap(user -> {
                    user.setActive(false);
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Disable in Keycloak as well
                                keycloakService.setUserEnabled(savedUser.getEmail(), false)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to disable user in Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                })
                .then();
    }

    @Override
    public Mono<Void> activateUser(String id) {
        return userRepository.findById(id)
                .flatMap(user -> {
                    user.setActive(true);
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Enable in Keycloak as well
                                keycloakService.setUserEnabled(savedUser.getEmail(), true)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to enable user in Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                })
                .then();
    }

    // ========================================================================
    // VERIFICATION OPERATIONS
    // ========================================================================

    @Override
    public Mono<User> verifyEmail(String userId) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setEmailVerified(true);
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Sync emailVerified status to Keycloak
                                keycloakService.updateUser(savedUser)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync email verification to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                });
    }

    @Override
    @Deprecated
    public Mono<User> verifyPhone(String userId, String otpCode) {
        // DEPRECATED: OTP verification should be done via OtpService/Redis
        // This method is kept for backward compatibility
        log.warn("Using deprecated verifyPhone with OTP code. Use OtpService instead.");
        return verifyPhone(userId);
    }

    @Override
    public Mono<User> verifyPhone(String userId) {
        // Mark phone as verified (OTP already validated via OtpService/Redis)
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setPhoneVerified(true);
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Sync phoneVerified status to Keycloak
                                keycloakService.updatePhoneVerified(savedUser.getId(), true)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync phone verification to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            );
                });
    }

    @Override
    public Mono<Void> sendEmailVerification(String userId) {
        log.info("Sending email verification for user: {}", userId);
        // Use Keycloak to send verification email
        return keycloakService.sendVerificationEmail(userId)
                .onErrorResume(e -> {
                    log.error("Failed to send verification email: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> sendPhoneVerification(String userId) {
        // Phone verification is handled by OtpService + MessagingService
        // This method is a placeholder - actual implementation uses OtpService
        log.info("Phone verification for user {} should use OtpService", userId);
        return Mono.empty();
    }

    // ========================================================================
    // ACTIVITY TRACKING
    // ========================================================================

    @Override
    public Mono<User> updateLastLogin(String userId) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setLastLoginAt(Instant.now());
                    return userRepository.save(user);
                });
    }

    // ========================================================================
    // ROLE MANAGEMENT OPERATIONS
    // ========================================================================

    @Override
    public Mono<User> addRole(String userId, UserType role, String addedBy) {
        log.info("Adding role {} to user {} by {}", role, userId, addedBy);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Validate role can be added
                    if (!UserType.canAddRole(user.getRoles(), role)) {
                        return Mono.error(new IllegalArgumentException(
                                "Cannot add role " + role + " to user. Invalid role combination."));
                    }

                    Set<UserType> oldRoles = EnumSet.copyOf(user.getRoles());
                    user.addRole(role);
                    user.setUpdatedAt(Instant.now());
                    user.setUpdatedBy(addedBy);

                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Sync role to Keycloak
                                keycloakService.addRoleToUser(savedUser.getId(), role.name())
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync role addition to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            )
                            .doOnSuccess(savedUser -> {
                                log.info("Role {} added to user {} by {}", role, userId, addedBy);
                                publishUserRoleChangedEvent(savedUser, oldRoles, "ADD", role, addedBy);
                            });
                });
    }

    @Override
    public Mono<User> removeRole(String userId, UserType role, String removedBy) {
        log.info("Removing role {} from user {} by {}", role, userId, removedBy);

        // Cannot remove CUSTOMER role
        if (role == UserType.CUSTOMER) {
            return Mono.error(new IllegalStateException("Cannot remove CUSTOMER role. It is the base role for all users."));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Validate role can be removed
                    if (!UserType.canRemoveRole(user.getRoles(), role)) {
                        return Mono.error(new IllegalArgumentException(
                                "Cannot remove role " + role + " from user. User does not have this role or it would result in an invalid state."));
                    }

                    Set<UserType> oldRoles = EnumSet.copyOf(user.getRoles());
                    user.removeRole(role);
                    user.setUpdatedAt(Instant.now());
                    user.setUpdatedBy(removedBy);

                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Sync role removal to Keycloak
                                keycloakService.removeRoleFromUser(savedUser.getId(), role.name())
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync role removal to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            )
                            .doOnSuccess(savedUser -> {
                                log.info("Role {} removed from user {} by {}", role, userId, removedBy);
                                publishUserRoleChangedEvent(savedUser, oldRoles, "REMOVE", role, removedBy);
                            });
                });
    }

    @Override
    public Mono<User> setRoles(String userId, Set<UserType> roles, String updatedBy) {
        log.info("Setting roles {} for user {} by {}", roles, userId, updatedBy);

        // Validate the new role set
        if (!UserType.isValidRoleCombination(roles)) {
            return Mono.error(new IllegalArgumentException(
                    "Invalid role combination. Roles must include CUSTOMER and follow business rules."));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    Set<UserType> oldRoles = EnumSet.copyOf(user.getRoles());

                    // Determine roles to add and remove
                    Set<UserType> rolesToAdd = EnumSet.copyOf(roles);
                    rolesToAdd.removeAll(oldRoles);

                    Set<UserType> rolesToRemove = EnumSet.copyOf(oldRoles);
                    rolesToRemove.removeAll(roles);

                    // Update user's roles
                    user.getRoles().clear();
                    user.getRoles().addAll(roles);
                    user.setUpdatedAt(Instant.now());
                    user.setUpdatedBy(updatedBy);

                    return userRepository.save(user)
                            .flatMap(savedUser ->
                                // Sync roles to Keycloak
                                syncRolesToKeycloak(savedUser.getId(), rolesToAdd, rolesToRemove)
                                        .thenReturn(savedUser)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to sync roles to Keycloak: {}", e.getMessage());
                                            return Mono.just(savedUser);
                                        })
                            )
                            .doOnSuccess(savedUser -> {
                                log.info("Roles set to {} for user {} by {}", roles, userId, updatedBy);
                                publishUserRoleChangedEvent(savedUser, oldRoles, "SET", null, updatedBy);
                            });
                });
    }

    @Override
    public Mono<Set<UserType>> getRoles(String userId) {
        return userRepository.findById(userId)
                .map(User::getRoles)
                .defaultIfEmpty(EnumSet.of(UserType.CUSTOMER));
    }

    /**
     * Sync role changes to Keycloak.
     */
    private Mono<Void> syncRolesToKeycloak(String userId, Set<UserType> rolesToAdd, Set<UserType> rolesToRemove) {
        // Add new roles
        Mono<Void> addRoles = Flux.fromIterable(rolesToAdd)
                .flatMap(role -> keycloakService.addRoleToUser(userId, role.name()))
                .then();

        // Remove old roles
        Mono<Void> removeRoles = Flux.fromIterable(rolesToRemove)
                .flatMap(role -> keycloakService.removeRoleFromUser(userId, role.name()))
                .then();

        return Mono.when(addRoles, removeRoles);
    }

    /**
     * Publish UserRoleChangedEvent for audit logging.
     */
    private void publishUserRoleChangedEvent(User user, Set<UserType> oldRoles, String action, UserType changedRole, String changedBy) {
        try {
            Set<String> oldRoleNames = oldRoles.stream().map(Enum::name).collect(Collectors.toSet());
            Set<String> newRoleNames = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());

            UserRoleChangedEvent event = new UserRoleChangedEvent(
                    user.getId(),
                    user.getEmail(),
                    oldRoleNames,
                    newRoleNames,
                    action,
                    changedRole != null ? changedRole.name() : null,
                    changedBy,
                    Instant.now()
            );

            boolean sent = streamBridge.send("userRoleOutput-out-0", event);
            if (sent) {
                log.info("Published UserRoleChangedEvent for user: {} action: {}", user.getId(), action);
            } else {
                log.warn("Failed to publish UserRoleChangedEvent for user: {}", user.getId());
            }
        } catch (Exception e) {
            // Don't fail role update if event publishing fails
            log.error("Error publishing UserRoleChangedEvent for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
