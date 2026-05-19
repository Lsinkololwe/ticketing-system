package com.pml.identity.repository;

import com.pml.identity.domain.model.RolePermission;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Role Permission Repository
 */
@Repository
public interface RolePermissionRepository extends ReactiveMongoRepository<RolePermission, String> {

    Flux<RolePermission> findByRoleId(String roleId);

    Flux<RolePermission> findByPermissionId(String permissionId);

    Mono<RolePermission> findByRoleIdAndPermissionId(String roleId, String permissionId);

    Mono<Boolean> existsByRoleIdAndPermissionId(String roleId, String permissionId);

    /**
     * Find all role permissions for a given role where isActive is true.
     * Using explicit @Query to ensure correct field name mapping for boolean isActive field.
     */
    @Query("{ 'roleId': ?0, 'isActive': true }")
    Flux<RolePermission> findByRoleIdAndIsActiveTrue(String roleId);

    Mono<Void> deleteByRoleIdAndPermissionId(String roleId, String permissionId);
}
