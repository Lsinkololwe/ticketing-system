package com.pml.identity.web.rest;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Debug controller for authentication troubleshooting.
 *
 * <p><b>WARNING:</b> This controller exposes sensitive authentication information.
 * Remove or secure in production!</p>
 *
 * <p>Usage: GET /api/debug/auth with Bearer token</p>
 */
@RestController
@RequestMapping("/api/debug")
public class AuthDebugController {

    /**
     * Returns the current authentication context including:
     * - User ID (sub claim)
     * - Username
     * - Email
     * - All extracted authorities (roles and scopes)
     * - Raw realm_access.roles from JWT
     */
    @GetMapping("/auth")
    public Mono<Map<String, Object>> getAuthenticationInfo() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(this::extractAuthInfo)
                .defaultIfEmpty(Map.of(
                        "authenticated", false,
                        "message", "No authentication found in security context"
                ));
    }

    private Map<String, Object> extractAuthInfo(Authentication authentication) {
        Map<String, Object> info = new HashMap<>();

        info.put("authenticated", authentication.isAuthenticated());
        info.put("principalType", authentication.getClass().getSimpleName());
        info.put("principal", authentication.getName());

        // Extract authorities
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        info.put("authorities", authorities);

        // Separate roles and scopes for clarity
        List<String> roles = authorities.stream()
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toList());
        List<String> scopes = authorities.stream()
                .filter(a -> a.startsWith("SCOPE_"))
                .collect(Collectors.toList());
        info.put("roles", roles);
        info.put("scopes", scopes);

        // If JWT authentication, extract more details
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            info.put("tokenType", "JWT");
            info.put("subject", jwt.getSubject());
            info.put("issuer", jwt.getIssuer().toString());
            info.put("preferredUsername", jwt.getClaimAsString("preferred_username"));
            info.put("email", jwt.getClaimAsString("email"));

            // Raw realm_access.roles from token
            @SuppressWarnings("unchecked")
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                info.put("rawRealmAccessRoles", realmAccess.get("roles"));
            }

            // Raw resource_access
            @SuppressWarnings("unchecked")
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                info.put("rawResourceAccess", resourceAccess);
            }

            // Token expiration
            info.put("expiresAt", jwt.getExpiresAt());
            info.put("issuedAt", jwt.getIssuedAt());
        }

        return info;
    }
}
