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
import java.util.Optional;
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
     * Get the current authenticated user's ID (Keycloak subject claim).
     *
     * <p>This is the primary method for getting the user's identity.
     * The ID is extracted from the JWT 'sub' claim which is set by Keycloak.</p>
     *
     * @return Mono containing the user ID, or empty if not authenticated
     */
    public static Mono<String> getCurrentUserId() {
        return getJwt()
                .map(Jwt::getSubject);
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
     * Get the current authenticated user's username.
     *
     * @return Mono containing the username, or empty if not authenticated
     */
    public static Mono<String> getCurrentUsername() {
        return getJwt()
                .map(jwt -> jwt.getClaimAsString("preferred_username"));
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
    // ROLE AND AUTHORITY CHECKS
    // ========================================================================

    /**
     * Get all granted authorities for the current user.
     *
     * @return Mono containing set of authority strings (e.g., "ROLE_ORGANIZER", "SCOPE_internal-read")
     */
    public static Mono<Set<String>> getCurrentAuthorities() {
        return getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .defaultIfEmpty(Collections.emptySet());
    }

    /**
     * Check if the current user has a specific role.
     *
     * @param role Role name without ROLE_ prefix (e.g., "ORGANIZER", "ADMIN")
     * @return Mono<Boolean> true if user has the role
     */
    public static Mono<Boolean> hasRole(String role) {
        return getCurrentAuthorities()
                .map(authorities -> authorities.contains("ROLE_" + role));
    }

    /**
     * Check if the current user has any of the specified roles.
     *
     * @param roles Role names without ROLE_ prefix
     * @return Mono<Boolean> true if user has any of the roles
     */
    public static Mono<Boolean> hasAnyRole(String... roles) {
        return getCurrentAuthorities()
                .map(authorities -> {
                    for (String role : roles) {
                        if (authorities.contains("ROLE_" + role)) {
                            return true;
                        }
                    }
                    return false;
                });
    }

    /**
     * Check if the current user is an admin.
     *
     * @return Mono<Boolean> true if user has ADMIN or SUPER_ADMIN role
     */
    public static Mono<Boolean> isAdmin() {
        return hasAnyRole("ADMIN", "SUPER_ADMIN");
    }

    /**
     * Check if the current user is an organizer.
     *
     * @return Mono<Boolean> true if user has ORGANIZER role
     */
    public static Mono<Boolean> isOrganizer() {
        return hasRole("ORGANIZER");
    }

    /**
     * Check if the current user is an internal service.
     *
     * @return Mono<Boolean> true if request is from an internal service
     */
    public static Mono<Boolean> isInternalService() {
        return getCurrentAuthorities()
                .map(authorities ->
                        authorities.contains("ROLE_INTERNAL_SERVICE") ||
                        authorities.contains("SCOPE_internal-read") ||
                        authorities.contains("SCOPE_internal-write"));
    }

    // ========================================================================
    // CUSTOM CLAIMS EXTRACTION
    // ========================================================================

    /**
     * Get the user type from JWT custom claim.
     *
     * @return Mono containing user type (CUSTOMER, ORGANIZER, ADMIN), or empty
     */
    public static Mono<String> getUserType() {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaimAsString("userType"));
    }

    /**
     * Get phone number from JWT claims.
     *
     * @return Mono containing phone number, or empty
     */
    public static Mono<String> getPhoneNumber() {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaimAsString("phone_number"));
    }

    /**
     * Check if phone is verified.
     *
     * @return Mono<Boolean> true if phone is verified
     */
    public static Mono<Boolean> isPhoneVerified() {
        return getJwt()
                .map(jwt -> Boolean.TRUE.equals(jwt.getClaim("phone_verified")))
                .defaultIfEmpty(false);
    }

    /**
     * Get organization IDs from JWT claims (if custom claim is set).
     *
     * @return Mono containing list of organization IDs user belongs to
     */
    @SuppressWarnings("unchecked")
    public static Mono<List<String>> getOrganizationIds() {
        return getJwt()
                .map(jwt -> {
                    Object orgClaim = jwt.getClaim("organizations");
                    if (orgClaim instanceof List) {
                        return ((List<Map<String, Object>>) orgClaim).stream()
                                .map(org -> (String) org.get("id"))
                                .collect(Collectors.toList());
                    }
                    return Collections.<String>emptyList();
                })
                .defaultIfEmpty(Collections.emptyList());
    }

    /**
     * Get primary organization ID from JWT claims.
     *
     * @return Mono containing primary organization ID, or empty
     */
    public static Mono<String> getPrimaryOrganizationId() {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaimAsString("primaryOrganizationId"));
    }

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

    /**
     * Get a custom claim value as a specific type.
     *
     * @param claimName Name of the claim
     * @param claimType Expected type of the claim
     * @param <T> Type parameter
     * @return Mono containing claim value, or empty
     */
    public static <T> Mono<T> getClaim(String claimName, Class<T> claimType) {
        return getJwt()
                .mapNotNull(jwt -> jwt.getClaim(claimName))
                .filter(claimType::isInstance)
                .cast(claimType);
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
    public static Mono<Authentication> getAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated);
    }

    /**
     * Check if there is an authenticated user.
     *
     * @return Mono<Boolean> true if authenticated
     */
    public static Mono<Boolean> isAuthenticated() {
        return getAuthentication()
                .map(auth -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Get the full authentication context for debugging.
     *
     * @return Mono containing AuthenticationContext with all relevant info
     */
    public static Mono<AuthenticationContext> getAuthenticationContext() {
        return getJwt()
                .flatMap(jwt -> getCurrentAuthorities()
                        .map(authorities -> AuthenticationContext.builder()
                                .userId(jwt.getSubject())
                                .username(jwt.getClaimAsString("preferred_username"))
                                .email(jwt.getClaimAsString("email"))
                                .phoneNumber(jwt.getClaimAsString("phone_number"))
                                .userType(jwt.getClaimAsString("userType"))
                                .authorities(authorities)
                                .tokenId(jwt.getId())
                                .issuedAt(jwt.getIssuedAt())
                                .expiresAt(jwt.getExpiresAt())
                                .build()));
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
        String phoneNumber;
        String userType;
        Set<String> authorities;
        String tokenId;
        java.time.Instant issuedAt;
        java.time.Instant expiresAt;

        public boolean hasRole(String role) {
            return authorities != null && authorities.contains("ROLE_" + role);
        }

        public boolean isAdmin() {
            return hasRole("ADMIN") || hasRole("SUPER_ADMIN");
        }

        public boolean isOrganizer() {
            return hasRole("ORGANIZER");
        }
    }
}
