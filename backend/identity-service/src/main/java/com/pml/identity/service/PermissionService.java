package com.pml.identity.service;

import com.pml.identity.domain.model.Permission;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service interface for Permission operations.
 *
 * <p>Manages permission definitions and role-permission mappings.
 * All GraphQL resolvers should use this service instead of accessing
 * repositories directly.</p>
 *
 * @see Permission
 * @see PermissionResolutionService For computing effective permissions
 */
public interface PermissionService {

    // ─────────────────────────────────────────────────────────────────────
    // Permission CRUD
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find permission by ID.
     *
     * @param id The permission ID
     * @return The permission if found
     */
    Mono<Permission> findById(String id);

    /**
     * Find permission by name.
     *
     * @param name The permission name (e.g., "EVENT_CREATE")
     * @return The permission if found
     */
    Mono<Permission> findByName(String name);

    /**
     * Find all active permissions.
     *
     * @return Flux of active permissions
     */
    Flux<Permission> findAllActive();

    /**
     * Find permissions by category.
     *
     * @param category The permission category (e.g., "EVENT", "ORGANIZATION")
     * @return Flux of permissions in the category
     */
    Flux<Permission> findByCategory(String category);

    /**
     * Create a new permission.
     *
     * @param permission The permission to create
     * @return The created permission
     */
    Mono<Permission> create(Permission permission);

    /**
     * Update a permission.
     *
     * @param id The permission ID
     * @param permission Updated permission data
     * @return The updated permission
     */
    Mono<Permission> update(String id, Permission permission);

    /**
     * Deactivate a permission.
     *
     * @param id The permission ID
     * @return The deactivated permission
     */
    Mono<Permission> deactivate(String id);

    // ─────────────────────────────────────────────────────────────────────
    // Role-Permission Mappings
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get all permissions for a role.
     *
     * @param roleId The role ID (e.g., "ADMIN", "MANAGER")
     * @return Flux of permissions assigned to the role
     */
    Flux<Permission> findPermissionsByRole(String roleId);

    /**
     * Get permission names for a role.
     *
     * @param roleId The role ID
     * @return Flux of permission names
     */
    Flux<String> findPermissionNamesByRole(String roleId);

    /**
     * Assign a permission to a role.
     *
     * @param roleId The role ID
     * @param permissionName The permission name
     * @return Mono completing when assignment is done
     */
    Mono<Void> assignPermissionToRole(String roleId, String permissionName);

    /**
     * Remove a permission from a role.
     *
     * @param roleId The role ID
     * @param permissionName The permission name
     * @return Mono completing when removal is done
     */
    Mono<Void> removePermissionFromRole(String roleId, String permissionName);

    // ─────────────────────────────────────────────────────────────────────
    // User Permissions (from JWT roles)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get all permission names for a list of roles.
     * Used to compute permissions from JWT token roles.
     *
     * @param roles List of role IDs from JWT
     * @return Mono containing list of unique permission names
     */
    Mono<List<String>> getPermissionsForRoles(List<String> roles);
}
