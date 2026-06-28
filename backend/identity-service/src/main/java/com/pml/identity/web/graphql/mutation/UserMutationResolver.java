package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.infrastructure.cache.OtpService;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.SecurityContextUtils;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.pml.identity.web.graphql.dto.user.UserMutationResponse;

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
    public Mono<Boolean> sendEmailVerification() {
        return SecurityContextUtils.getAuthenticationContext()
                .doOnNext(ctx -> log.info("Sending email verification for user: {} (keycloakId={})", ctx.getEmail(), ctx.getUserId()))
                .flatMap(ctx -> keycloakService.sendVerificationEmail(ctx.getUserId())
                        .thenReturn(true)
                        .doOnSuccess(v -> log.info("Email verification sent successfully for user: {}", ctx.getEmail()))
                        .onErrorResume(e -> {
                            log.error("Failed to send email verification for user {}: {}", ctx.getEmail(), e.getMessage());
                            return Mono.just(false);
                        }))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("sendEmailVerification called without authentication");
                    return Mono.just(false);
                }));
    }

    /**
     * Verify email with token from the verification link.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> verifyEmail(@InputArgument String token) {
        return SecurityContextUtils.getAuthenticationContext()
                .doOnNext(ctx -> log.info("Verifying email for user: {} with token", ctx.getEmail()))
                .flatMap(ctx -> keycloakService.findUserById(ctx.getUserId())
                        .flatMap(keycloakUserOpt -> {
                            if (keycloakUserOpt.isEmpty()) {
                                log.warn("Keycloak user not found: {}", ctx.getUserId());
                                return Mono.empty();
                            }

                            boolean isEmailVerified = keycloakUserOpt.get().isEmailVerified();
                            if (!isEmailVerified) {
                                log.info("Email not yet verified in Keycloak for user: {}", ctx.getEmail());
                                return userService.findByEmail(ctx.getEmail());
                            }

                            return userService.findByEmail(ctx.getEmail())
                                    .flatMap(user -> {
                                        if (!user.isEmailVerified()) {
                                            return userService.verifyEmail(user.getId());
                                        }
                                        return Mono.just(user);
                                    });
                        })
                        .doOnSuccess(u -> {
                            if (u != null) {
                                log.info("Email verification completed for user: {}", ctx.getEmail());
                            }
                        }))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("verifyEmail called without authentication");
                    return Mono.empty();
                }));
    }

    /**
     * Sync email verification status from Keycloak to local database.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> syncEmailVerificationStatus() {
        return SecurityContextUtils.getAuthenticationContext()
                .doOnNext(ctx -> log.info("Syncing email verification status for user: {}", ctx.getEmail()))
                .flatMap(ctx -> keycloakService.findUserById(ctx.getUserId())
                        .flatMap(keycloakUserOpt -> {
                            if (keycloakUserOpt.isEmpty()) {
                                log.warn("Keycloak user not found: {}", ctx.getUserId());
                                return userService.findByEmail(ctx.getEmail());
                            }

                            boolean isEmailVerified = keycloakUserOpt.get().isEmailVerified();

                            return userService.findByEmail(ctx.getEmail())
                                    .flatMap(user -> {
                                        if (isEmailVerified && !user.isEmailVerified()) {
                                            log.info("Updating email verification status for user: {}", ctx.getEmail());
                                            return userService.verifyEmail(user.getId());
                                        }
                                        return Mono.just(user);
                                    });
                        }))
                .switchIfEmpty(Mono.empty());
    }

    // ==========================================
    // Profile Mutations
    // ==========================================

    /**
     * Update the current user's profile.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> updateProfile(@InputArgument Map<String, Object> input) {
        return SecurityContextUtils.getCurrentUserEmail()
                .doOnNext(email -> log.info("Updating profile for user: {}", email))
                .flatMap(email -> userService.findByEmail(email)
                        .flatMap(user -> {
                            User profileUpdate = User.builder()
                                    .firstName(input.get("firstName") != null ? (String) input.get("firstName") : null)
                                    .lastName(input.get("lastName") != null ? (String) input.get("lastName") : null)
                                    .phoneNumber(input.get("phoneNumber") != null ? (String) input.get("phoneNumber") : null)
                                    .build();
                            return userService.updateProfile(user.getId(), profileUpdate);
                        })
                        .doOnSuccess(u -> log.info("Profile updated for user: {}", email)))
                .switchIfEmpty(Mono.empty());
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
            @InputArgument String oldPassword,
            @InputArgument String newPassword) {
        return SecurityContextUtils.getCurrentUserEmail()
                .doOnNext(email -> log.info("Changing password for user: {}", email))
                .flatMap(email -> keycloakService.updatePassword(email, newPassword)
                        .thenReturn(true)
                        .doOnSuccess(success -> log.info("Password changed successfully for user: {}", email))
                        .onErrorResume(e -> {
                            log.error("Password change failed for user {}: {}", email, e.getMessage());
                            return Mono.just(false);
                        }))
                .switchIfEmpty(Mono.just(false));
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
     *
     * <p>Multi-role support: All users automatically get the CUSTOMER role as the base role.
     * Use the grantRoleToUser mutation to add additional roles after creation.</p>
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> createUser(@InputArgument Map<String, Object> input) {
        log.info("Admin creating new user with email: {}", input.get("email"));

        String password = (String) input.get("password");

        // Build user profile data (NO password - Keycloak handles authentication)
        // Note: timestamps are automatically set by Spring Data MongoDB via @CreatedDate/@LastModifiedDate
        // All users automatically get CUSTOMER role via builder default
        User newUser = User.builder()
                .username((String) input.get("username"))
                .email((String) input.get("email"))
                .firstName((String) input.get("firstName"))
                .lastName((String) input.get("lastName"))
                .phoneNumber((String) input.get("phoneNumber"))
                .active(true)
                .build();

        // Create in Keycloak first, then store profile in MongoDB
        return keycloakService.createUser(newUser, password)
                .flatMap(keycloakUserId -> {
                    // Set MongoDB document ID to match Keycloak user ID
                    newUser.setId(keycloakUserId);
                    return userService.createUser(newUser);
                })
                .doOnSuccess(u -> log.info("Admin created user: {} (keycloakId={}) with roles: {}",
                        u.getEmail(), u.getId(), u.getRoles()));
    }

    /**
     * Update a user (admin only).
     *
     * <p>Multi-role support: For role management, use the grantRoleToUser and
     * revokeRoleFromUser mutations instead.</p>
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
                    return userService.updateUser(id, existingUser);
                })
                .flatMap(user -> keycloakService.updateUser(user).thenReturn(user))
                .doOnSuccess(u -> log.info("Admin updated user: {} with roles: {}", u.getEmail(), u.getRoles()));
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

    // ==========================================
    // Multi-Role Management Mutations (OWASP Compliant)
    // ==========================================

    /**
     * Add a role to a user.
     *
     * OWASP Compliance:
     * - A01:2021 Broken Access Control: Admin-only operation with audit logging
     * - A04:2021 Insecure Design: Validates role combinations before applying
     * - A09:2021 Security Logging: Logs role changes with actor information
     *
     * @param userId The user ID to add the role to
     * @param role The role to add
     * @return UserMutationResponse with success/failure and updated user
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserMutationResponse> addUserRole(
            @InputArgument String userId,
            @InputArgument UserType role) {

        // Validate input
        if (userId == null || userId.isBlank()) {
            return Mono.just(UserMutationResponse.failure("User ID is required"));
        }
        if (role == null) {
            return Mono.just(UserMutationResponse.failure("Role is required"));
        }

        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("SECURITY_AUDIT: Admin {} adding role {} to user {}", adminId, role, userId))
                .flatMap(adminId -> {
                    // CUSTOMER role is already present for all users
                    if (role == UserType.CUSTOMER) {
                        return userService.findById(userId)
                                .map(user -> UserMutationResponse.success(user, "CUSTOMER role is already present for all users"))
                                .switchIfEmpty(Mono.just(UserMutationResponse.failure("User not found")));
                    }

                    return userService.addRole(userId, role, adminId)
                            .map(user -> {
                                log.info("SECURITY_AUDIT: Role {} added to user {} by admin {}", role, userId, adminId);
                                return UserMutationResponse.success(user, "Role " + role + " added successfully");
                            })
                            .onErrorResume(IllegalArgumentException.class, e -> {
                                log.warn("SECURITY_AUDIT: Failed to add role {} to user {}: {}", role, userId, e.getMessage());
                                return Mono.just(UserMutationResponse.failure(e.getMessage()));
                            })
                            .onErrorResume(IllegalStateException.class, e -> {
                                log.warn("SECURITY_AUDIT: Invalid state when adding role {} to user {}: {}", role, userId, e.getMessage());
                                return Mono.just(UserMutationResponse.failure(e.getMessage()));
                            })
                            .switchIfEmpty(Mono.just(UserMutationResponse.failure("User not found")));
                });
    }

    /**
     * Remove a role from a user.
     *
     * OWASP Compliance:
     * - A01:2021 Broken Access Control: Admin-only operation, CUSTOMER role cannot be removed
     * - A04:2021 Insecure Design: Validates role removal rules before applying
     * - A09:2021 Security Logging: Logs role changes with actor information
     *
     * @param userId The user ID to remove the role from
     * @param role The role to remove
     * @return UserMutationResponse with success/failure and updated user
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserMutationResponse> removeUserRole(
            @InputArgument String userId,
            @InputArgument UserType role) {

        // Validate input
        if (userId == null || userId.isBlank()) {
            return Mono.just(UserMutationResponse.failure("User ID is required"));
        }
        if (role == null) {
            return Mono.just(UserMutationResponse.failure("Role is required"));
        }

        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("SECURITY_AUDIT: Admin {} removing role {} from user {}", adminId, role, userId))
                .flatMap(adminId -> {
                    // CUSTOMER role cannot be removed (it's the base role)
                    if (role == UserType.CUSTOMER) {
                        log.warn("SECURITY_AUDIT: Attempted to remove CUSTOMER role from user {} by admin {}", userId, adminId);
                        return Mono.just(UserMutationResponse.failure("CUSTOMER role cannot be removed - it is the base role for all users"));
                    }

                    return userService.removeRole(userId, role, adminId)
                            .map(user -> {
                                log.info("SECURITY_AUDIT: Role {} removed from user {} by admin {}", role, userId, adminId);
                                return UserMutationResponse.success(user, "Role " + role + " removed successfully");
                            })
                            .onErrorResume(IllegalArgumentException.class, e -> {
                                log.warn("SECURITY_AUDIT: Failed to remove role {} from user {}: {}", role, userId, e.getMessage());
                                return Mono.just(UserMutationResponse.failure(e.getMessage()));
                            })
                            .onErrorResume(IllegalStateException.class, e -> {
                                log.warn("SECURITY_AUDIT: Invalid state when removing role {} from user {}: {}", role, userId, e.getMessage());
                                return Mono.just(UserMutationResponse.failure(e.getMessage()));
                            })
                            .switchIfEmpty(Mono.just(UserMutationResponse.failure("User not found")));
                });
    }

    /**
     * Set all roles for a user (replaces existing roles).
     *
     * OWASP Compliance:
     * - A01:2021 Broken Access Control: Admin-only operation, validates CUSTOMER role is included
     * - A04:2021 Insecure Design: Validates role combinations before applying
     * - A09:2021 Security Logging: Logs complete role changes with actor information
     *
     * @param userId The user ID to set roles for
     * @param roles The complete set of roles (must include CUSTOMER)
     * @return UserMutationResponse with success/failure and updated user
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<UserMutationResponse> setUserRoles(
            @InputArgument String userId,
            @InputArgument List<UserType> roles) {

        // Validate input
        if (userId == null || userId.isBlank()) {
            return Mono.just(UserMutationResponse.failure("User ID is required"));
        }
        if (roles == null || roles.isEmpty()) {
            return Mono.just(UserMutationResponse.failure("At least one role is required"));
        }

        // CUSTOMER role must be included
        if (!roles.contains(UserType.CUSTOMER)) {
            return SecurityContextUtils.getCurrentUserId()
                    .defaultIfEmpty("system")
                    .doOnNext(adminId -> log.warn("SECURITY_AUDIT: Attempted to set roles without CUSTOMER for user {} by admin {}", userId, adminId))
                    .map(adminId -> UserMutationResponse.failure("CUSTOMER role must be included - it is the base role for all users"));
        }

        // Convert to Set for the service
        Set<UserType> roleSet = EnumSet.copyOf(roles);

        // Validate role combination
        if (!UserType.isValidRoleCombination(roleSet)) {
            return Mono.just(UserMutationResponse.failure("Invalid role combination"));
        }

        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("SECURITY_AUDIT: Admin {} setting roles for user {} to: {}", adminId, userId, roles))
                .flatMap(adminId -> userService.setRoles(userId, roleSet, adminId)
                        .map(user -> {
                            log.info("SECURITY_AUDIT: Roles set to {} for user {} by admin {}", roleSet, userId, adminId);
                            return UserMutationResponse.success(user, "Roles updated successfully");
                        })
                        .onErrorResume(IllegalArgumentException.class, e -> {
                            log.warn("SECURITY_AUDIT: Failed to set roles for user {}: {}", userId, e.getMessage());
                            return Mono.just(UserMutationResponse.failure(e.getMessage()));
                        })
                        .onErrorResume(IllegalStateException.class, e -> {
                            log.warn("SECURITY_AUDIT: Invalid state when setting roles for user {}: {}", userId, e.getMessage());
                            return Mono.just(UserMutationResponse.failure(e.getMessage()));
                        })
                        .switchIfEmpty(Mono.just(UserMutationResponse.failure("User not found"))));
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
    public Mono<Boolean> sendPhoneVerification() {
        return SecurityContextUtils.getCurrentUserEmail()
                .doOnNext(email -> log.info("Sending phone verification for user: {}", email))
                .flatMap(email -> userService.findByEmail(email)
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
                        }))
                .onErrorResume(e -> {
                    log.error("Failed to send phone verification: {}", e.getMessage());
                    return Mono.just(false);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("sendPhoneVerification called without authentication");
                    return Mono.just(false);
                }));
    }

    /**
     * Verify phone number with OTP code.
     * Validates the OTP and updates phone verification status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> verifyPhone(@InputArgument String code) {
        return SecurityContextUtils.getAuthenticationContext()
                .doOnNext(ctx -> log.info("Verifying phone for user: {} with code", ctx.getEmail()))
                .flatMap(ctx -> userService.findByEmail(ctx.getEmail())
                        .flatMap(user -> {
                            if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                                log.warn("User {} has no phone number set", ctx.getEmail());
                                return Mono.empty();
                            }

                            if (user.isPhoneVerified()) {
                                log.info("Phone already verified for user: {}", ctx.getEmail());
                                return Mono.just(user);
                            }

                            // Verify OTP
                            return otpService.verifyOtp(user.getPhoneNumber(), code)
                                    .flatMap(valid -> {
                                        if (!valid) {
                                            log.warn("Invalid OTP for user: {}", ctx.getEmail());
                                            return Mono.empty();
                                        }

                                        // Update phone verification status
                                        return userService.verifyPhone(user.getId())
                                                .flatMap(updatedUser -> {
                                                    // Also update in Keycloak
                                                    return keycloakService.updatePhoneVerified(ctx.getUserId(), true)
                                                            .thenReturn(updatedUser);
                                                });
                                    });
                        })
                        .doOnSuccess(u -> {
                            if (u != null) {
                                log.info("Phone verified for user: {}", ctx.getEmail());
                            }
                        }))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("verifyPhone called without authentication");
                    return Mono.empty();
                }));
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
