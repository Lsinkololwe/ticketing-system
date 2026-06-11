package com.pml.identity.service;

import com.pml.shared.constants.UserType;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Service for synchronizing user roles between MongoDB and Keycloak
 *
 * Implements OWASP authentication and authorization best practices:
 * - Idempotent operations (safe to retry)
 * - Proper error handling without information leakage
 * - Audit logging for all role changes
 * - Circuit breaker for external service resilience
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html">OWASP Authentication Cheat Sheet</a>
 */
public interface RoleSyncService {

    /**
     * Grant ORGANIZER role to a user (both in Keycloak and MongoDB)
     *
     * This operation is idempotent - calling it multiple times with the same
     * parameters will have the same effect as calling it once.
     *
     * @param userId The user's MongoDB ID (matches Keycloak ID)
     * @param performedBy The ID of the admin who granted the role
     * @param organizationId The organization ID (for audit context)
     * @return Mono completing when role is granted successfully
     */
    Mono<Void> grantOrganizerRole(String userId, String performedBy, String organizationId);

    /**
     * Revoke ORGANIZER role from a user (both in Keycloak and MongoDB)
     *
     * This operation is idempotent.
     *
     * @param userId The user's MongoDB ID (matches Keycloak ID)
     * @param performedBy The ID of the admin who revoked the role
     * @param organizationId The organization ID (for audit context)
     * @return Mono completing when role is revoked successfully
     */
    Mono<Void> revokeOrganizerRole(String userId, String performedBy, String organizationId);

    /**
     * Synchronize all roles for a user from MongoDB to Keycloak
     *
     * Ensures consistency between MongoDB User.roles and Keycloak realm roles.
     * This is useful for recovery scenarios or manual corrections.
     *
     * @param userId The user's ID
     * @param performedBy The ID of the admin performing the sync
     * @return Mono completing when sync is successful
     */
    Mono<Void> syncUserRoles(String userId, String performedBy);

    /**
     * Check if Keycloak service is healthy (for circuit breaker)
     *
     * @return Mono with true if healthy, false otherwise
     */
    Mono<Boolean> isKeycloakHealthy();
}
