package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only views of the permission catalogue and of what the signed-in user may do. Everything
 * here is computed from code — the catalogue on {@link Permission}, role sets on
 * {@link OrganizationRole} and {@link EventRole} — so there is nothing stored to query.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PermissionQueryResolver {

    private static final Set<String> PLATFORM_ROLES = Arrays.stream(UserType.values())
            .map(UserType::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final PermissionResolutionService permissionResolutionService;

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<Map<String, Object>> permissions() {
        return Arrays.stream(Permission.values()).map(PermissionQueryResolver::toGraphQl).toList();
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> permission(@InputArgument String code) {
        return Permission.fromCode(code).map(PermissionQueryResolver::toGraphQl).orElse(null);
    }

    /** What a named role carries; null when no platform, organization or event role has that name. */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, Object> rolePermissions(@InputArgument String role) {
        if (role == null) {
            return null;
        }
        for (OrganizationRole organizationRole : OrganizationRole.values()) {
            if (organizationRole.name().equals(role)) {
                EnumSet<Permission> switchable = EnumSet.copyOf(organizationRole.permissions(allSwitchesOn()));
                switchable.removeAll(organizationRole.permissions());
                return rolePermissions(role, Permission.Scope.ORGANIZATION, organizationRole.permissions(), switchable);
            }
        }
        for (EventRole eventRole : EventRole.values()) {
            if (eventRole.name().equals(role)) {
                return rolePermissions(role, Permission.Scope.EVENT, eventRole.permissions(), Set.of());
            }
        }
        if (PLATFORM_ROLES.contains(role)) {
            return rolePermissions(role, Permission.Scope.PLATFORM, Permission.grantedByPlatformRoles(List.of(role)), Set.of());
        }
        return null;
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<List<String>> currentUserPermissions() {
        return callerRoles().map(roles -> List.copyOf(Permission.codes(Permission.grantedByPlatformRoles(roles))));
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> myPermissions() {
        return callerRoles().map(roles -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("roles", List.copyOf(roles));
            out.put("permissions", List.copyOf(Permission.codes(Permission.grantedByPlatformRoles(roles))));
            return out;
        });
    }

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Map<String, Object>> myEffectivePermissions(
            @InputArgument String organizationId,
            @InputArgument String eventId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> permissionResolutionService
                        .getEffectivePermissions(userId, organizationId, eventId)
                        .map(PermissionQueryResolver::toGraphQl));
    }

    /**
     * The caller's platform roles, taken from the authorities the token was converted into, so
     * the answer matches what {@code @PreAuthorize} checks on the same request.
     */
    private static Mono<Set<String>> callerRoles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> roleNames(context.getAuthentication() == null
                        ? List.of() : context.getAuthentication().getAuthorities()))
                .defaultIfEmpty(Set.of());
    }

    static Set<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
        Set<String> roles = new TreeSet<>();
        for (GrantedAuthority authority : authorities) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_") && PLATFORM_ROLES.contains(name.substring("ROLE_".length()))) {
                roles.add(name.substring("ROLE_".length()));
            }
        }
        return roles;
    }

    private static OrganizationSettings allSwitchesOn() {
        OrganizationSettings settings = new OrganizationSettings();
        settings.setManagersCanViewFinancials(true);
        settings.setAdminsCanRequestPayouts(true);
        return settings;
    }

    private static Map<String, Object> rolePermissions(String role, Permission.Scope scope,
                                                       Set<Permission> permissions, Set<Permission> switchable) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", role);
        out.put("scope", scope.name());
        out.put("permissions", permissions.stream().map(PermissionQueryResolver::toGraphQl).toList());
        out.put("switchable", switchable.stream().map(PermissionQueryResolver::toGraphQl).toList());
        return out;
    }

    private static Map<String, Object> toGraphQl(Permission permission) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", permission.code());
        out.put("module", permission.module());
        out.put("description", permission.description());
        out.put("scope", permission.scope().name());
        return out;
    }

    private static Map<String, Object> toGraphQl(PermissionResolutionService.EffectivePermissions resolved) {
        Object role = resolved.eventRole() != null ? resolved.eventRole() : resolved.organizationRole();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", resolved.userId());
        out.put("organizationId", resolved.organizationId());
        out.put("eventId", resolved.eventId());
        out.put("permissions", List.copyOf(Permission.codes(resolved.permissions())));
        out.put("role", role == null ? null : role.toString());
        out.put("source", resolved.source());
        return out;
    }
}
