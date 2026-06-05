package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.Permission;
import com.pml.identity.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GraphQL Query Resolver for Permission operations.
 *
 * <p>This resolver uses PermissionService for all operations,
 * following the interface-based design pattern.</p>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PermissionQueryResolver {

    private final PermissionService permissionService;

    /**
     * Get a permission by ID.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Permission> permission(@InputArgument String id) {
        log.debug("GraphQL query: permission(id={})", id);
        return permissionService.findById(id);
    }

    /**
     * Get a permission by name.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Permission> permissionByName(@InputArgument String name) {
        log.debug("GraphQL query: permissionByName(name={})", name);
        return permissionService.findByName(name);
    }

    /**
     * Get all active permissions.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<Permission> permissions() {
        log.debug("GraphQL query: permissions");
        return permissionService.findAllActive();
    }

    /**
     * Get permissions by category.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<Permission> permissionsByCategory(@InputArgument String category) {
        log.debug("GraphQL query: permissionsByCategory(category={})", category);
        return permissionService.findByCategory(category);
    }

    /**
     * Get permissions assigned to a role.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<Permission> rolePermissions(@InputArgument String roleId) {
        log.debug("GraphQL query: rolePermissions(roleId={})", roleId);
        return permissionService.findPermissionsByRole(roleId);
    }

    /**
     * Get permissions for the currently authenticated user based on their roles.
     *
     * <p>This query extracts roles from the JWT token (realm_access.roles,
     * resource_access) and looks up the associated
     * permissions from the role_permissions collection.</p>
     *
     * @param jwt The authenticated user's JWT token
     * @return List of permission names the user has
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<List<String>> currentUserPermissions(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.warn("GraphQL query: currentUserPermissions - no JWT present");
            return Mono.just(List.of());
        }

        List<String> userRoles = extractRolesFromJwt(jwt);
        log.info("GraphQL query: currentUserPermissions - extracted roles from JWT: {}", userRoles);

        if (userRoles.isEmpty()) {
            log.warn("GraphQL query: currentUserPermissions - no valid roles found in JWT. " +
                    "JWT claims: realm_access={}, resource_access={}",
                    jwt.getClaim("realm_access"),
                    jwt.getClaim("resource_access"));
            return Mono.just(List.of());
        }

        return permissionService.getPermissionsForRoles(userRoles);
    }

    /**
     * Extract roles from JWT token.
     *
     * <p>Extracts from:</p>
     * <ul>
     *   <li>realm_access.roles (Keycloak realm roles)</li>
     *   <li>resource_access.{client}.roles (Keycloak client roles)</li>
     *   <li>realm_access.roles (realm-level roles)</li>
     * </ul>
     *
     * <p>Note: Role names are normalized to UPPERCASE to match the role_permissions
     * collection which stores roles as ADMIN, SUPER_ADMIN, CUSTOMER, etc.</p>
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromJwt(Jwt jwt) {
        List<String> roles = new ArrayList<>();

        // Extract realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List) {
                List<String> realmRoles = (List<String>) rolesObj;
                roles.addAll(realmRoles.stream()
                        .filter(this::isValidRole)
                        .map(String::toUpperCase)
                        .toList());
            }
        }

        // Extract client roles from resource_access
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((clientId, access) -> {
                if (access instanceof Map) {
                    Map<String, Object> clientAccess = (Map<String, Object>) access;
                    Object clientRolesObj = clientAccess.get("roles");
                    if (clientRolesObj instanceof List) {
                        List<String> clientRoles = (List<String>) clientRolesObj;
                        roles.addAll(clientRoles.stream()
                                .filter(this::isValidRole)
                                .map(String::toUpperCase)
                                .toList());
                    }
                }
            });
        }

        return roles.stream().distinct().toList();
    }

    /**
     * Validates if a role should be included (filters out Keycloak defaults).
     */
    private boolean isValidRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        // Skip default Keycloak roles
        if (role.startsWith("default-roles-")) {
            return false;
        }
        return !List.of("offline_access", "uma_authorization").contains(role);
    }
}
