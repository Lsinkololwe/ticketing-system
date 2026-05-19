package com.pml.identity.service.impl;

import com.pml.identity.event.domain.UserRegisteredEvent;
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
    public Flux<User> findByUserType(UserType userType) {
        return userRepository.findByUserType(userType);
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
                    existingUser.setUserType(user.getUserType());
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
}
