package com.pml.identity.service;

import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Permission Resolution Service Interface
 *
 * Resolves effective permissions for users based on their roles and access grants.
 *
 * RESOLUTION ORDER:
 * 1. Platform role (super admin overrides all)
 * 2. Event-level access (if checking event permission)
 * 3. Organization role
 * 4. Custom permissions
 * 5. Denied permissions (explicit deny)
 * 6. Default: deny
 */
public interface PermissionResolutionService {

    // ─────────────────────────────────────────────────────────────────────
    // Organization Permission Checks
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user has permission in organization
     */
    Mono<Boolean> hasOrganizationPermission(String userId, String organizationId, String permission);

    /**
     * Get user's effective organization permissions
     */
    Mono<Set<String>> getEffectiveOrganizationPermissions(String userId, String organizationId);

    /**
     * Get user's organization role
     */
    Mono<OrganizationRole> getOrganizationRole(String userId, String organizationId);

    // ─────────────────────────────────────────────────────────────────────
    // Event Permission Checks
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user has permission for event
     * Checks event-level access first, then falls back to organization role
     */
    Mono<Boolean> hasEventPermission(String userId, String eventId, String organizationId, String permission);

    /**
     * Get user's effective event permissions
     */
    Mono<Set<String>> getEffectiveEventPermissions(String userId, String eventId, String organizationId);

    /**
     * Get user's event role (or null if no event-level access)
     */
    Mono<EventRole> getEventRole(String userId, String eventId);

    // ─────────────────────────────────────────────────────────────────────
    // Platform Permission Checks
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user has platform-level permission (admin operations)
     */
    Mono<Boolean> hasPlatformPermission(String userId, String permission);

    /**
     * Check if user is platform admin
     */
    Mono<Boolean> isPlatformAdmin(String userId);

    /**
     * Check if user is super admin
     */
    Mono<Boolean> isSuperAdmin(String userId);

    // ─────────────────────────────────────────────────────────────────────
    // Combined Permission Checks
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get all effective permissions for a user in a context
     */
    Mono<EffectivePermissions> getEffectivePermissions(String userId, String organizationId, String eventId);

    /**
     * Effective permissions result
     */
    record EffectivePermissions(
            String userId,
            String organizationId,
            String eventId,
            Set<String> permissions,
            OrganizationRole organizationRole,
            EventRole eventRole,
            String source // PLATFORM, ORGANIZATION, EVENT
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Role Permission Mapping
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get default permissions for an organization role
     */
    Set<String> getOrganizationRolePermissions(OrganizationRole role);

    /**
     * Get default permissions for an event role
     */
    Set<String> getEventRolePermissions(EventRole role);
}
