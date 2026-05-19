package com.pml.catalog.config.security;

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
 * &#64;PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
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

    /**
     * Check if the authenticated user is an active member of the specified organization.
     *
     * <p>Use this for queries that accept organizationId directly (not organizerId).</p>
     *
     * @param organizationId The organization to check membership for
     * @param authentication Spring Security authentication object
     * @return Mono&lt;Boolean&gt; true if the user is a member
     */
    public Mono<Boolean> isMemberOfOrganization(String organizationId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Not authenticated - denying organization access");
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            log.debug("Could not extract user ID from authentication");
            return Mono.just(false);
        }

        return identityServiceClient.checkOrganizationMembership(userId, organizationId)
                .map(result -> {
                    boolean allowed = result.isMember() && result.isActive();
                    if (allowed) {
                        log.debug("Organization membership confirmed: userId={}, orgId={}, role={}",
                                userId, organizationId, result.role());
                    } else {
                        log.debug("Organization membership denied: userId={}, orgId={}, isMember={}, isActive={}",
                                userId, organizationId, result.isMember(), result.isActive());
                    }
                    return allowed;
                })
                .onErrorResume(e -> {
                    log.error("Error checking organization membership: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Check if the authenticated user has a specific role in the organization.
     *
     * <p>Use this for role-based access control within an organization.</p>
     *
     * @param organizationId The organization to check
     * @param requiredRole The minimum role required (e.g., "OWNER", "ADMIN", "MANAGER")
     * @param authentication Spring Security authentication object
     * @return Mono&lt;Boolean&gt; true if the user has the required role
     */
    public Mono<Boolean> hasOrganizationRole(String organizationId, String requiredRole, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }

        String userId = extractUserId(authentication);
        if (userId == null) {
            return Mono.just(false);
        }

        return identityServiceClient.checkOrganizationMembership(userId, organizationId)
                .map(result -> {
                    if (!result.isMember() || !result.isActive()) {
                        return false;
                    }

                    // Check role hierarchy
                    return isRoleAtLeast(result.role(), requiredRole);
                })
                .onErrorResume(e -> {
                    log.error("Error checking organization role: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Check if the authenticated user can manage events for the given organizer.
     *
     * <p>Event management requires either:</p>
     * <ul>
     *   <li>Being the organizer themselves</li>
     *   <li>Being a team member with OWNER, ADMIN, or MANAGER role</li>
     * </ul>
     *
     * @param organizerId The organizer whose events are being managed
     * @param authentication Spring Security authentication object
     * @return Mono&lt;Boolean&gt; true if the user can manage events
     */
    public Mono<Boolean> canManageEvents(String organizerId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(false);
        }

        String requestingUserId = extractUserId(authentication);
        if (requestingUserId == null) {
            return Mono.just(false);
        }

        // Self-access always allowed
        if (requestingUserId.equals(organizerId)) {
            return Mono.just(true);
        }

        // Check if user is a team member with event management permission
        return identityServiceClient.checkSameOrganization(requestingUserId, organizerId)
                .map(result -> {
                    if (!result.sharesOrganization()) {
                        return false;
                    }
                    // Only OWNER, ADMIN, and MANAGER can manage events
                    String role = result.requestingUserRole();
                    return "OWNER".equals(role) || "ADMIN".equals(role) || "MANAGER".equals(role);
                })
                .onErrorResume(e -> {
                    log.error("Error checking event management access: {}", e.getMessage());
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
            return jwt.getSubject();
        }
        return null;
    }

    /**
     * Check if a role is at least as privileged as the required role.
     *
     * <p>Role hierarchy: OWNER > ADMIN > MANAGER > MARKETER > CONTRIBUTOR</p>
     */
    private boolean isRoleAtLeast(String actualRole, String requiredRole) {
        if (actualRole == null || requiredRole == null) {
            return false;
        }

        int actualLevel = getRoleLevel(actualRole);
        int requiredLevel = getRoleLevel(requiredRole);

        return actualLevel >= requiredLevel;
    }

    /**
     * Get numeric level for role comparison.
     */
    private int getRoleLevel(String role) {
        return switch (role.toUpperCase()) {
            case "OWNER" -> 5;
            case "ADMIN" -> 4;
            case "MANAGER" -> 3;
            case "MARKETER" -> 2;
            case "CONTRIBUTOR" -> 1;
            default -> 0;
        };
    }
}
