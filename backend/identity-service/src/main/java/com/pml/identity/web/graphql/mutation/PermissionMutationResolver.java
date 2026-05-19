package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.model.Permission;
import com.pml.identity.service.PermissionService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * GraphQL Mutation Resolver for Permission operations.
 *
 * <p>This resolver uses PermissionService for all operations,
 * following the interface-based design pattern.</p>
 *
 * <p>NOTE: Roles are managed in Keycloak. The roleId parameter refers to a Keycloak
 * role name (e.g., "ADMIN", "ORGANIZER", "CUSTOMER"). This resolver manages the
 * role_permissions mapping table that maps Keycloak roles to fine-grained permissions.</p>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PermissionMutationResolver {

    private final PermissionService permissionService;

    // Valid Keycloak role names (should match roles defined in Keycloak)
    private static final Set<String> VALID_KEYCLOAK_ROLES = Set.of(
            "CUSTOMER", "ORGANIZER", "ADMIN", "SUPER_ADMIN", "SCANNER", "FINANCE"
    );

    /**
     * Create a new permission.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Permission> createPermission(
            @InputArgument String name,
            @InputArgument String description,
            @InputArgument String category) {
        log.info("Creating new permission: {}", name);

        Permission permission = Permission.builder()
                .name(name)
                .description(description)
                .category(category)
                .build();

        return permissionService.create(permission)
                .doOnSuccess(p -> log.info("Permission created: {}", p.getName()));
    }

    /**
     * Update an existing permission.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Permission> updatePermission(
            @InputArgument String id,
            @InputArgument String name,
            @InputArgument String description,
            @InputArgument String category) {
        log.info("Updating permission: {}", id);

        Permission updateData = Permission.builder()
                .name(name)
                .description(description)
                .category(category)
                .build();

        return permissionService.update(id, updateData)
                .doOnSuccess(p -> log.info("Permission updated: {}", p.getName()));
    }

    /**
     * Delete a permission (soft delete by deactivating).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> deletePermission(@InputArgument String id) {
        log.info("Deleting permission: {}", id);

        return permissionService.deactivate(id)
                .thenReturn(true)
                .doOnSuccess(v -> log.info("Permission deleted: {}", id))
                .onErrorResume(e -> {
                    log.error("Failed to delete permission {}: {}", id, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Assign a permission to a role.
     *
     * @param roleId       The Keycloak role name (e.g., "ADMIN", "ORGANIZER")
     * @param permissionId The permission name (e.g., "events:create")
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> assignPermissionToRole(@InputArgument String roleId, @InputArgument String permissionId) {
        log.info("Assigning permission {} to role {}", permissionId, roleId);

        // Validate role is a known Keycloak role
        String normalizedRoleId = roleId.toUpperCase();
        if (!VALID_KEYCLOAK_ROLES.contains(normalizedRoleId)) {
            log.warn("Unknown Keycloak role: {}", roleId);
            return Mono.error(new IllegalArgumentException("Unknown Keycloak role: " + roleId +
                    ". Valid roles are: " + VALID_KEYCLOAK_ROLES));
        }

        return permissionService.assignPermissionToRole(normalizedRoleId, permissionId)
                .thenReturn(true)
                .doOnSuccess(v -> log.info("Permission {} assigned to role {}", permissionId, normalizedRoleId))
                .onErrorResume(e -> {
                    log.error("Failed to assign permission {} to role {}: {}", permissionId, roleId, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Remove a permission from a role.
     *
     * @param roleId       The Keycloak role name (e.g., "ADMIN", "ORGANIZER")
     * @param permissionId The permission name (e.g., "events:create")
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> removePermissionFromRole(@InputArgument String roleId, @InputArgument String permissionId) {
        String normalizedRoleId = roleId.toUpperCase();
        log.info("Removing permission {} from role {}", permissionId, normalizedRoleId);

        return permissionService.removePermissionFromRole(normalizedRoleId, permissionId)
                .thenReturn(true)
                .doOnSuccess(v -> log.info("Permission {} removed from role {}", permissionId, normalizedRoleId))
                .onErrorResume(e -> {
                    log.error("Failed to remove permission {} from role {}: {}", permissionId, roleId, e.getMessage());
                    return Mono.just(false);
                });
    }
}
