package com.pml.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security Context Utilities
 *
 * <p>Provides reactive utilities for extracting authenticated user information
 * from the Spring Security context. All identity information MUST come from
 * the JWT token, never from frontend parameters.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Extracts identity from server-side JWT, not client input</li>
 *   <li>A07:2021 - Identification and Authentication Failures: Validates JWT claims</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * // In a reactive service method:
 * SecurityContextUtils.getCurrentUserId()
 *     .flatMap(userId -> eventRepository.findByOrganizerId(userId))
 *     .switchIfEmpty(Mono.error(new UnauthorizedException("Not authenticated")));
 * </pre>
 *
 * <h2>Role-Based Access Control</h2>
 * <p>For role checks, use Spring Security's {@code @PreAuthorize} annotations instead of
 * programmatic checks. This provides declarative security and better auditability.</p>
 * <pre>
 * &#64;PreAuthorize("hasRole('ORGANIZER')")
 * public Mono&lt;Event&gt; createEvent(...) { ... }
 * </pre>
 *
 * @since 1.0.0
 */
public final class SecurityContextUtils {

    private SecurityContextUtils() {
        // Utility class - prevent instantiation
    }

    // ========================================================================
    // PRIMARY IDENTITY EXTRACTION
    // ========================================================================

    /**
     * Get the current authenticated user's ID from the JWT 'sub' claim.
     *
     * <p>Uses the OIDC standard 'sub' (subject) claim which is the unique,
     * immutable identifier for the user. This is the industry-standard
     * approach per RFC 7519 and OpenID Connect Core 1.0.</p>
     *
     * @return Mono containing the user ID, or empty if not authenticated
     */
    public static Mono<String> getCurrentUserId() {
        return getJwt()
                .mapNotNull(Jwt::getSubject)
                .filter(subject -> !subject.isBlank());
    }

    /**
     * Get the current authenticated user's ID or throw an error.
     *
     * <p>Use this when authentication is required and you want to fail fast.</p>
     *
     * @return Mono containing the user ID
     * @throws SecurityException if not authenticated
     */
    public static Mono<String> requireCurrentUserId() {
        return getCurrentUserId()
                .switchIfEmpty(Mono.error(new SecurityException("Authentication required")));
    }

    /**
     * Get the current authenticated user's email.
     *
     * @return Mono containing the email, or empty if not authenticated or no email claim
     */
    public static Mono<String> getCurrentUserEmail() {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaimAsString("email"));
    }

    // ========================================================================
    // CUSTOM CLAIMS EXTRACTION
    // ========================================================================

    /**
     * Get a custom claim value from the JWT.
     *
     * @param claimName Name of the claim
     * @return Mono containing claim value as string, or empty
     */
    public static Mono<String> getClaim(String claimName) {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaimAsString(claimName));
    }

    // ========================================================================
    // AUTHENTICATION CONTEXT
    // ========================================================================

    /**
     * Get the full JWT token.
     *
     * @return Mono containing the JWT, or empty if not authenticated
     */
    public static Mono<Jwt> getJwt() {
        return getAuthentication()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken);
    }

    /**
     * Get the Authentication object.
     *
     * @return Mono containing Authentication, or empty if not authenticated
     */
    private static Mono<Authentication> getAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated);
    }

    /**
     * Get all granted authorities for the current user.
     *
     * @return Mono containing set of authority strings (e.g., "ROLE_ORGANIZER", "SCOPE_internal-read")
     */
    private static Mono<Set<String>> getCurrentAuthorities() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .defaultIfEmpty(Collections.emptySet());
    }

    /**
     * Get the full authentication context for debugging and comprehensive user info.
     *
     * @return Mono containing AuthenticationContext with all relevant info
     */
    @SuppressWarnings("unchecked")
    public static Mono<AuthenticationContext> getAuthenticationContext() {
        return getJwt()
                .flatMap(jwt -> getCurrentAuthorities()
                        .map(authorities -> {
                            // Extract roles from realm_access (Keycloak standard)
                            java.util.Set<String> roles = new java.util.HashSet<>();
                            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                            if (realmAccess != null) {
                                Object rolesObj = realmAccess.get("roles");
                                if (rolesObj instanceof List) {
                                    ((List<String>) rolesObj).forEach(roles::add);
                                }
                            }
                            if (roles.isEmpty()) {
                                roles.add("CUSTOMER");
                            }

                            return AuthenticationContext.builder()
                                    .userId(jwt.getSubject())
                                    .username(jwt.getClaimAsString("preferred_username"))
                                    .email(jwt.getClaimAsString("email"))
                                    .roles(roles)
                                    .authorities(authorities)
                                    .tokenId(jwt.getId())
                                    .issuedAt(jwt.getIssuedAt())
                                    .expiresAt(jwt.getExpiresAt())
                                    .build();
                        }));
    }

    /**
     * Authentication context containing all relevant user info.
     * Use this when you need multiple pieces of identity info.
     */
    @lombok.Value
    @lombok.Builder
    public static class AuthenticationContext {
        String userId;
        String username;
        String email;
        Set<String> roles;
        Set<String> authorities;
        String tokenId;
        java.time.Instant issuedAt;
        java.time.Instant expiresAt;

        public boolean hasRole(String role) {
            return (roles != null && roles.contains(role)) ||
                   (authorities != null && authorities.contains("ROLE_" + role));
        }

        public boolean isAdmin() {
            return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        }

        public boolean isOrganizer() {
            return hasRole("ORGANIZER");
        }
    }
}
