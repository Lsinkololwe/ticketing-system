package com.pml.catalog.security;

import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Organization Security Service
 *
 * <p>Provides organization membership validation for use in @PreAuthorize
 * expressions. This is a critical OWASP A01:2021 control for multi-tenant
 * data isolation.</p>
 *
 * <h2>Usage in @PreAuthorize</h2>
 * <pre>
 * // Check if user is the organizer OR belongs to the same organization
 * &#64;PreAuthorize("@organizationSecurityService.rolesOrTeamMember(authentication, 'ADMIN', #organizerId)")
 * public Flux&lt;Event&gt; eventsByOrganizer(@InputArgument String organizerId)
 * </pre>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Validates organization membership before data access</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with centralized authorization</li>
 *   <li>Multi-Tenant Security: Ensures users can only access their organization's data</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Service("organizationSecurityService")
@RequiredArgsConstructor
public class OrganizationSecurityService {

    private final IdentityServiceClient identityServiceClient;

    /**
     * Check if the authenticated user is the organizer OR belongs to the same organization.
     *
     * <p>This is the primary method for query resolvers that accept organizerId.
     * It allows:</p>
     * <ul>
     *   <li>The organizer themselves to access their data</li>
     *   <li>Team members of the same organization to access the data</li>
     * </ul>
     *
     * @param organizerId The organizer whose data is being accessed
     * @param authentication Spring Security authentication object
     * @return Mono&lt;Boolean&gt; true if the user has access
     */
    public Mono<Boolean> isOrganizerOrTeamMember(String organizerId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Not authenticated - denying access");
            return Mono.just(false);
        }

        String requestingUserId = extractUserId(authentication);
        if (requestingUserId == null) {
            log.debug("Could not extract user ID from authentication");
            return Mono.just(false);
        }

        // Same user - always allowed (self-access)
        if (requestingUserId.equals(organizerId)) {
            log.debug("Self-access allowed: userId={}", requestingUserId);
            return Mono.just(true);
        }

        // Check if users belong to the same organization
        return identityServiceClient.checkSameOrganization(requestingUserId, organizerId)
                .map(result -> {
                    boolean allowed = result.sharesOrganization();
                    if (allowed) {
                        log.debug("Team member access allowed: requestingUser={}, targetOrganizer={}, sharedOrg={}",
                                requestingUserId, organizerId, result.sharedOrganizationId());
                    } else {
                        log.debug("Access denied: requestingUser={} does not share organization with organizerId={}",
                                requestingUserId, organizerId);
                    }
                    return allowed;
                })
                .onErrorResume(e -> {
                    log.error("Error checking organization membership: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extract user ID from Spring Security Authentication object.
     */
    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return com.pml.shared.security.AccountIdentity.userIdOf(jwt);
        }
        return null;
    }


    /**
     * One expression for "holds one of these roles, or is the organizer or on their team".
     *
     * <p>A {@code @PreAuthorize} expression that joins a role test to a bean call with {@code or}
     * cannot be used when the check returns a {@code Mono}: the {@code or} yields a Mono that is not unwrapped, and the call
     * fails with a ConverterNotFoundException (MonoJust to Boolean). A single reactive expression can.</p>
     */
    public Mono<Boolean> rolesOrTeamMember(Authentication authentication, String rolesCsv, String organizerId) {
        if (holdsAnyRole(authentication, rolesCsv)) {
            return Mono.just(true);
        }
        return isOrganizerOrTeamMember(organizerId, authentication);
    }

    private static boolean holdsAnyRole(Authentication authentication, String rolesCsv) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (String role : rolesCsv.split(",")) {
            wanted.add("ROLE_" + role.trim());
        }
        return authentication.getAuthorities().stream().anyMatch(a -> wanted.contains(a.getAuthority()));
    }
}
