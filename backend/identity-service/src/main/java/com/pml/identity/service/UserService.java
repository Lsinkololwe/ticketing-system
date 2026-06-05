package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * User Service Interface
 *
 * ARCHITECTURE NOTE (Phase 1 Migration):
 * ======================================
 * This service manages user profile data stored in MongoDB.
 * Keycloak is the SINGLE SOURCE OF TRUTH for:
 * - Authentication (handled by KeycloakAuthService)
 * - Password management (handled by KeycloakService)
 * - Account locking/brute force protection (handled by Keycloak automatically)
 *
 * Methods REMOVED (now handled by Keycloak):
 * - changePassword() → Use KeycloakService.updatePassword()
 * - incrementFailedLoginAttempts() → Keycloak brute force protection
 * - resetFailedLoginAttempts() → Keycloak handles automatically
 * - lockAccount() → Keycloak handles via brute force protection
 * - unlockAccount() → Use Keycloak Admin Console or Admin API
 *
 * MULTI-ROLE MODEL:
 * =================
 * Users can have multiple roles. The CUSTOMER role is the base role
 * that all users have and cannot be removed.
 */
public interface UserService {

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    Mono<User> findById(String id);

    Mono<User> findByUsername(String username);

    Mono<User> findByEmail(String email);

    Mono<User> findByPhoneNumber(String phoneNumber);

    Flux<User> findAll();

    /**
     * Find all users who have a specific role.
     *
     * @param role the role to search for
     * @return Flux of users with the specified role
     */
    Flux<User> findByRole(UserType role);

    Flux<User> findActiveUsers();

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByUsername(String username);

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    /**
     * Create a new user in MongoDB.
     * NOTE: User should be created in Keycloak FIRST, then this method stores
     * the profile data with the Keycloak user ID as the MongoDB document ID.
     */
    Mono<User> createUser(User user);

    Mono<User> updateUser(String id, User user);

    Mono<User> updateProfile(String id, User profileData);

    Mono<Void> deleteUser(String id);

    Mono<Void> deactivateUser(String id);

    Mono<Void> activateUser(String id);

    // ========================================================================
    // VERIFICATION OPERATIONS
    // ========================================================================

    /**
     * Mark email as verified and sync to Keycloak.
     */
    Mono<User> verifyEmail(String userId);

    /**
     * Verify phone with OTP code (validates against stored OTP).
     * @deprecated Use verifyPhone(userId) after validating OTP via OtpService/Redis
     */
    Mono<User> verifyPhone(String userId, String otpCode);

    /**
     * Mark phone as verified (when OTP is already validated externally via Redis).
     */
    Mono<User> verifyPhone(String userId);

    /**
     * Send email verification via Keycloak.
     */
    Mono<Void> sendEmailVerification(String userId);

    /**
     * Send phone verification OTP.
     */
    Mono<Void> sendPhoneVerification(String userId);

    // ========================================================================
    // ACTIVITY TRACKING
    // ========================================================================

    /**
     * Update the last login timestamp.
     */
    Mono<User> updateLastLogin(String userId);

    // ========================================================================
    // ROLE MANAGEMENT OPERATIONS
    // ========================================================================

    /**
     * Add a role to a user.
     * This method syncs the role change to Keycloak and logs the audit event.
     *
     * @param userId the user ID
     * @param role the role to add
     * @param addedBy the ID of the user performing the action (for audit)
     * @return Mono with the updated user
     * @throws IllegalArgumentException if the role cannot be added (invalid combination)
     * @throws IllegalStateException if CUSTOMER role is attempted to be added (already present)
     */
    Mono<User> addRole(String userId, UserType role, String addedBy);

    /**
     * Remove a role from a user.
     * This method syncs the role change to Keycloak and logs the audit event.
     *
     * Note: The CUSTOMER role cannot be removed.
     *
     * @param userId the user ID
     * @param role the role to remove
     * @param removedBy the ID of the user performing the action (for audit)
     * @return Mono with the updated user
     * @throws IllegalArgumentException if the role cannot be removed
     * @throws IllegalStateException if CUSTOMER role removal is attempted
     */
    Mono<User> removeRole(String userId, UserType role, String removedBy);

    /**
     * Set the complete set of roles for a user.
     * This method replaces all existing roles and syncs to Keycloak.
     *
     * Note: The roles set must include CUSTOMER and be a valid combination.
     *
     * @param userId the user ID
     * @param roles the new set of roles
     * @param updatedBy the ID of the user performing the action (for audit)
     * @return Mono with the updated user
     */
    Mono<User> setRoles(String userId, Set<UserType> roles, String updatedBy);

    /**
     * Get all roles for a user.
     *
     * @param userId the user ID
     * @return Mono with the set of roles
     */
    Mono<Set<UserType>> getRoles(String userId);
}
