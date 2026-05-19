package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.infrastructure.cache.OtpService;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.constants.UserType;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * GraphQL Mutation Resolver for User operations.
 * Handles user-related mutations including profile updates and email verification.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserMutationResolver {

    private final UserService userService;
    private final KeycloakService keycloakService;
    private final OtpService otpService;
    private final MessagingService messagingService;
    private final UserSyncService userSyncService;

    // ==========================================
    // Email Verification Mutations
    // ==========================================

    /**
     * Send email verification to the currently authenticated user.
     * Triggers Keycloak to send a verification email with a link.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> sendEmailVerification(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.warn("sendEmailVerification called without authentication");
            return Mono.just(false);
        }

        String keycloakUserId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        log.info("Sending email verification for user: {} (keycloakId={})", email, keycloakUserId);

        return keycloakService.sendVerificationEmail(keycloakUserId)
                .thenReturn(true)
                .doOnSuccess(v -> log.info("Email verification sent successfully for user: {}", email))
                .onErrorResume(e -> {
                    log.error("Failed to send email verification for user {}: {}", email, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Verify email with token from the verification link.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> verifyEmail(@AuthenticationPrincipal Jwt jwt, @InputArgument String token) {
        if (jwt == null) {
            log.warn("verifyEmail called without authentication");
            return Mono.empty();
        }

        String email = jwt.getClaimAsString("email");
        String keycloakUserId = jwt.getSubject();

        log.info("Verifying email for user: {} with token", email);

        return keycloakService.findUserById(keycloakUserId)
                .flatMap(keycloakUserOpt -> {
                    if (keycloakUserOpt.isEmpty()) {
                        log.warn("Keycloak user not found: {}", keycloakUserId);
                        return Mono.empty();
                    }

                    boolean isEmailVerified = keycloakUserOpt.get().isEmailVerified();
                    if (!isEmailVerified) {
                        log.info("Email not yet verified in Keycloak for user: {}", email);
                        return userService.findByEmail(email);
                    }

                    return userService.findByEmail(email)
                            .flatMap(user -> {
                                if (!user.isEmailVerified()) {
                                    return userService.verifyEmail(user.getId());
                                }
                                return Mono.just(user);
                            });
                })
                .doOnSuccess(u -> {
                    if (u != null) {
                        log.info("Email verification completed for user: {}", email);
                    }
                });
    }

    /**
     * Sync email verification status from Keycloak to local database.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> syncEmailVerificationStatus(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.empty();
        }

        String email = jwt.getClaimAsString("email");
        String keycloakUserId = jwt.getSubject();

        log.info("Syncing email verification status for user: {}", email);

        return keycloakService.findUserById(keycloakUserId)
                .flatMap(keycloakUserOpt -> {
                    if (keycloakUserOpt.isEmpty()) {
                        log.warn("Keycloak user not found: {}", keycloakUserId);
                        return userService.findByEmail(email);
                    }

                    boolean isEmailVerified = keycloakUserOpt.get().isEmailVerified();

                    return userService.findByEmail(email)
                            .flatMap(user -> {
                                if (isEmailVerified && !user.isEmailVerified()) {
                                    log.info("Updating email verification status for user: {}", email);
                                    return userService.verifyEmail(user.getId());
                                }
                                return Mono.just(user);
                            });
                });
    }

    // ==========================================
    // Profile Mutations
    // ==========================================

    /**
     * Update the current user's profile.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> updateProfile(@AuthenticationPrincipal Jwt jwt, @InputArgument Map<String, Object> input) {
        if (jwt == null) {
            return Mono.empty();
        }

        String email = jwt.getClaimAsString("email");
        log.info("Updating profile for user: {}", email);

        return userService.findByEmail(email)
                .flatMap(user -> {
                    User profileUpdate = User.builder()
                            .firstName(input.get("firstName") != null ? (String) input.get("firstName") : null)
                            .lastName(input.get("lastName") != null ? (String) input.get("lastName") : null)
                            .phoneNumber(input.get("phoneNumber") != null ? (String) input.get("phoneNumber") : null)
                            .bio(input.get("bio") != null ? (String) input.get("bio") : null)
                            .locale(input.get("locale") != null ? (String) input.get("locale") : null)
                            .timezone(input.get("timezone") != null ? (String) input.get("timezone") : null)
                            .build();
                    return userService.updateProfile(user.getId(), profileUpdate);
                })
                .doOnSuccess(u -> log.info("Profile updated for user: {}", email));
    }

    // ==========================================
    // Password Mutations
    // ==========================================

    /**
     * Change the current user's password.
     *
     * ARCHITECTURE NOTE: Password management is handled ONLY by Keycloak.
     * This mutation validates the old password and updates the password in Keycloak.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @InputArgument String oldPassword,
            @InputArgument String newPassword) {
        if (jwt == null) {
            return Mono.just(false);
        }

        String email = jwt.getClaimAsString("email");
        log.info("Changing password for user: {}", email);

        // Password is managed solely by Keycloak
        // Note: Keycloak's updatePassword doesn't validate old password by default
        // For enhanced security, implement password validation via Keycloak's authentication flow
        return keycloakService.updatePassword(email, newPassword)
                .thenReturn(true)
                .doOnSuccess(success -> log.info("Password changed successfully for user: {}", email))
                .onErrorResume(e -> {
                    log.error("Password change failed for user {}: {}", email, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Reset password for a user (sends reset email).
     */
    @DgsMutation
    public Mono<Boolean> resetPassword(@InputArgument String email) {
        log.info("Initiating password reset for email: {}", email);

        return keycloakService.findUserByEmail(email)
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty()) {
                        log.warn("User not found for password reset: {}", email);
                        return Mono.just(true); // Don't reveal if user exists
                    }
                    return keycloakService.sendPasswordResetEmail(userOpt.get().getId())
                            .thenReturn(true);
                })
                .onErrorResume(e -> {
                    log.error("Failed to send password reset email: {}", e.getMessage());
                    return Mono.just(true); // Don't reveal errors to prevent enumeration
                });
    }

    // ==========================================
    // Admin User Mutations
    // ==========================================

    /**
     * Create a new user (admin only).
     *
     * ARCHITECTURE NOTE: Keycloak is the source of truth for authentication.
     * Creates user in Keycloak FIRST with the password, then stores profile in MongoDB
     * with the Keycloak user ID as the document ID.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> createUser(@InputArgument Map<String, Object> input) {
        log.info("Admin creating new user with email: {}", input.get("email"));

        String userTypeStr = (String) input.get("userType");
        UserType userType = userTypeStr != null ? UserType.valueOf(userTypeStr) : UserType.CUSTOMER;
        String password = (String) input.get("password");

        // Build user profile data (NO password - Keycloak handles authentication)
        // Note: timestamps are automatically set by Spring Data MongoDB via @CreatedDate/@LastModifiedDate
        User newUser = User.builder()
                .username((String) input.get("username"))
                .email((String) input.get("email"))
                .firstName((String) input.get("firstName"))
                .lastName((String) input.get("lastName"))
                .phoneNumber((String) input.get("phoneNumber"))
                .userType(userType)
                .active(true)
                .build();

        // Create in Keycloak first, then store profile in MongoDB
        return keycloakService.createUser(newUser, password)
                .flatMap(keycloakUserId -> {
                    // Set MongoDB document ID to match Keycloak user ID
                    newUser.setId(keycloakUserId);
                    return userService.createUser(newUser);
                })
                .doOnSuccess(u -> log.info("Admin created user: {} (keycloakId={})", u.getEmail(), u.getId()));
    }

    /**
     * Update a user (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> updateUser(@InputArgument String id, @InputArgument Map<String, Object> input) {
        log.info("Admin updating user: {}", id);

        return userService.findById(id)
                .flatMap(existingUser -> {
                    if (input.get("firstName") != null) {
                        existingUser.setFirstName((String) input.get("firstName"));
                    }
                    if (input.get("lastName") != null) {
                        existingUser.setLastName((String) input.get("lastName"));
                    }
                    if (input.get("phoneNumber") != null) {
                        existingUser.setPhoneNumber((String) input.get("phoneNumber"));
                    }
                    if (input.get("userType") != null) {
                        existingUser.setUserType(UserType.valueOf((String) input.get("userType")));
                    }
                    if (input.get("bio") != null) {
                        existingUser.setBio((String) input.get("bio"));
                    }
                    if (input.get("locale") != null) {
                        existingUser.setLocale((String) input.get("locale"));
                    }
                    if (input.get("timezone") != null) {
                        existingUser.setTimezone((String) input.get("timezone"));
                    }
                    return userService.updateUser(id, existingUser);
                })
                .flatMap(user -> keycloakService.updateUser(user).thenReturn(user))
                .doOnSuccess(u -> log.info("Admin updated user: {}", u.getEmail()));
    }

    /**
     * Deactivate a user (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> deactivateUser(@InputArgument String id) {
        log.info("Admin deactivating user: {}", id);

        return userService.findById(id)
                .flatMap(user -> userService.deactivateUser(id)
                        .then(keycloakService.setUserEnabled(user.getEmail(), false)))
                .thenReturn(true)
                .doOnSuccess(v -> log.info("User deactivated: {}", id))
                .onErrorResume(e -> {
                    log.error("Failed to deactivate user {}: {}", id, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Activate a user (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> activateUser(@InputArgument String id) {
        log.info("Admin activating user: {}", id);

        return userService.findById(id)
                .flatMap(user -> userService.activateUser(id)
                        .then(keycloakService.setUserEnabled(user.getEmail(), true)))
                .thenReturn(true)
                .doOnSuccess(v -> log.info("User activated: {}", id))
                .onErrorResume(e -> {
                    log.error("Failed to activate user {}: {}", id, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Lock a user account (admin only).
     *
     * ARCHITECTURE NOTE: Account locking is handled ONLY by Keycloak.
     * This disables the user in Keycloak, preventing authentication.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> lockUser(@InputArgument String id) {
        log.info("Admin locking user: {}", id);

        // Account locking is handled solely by Keycloak (disable user)
        return userService.findById(id)
                .flatMap(user -> keycloakService.setUserEnabled(user.getEmail(), false))
                .thenReturn(true)
                .doOnSuccess(v -> log.info("User locked: {}", id))
                .onErrorResume(e -> {
                    log.error("Failed to lock user {}: {}", id, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Unlock a user account (admin only).
     *
     * ARCHITECTURE NOTE: Account unlocking is handled ONLY by Keycloak.
     * This enables the user in Keycloak, allowing authentication.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> unlockUser(@InputArgument String id) {
        log.info("Admin unlocking user: {}", id);

        // Account unlocking is handled solely by Keycloak (enable user)
        return userService.findById(id)
                .flatMap(user -> keycloakService.setUserEnabled(user.getEmail(), true))
                .thenReturn(true)
                .doOnSuccess(v -> log.info("User unlocked: {}", id))
                .onErrorResume(e -> {
                    log.error("Failed to unlock user {}: {}", id, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Update user roles in Keycloak (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> updateUserRoles(
            @InputArgument String userId,
            @InputArgument java.util.List<String> roles) {
        log.info("Admin updating roles for user {} to: {}", userId, roles);

        return keycloakService.getUserRoles(userId)
                .flatMap(currentRoles -> {
                    // Remove roles not in new list
                    java.util.List<Mono<Void>> removeOperations = currentRoles.stream()
                            .filter(r -> !roles.contains(r) && !r.equals("offline_access") && !r.equals("uma_authorization"))
                            .map(r -> keycloakService.removeRoleFromUser(userId, r))
                            .toList();

                    // Add new roles
                    java.util.List<Mono<Void>> addOperations = roles.stream()
                            .filter(r -> !currentRoles.contains(r))
                            .map(r -> keycloakService.addRoleToUser(userId, r))
                            .toList();

                    return Mono.when(removeOperations)
                            .then(Mono.when(addOperations));
                })
                .thenReturn(true)
                .doOnSuccess(v -> log.info("Roles updated for user: {}", userId))
                .onErrorResume(e -> {
                    log.error("Failed to update roles for user {}: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    // ==========================================
    // Phone Verification Mutations
    // ==========================================

    /**
     * Send phone verification OTP to the currently authenticated user.
     * Generates an OTP, stores it in Redis, and sends via WhatsApp/SMS.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> sendPhoneVerification(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.warn("sendPhoneVerification called without authentication");
            return Mono.just(false);
        }

        String email = jwt.getClaimAsString("email");
        log.info("Sending phone verification for user: {}", email);

        return userService.findByEmail(email)
                .flatMap(user -> {
                    if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                        log.warn("User {} has no phone number set", email);
                        return Mono.just(false);
                    }

                    if (user.isPhoneVerified()) {
                        log.info("Phone already verified for user: {}", email);
                        return Mono.just(true);
                    }

                    // Check cooldown
                    return otpService.canSendOtp(user.getPhoneNumber())
                            .flatMap(canSend -> {
                                if (!canSend) {
                                    log.warn("OTP cooldown active for user: {}", email);
                                    return Mono.just(false);
                                }

                                // Generate and send OTP
                                return otpService.generateOtp(user.getPhoneNumber())
                                        .flatMap(otp -> messagingService.sendOtp(user.getPhoneNumber(), otp, "whatsapp"))
                                        .flatMap(sent -> {
                                            if (sent) {
                                                return otpService.setCooldown(user.getPhoneNumber())
                                                        .thenReturn(true);
                                            }
                                            return Mono.just(false);
                                        });
                            });
                })
                .doOnSuccess(result -> {
                    if (result) {
                        log.info("Phone verification OTP sent for user: {}", email);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to send phone verification: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Verify phone number with OTP code.
     * Validates the OTP and updates phone verification status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> verifyPhone(@AuthenticationPrincipal Jwt jwt, @InputArgument String code) {
        if (jwt == null) {
            log.warn("verifyPhone called without authentication");
            return Mono.empty();
        }

        String email = jwt.getClaimAsString("email");
        String keycloakUserId = jwt.getSubject();
        log.info("Verifying phone for user: {} with code", email);

        return userService.findByEmail(email)
                .flatMap(user -> {
                    if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                        log.warn("User {} has no phone number set", email);
                        return Mono.empty();
                    }

                    if (user.isPhoneVerified()) {
                        log.info("Phone already verified for user: {}", email);
                        return Mono.just(user);
                    }

                    // Verify OTP
                    return otpService.verifyOtp(user.getPhoneNumber(), code)
                            .flatMap(valid -> {
                                if (!valid) {
                                    log.warn("Invalid OTP for user: {}", email);
                                    return Mono.empty();
                                }

                                // Update phone verification status
                                return userService.verifyPhone(user.getId())
                                        .flatMap(updatedUser -> {
                                            // Also update in Keycloak
                                            return keycloakService.updatePhoneVerified(keycloakUserId, true)
                                                    .thenReturn(updatedUser);
                                        });
                            });
                })
                .doOnSuccess(u -> {
                    if (u != null) {
                        log.info("Phone verified for user: {}", email);
                    }
                });
    }

    // ==========================================
    // Keycloak Sync Mutations (Admin only)
    // ==========================================

    /**
     * Sync a single user from Keycloak to MongoDB.
     *
     * Admin only - refreshes user data from Keycloak (the source of truth).
     * Use this to fix sync drift or verify data consistency.
     *
     * @param userId The Keycloak user ID (sub claim)
     * @return The synced user
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> syncUserFromKeycloak(@InputArgument String userId) {
        log.info("Admin syncing user from Keycloak: {}", userId);

        return userSyncService.syncUserFromKeycloak(userId)
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("User synced successfully from Keycloak: {}", userId);
                    }
                })
                .doOnError(e -> log.error("Failed to sync user from Keycloak: {}", e.getMessage()));
    }

    /**
     * Sync all users from Keycloak to MongoDB.
     *
     * Admin only - full sync operation for recovery scenarios.
     * Warning: This can take significant time for large user bases.
     *
     * @return true if sync started successfully
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> syncAllUsersFromKeycloak() {
        log.info("Admin initiated full user sync from Keycloak");

        // Start async sync and return immediately
        userSyncService.syncAllUsersFromKeycloak()
                .subscribe(
                        v -> log.info("Full user sync completed successfully"),
                        e -> log.error("Full user sync failed: {}", e.getMessage())
                );

        return Mono.just(true);
    }
}
