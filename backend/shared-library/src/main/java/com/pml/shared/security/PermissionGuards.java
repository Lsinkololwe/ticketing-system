package com.pml.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The permission questions a {@code @PreAuthorize} expression can ask, answered from the caller's
 * platform roles.
 *
 * <p>Used as {@code @permissionGuards.ownerOrPermission(authentication, #buyerId, 'buyer:view')}: the
 * person the data is about may always read it, and staff holding the named permission may read it for
 * support. It replaces {@code hasAnyRole('ADMIN','FINANCE') or #buyerId == authentication.principal.subject},
 * which named the roles that happen to hold the permission today. Every method answers a plain boolean,
 * so it is safe inside {@code or} and {@code and}, and an unknown permission code or an absent
 * authentication answers {@code false}.
 */
public class PermissionGuards {

    /** Whether the caller's platform roles grant the permission with this code. */
    public boolean holds(Authentication authentication, String permissionCode) {
        Permission permission = Permission.fromCode(permissionCode).orElse(null);
        return permission != null && grants(authentication, permission);
    }

    /** Whether {@code ownerId} is the caller, or the caller's platform roles grant the permission. */
    public boolean ownerOrPermission(Authentication authentication, String ownerId, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return isCaller(authentication, ownerId) || holds(authentication, permissionCode);
    }

    private static boolean isCaller(Authentication authentication, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return false;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return ownerId.equals(jwt.getSubject()) || ownerId.equals(AccountIdentity.userIdOf(jwt));
        }
        return false;
    }

    /** Static form of {@link #holds} for the security beans that fold the platform check into one expression. */
    public static boolean holdsPermission(Authentication authentication, String permissionCode) {
        Permission permission = Permission.fromCode(permissionCode).orElse(null);
        return permission != null && grants(authentication, permission);
    }

    private static boolean grants(Authentication authentication, Permission permission) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getAuthorities() == null) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return PlatformRole.heldByAuthority(authorities).stream().anyMatch(role -> role.grants().contains(permission));
    }
}
