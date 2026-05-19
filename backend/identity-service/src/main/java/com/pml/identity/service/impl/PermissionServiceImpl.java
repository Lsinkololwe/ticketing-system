package com.pml.identity.service.impl;

import com.pml.identity.domain.model.Permission;
import com.pml.identity.domain.model.RolePermission;
import com.pml.identity.repository.PermissionRepository;
import com.pml.identity.repository.RolePermissionRepository;
import com.pml.identity.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Implementation of PermissionService.
 *
 * <p>Manages permission definitions and role-permission mappings.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    // ─────────────────────────────────────────────────────────────────────
    // Permission CRUD
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Permission> findById(String id) {
        log.debug("Finding permission by ID: {}", id);
        return permissionRepository.findById(id);
    }

    @Override
    public Mono<Permission> findByName(String name) {
        log.debug("Finding permission by name: {}", name);
        return permissionRepository.findByName(name);
    }

    @Override
    public Flux<Permission> findAllActive() {
        log.debug("Finding all active permissions");
        return permissionRepository.findByIsActiveTrue();
    }

    @Override
    public Flux<Permission> findByCategory(String category) {
        log.debug("Finding permissions by category: {}", category);
        return permissionRepository.findByCategoryAndIsActiveTrue(category);
    }

    @Override
    public Mono<Permission> create(Permission permission) {
        log.info("Creating permission: {}", permission.getName());
        permission.setActive(true);
        return permissionRepository.save(permission);
    }

    @Override
    public Mono<Permission> update(String id, Permission permissionData) {
        log.info("Updating permission: {}", id);

        return permissionRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Permission not found: " + id)))
                .flatMap(existing -> {
                    if (permissionData.getName() != null) {
                        existing.setName(permissionData.getName());
                    }
                    if (permissionData.getDescription() != null) {
                        existing.setDescription(permissionData.getDescription());
                    }
                    if (permissionData.getCategory() != null) {
                        existing.setCategory(permissionData.getCategory());
                    }
                    return permissionRepository.save(existing);
                });
    }

    @Override
    public Mono<Permission> deactivate(String id) {
        log.info("Deactivating permission: {}", id);

        return permissionRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Permission not found: " + id)))
                .flatMap(permission -> {
                    permission.setActive(false);
                    return permissionRepository.save(permission);
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Role-Permission Mappings
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Flux<Permission> findPermissionsByRole(String roleId) {
        log.debug("Finding permissions for role: {}", roleId);

        return rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId)
                .map(RolePermission::getPermissionId) // This is the permission name
                .flatMap(permissionRepository::findByName);
    }

    @Override
    public Flux<String> findPermissionNamesByRole(String roleId) {
        log.debug("Finding permission names for role: {}", roleId);

        return rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId)
                .map(RolePermission::getPermissionId);
    }

    @Override
    public Mono<Void> assignPermissionToRole(String roleId, String permissionName) {
        log.info("Assigning permission {} to role {}", permissionName, roleId);

        // First verify the permission exists
        return permissionRepository.findByName(permissionName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Permission not found: " + permissionName)))
                .flatMap(permission -> {
                    // Check if already assigned
                    return rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionName)
                            .map(existing -> {
                                // Already exists, just activate if needed
                                existing.setActive(true);
                                return existing;
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // Create new assignment
                                RolePermission rp = RolePermission.builder()
                                        .roleId(roleId)
                                        .permissionId(permissionName)
                                        .isActive(true)
                                        .build();
                                return Mono.just(rp);
                            }))
                            .flatMap(rolePermissionRepository::save);
                })
                .then();
    }

    @Override
    public Mono<Void> removePermissionFromRole(String roleId, String permissionName) {
        log.info("Removing permission {} from role {}", permissionName, roleId);

        return rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionName)
                .flatMap(rp -> {
                    rp.setActive(false);
                    return rolePermissionRepository.save(rp);
                })
                .then();
    }

    // ─────────────────────────────────────────────────────────────────────
    // User Permissions (from JWT roles)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public Mono<List<String>> getPermissionsForRoles(List<String> roles) {
        log.debug("Getting permissions for roles: {}", roles);

        if (roles == null || roles.isEmpty()) {
            log.warn("No roles provided for permission lookup");
            return Mono.just(List.of());
        }

        return Flux.fromIterable(roles)
                .doOnNext(roleId -> log.debug("Looking up permissions for role: {}", roleId))
                .flatMap(roleId -> rolePermissionRepository.findByRoleIdAndIsActiveTrue(roleId)
                        .doOnNext(rp -> log.debug("Found role_permission: roleId={}, permissionId={}",
                                rp.getRoleId(), rp.getPermissionId()))
                        .switchIfEmpty(Flux.defer(() -> {
                            log.warn("No role_permissions found for roleId: '{}'. " +
                                    "Check if Mongock migrations ran and role names match (case-sensitive).", roleId);
                            return Flux.empty();
                        })))
                .map(RolePermission::getPermissionId)
                .distinct()
                .collectList()
                .doOnNext(permissions -> {
                    if (permissions.isEmpty()) {
                        log.warn("Returning EMPTY permissions. Roles checked: {}. " +
                                "Verify role_permissions collection has data.", roles);
                    } else {
                        log.debug("Returning {} permissions: {}", permissions.size(), permissions);
                    }
                });
    }
}
