package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import com.pml.shared.constants.UserType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    Flux<User> findByUserType(UserType userType);

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
}
