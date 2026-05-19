package com.pml.identity.web.rest;

import com.pml.identity.service.AuthorizationService;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Internal Authorization Controller
 *
 * <p>Exposes internal REST endpoints for cross-service authorization checks.
 * These endpoints are called by other services (catalog-service, booking-service)
 * to verify user permissions on resources.</p>
 *
 * <h2>Security</h2>
 * <p>All endpoints require internal service scope. Not accessible to regular users.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Centralized authorization service</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with service-to-service auth</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/authorization")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SCOPE_internal-read', 'SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_SYSTEM')")
public class InternalAuthorizationController {

    private final AuthorizationService authorizationService;

    /**
     * Check if a user is authorized to perform an action.
     *
     * <p>This is the primary endpoint for cross-service authorization.
     * The calling service must extract userId from the JWT and include it in the request.</p>
     *
     * @param request Authorization request
     * @return Authorization result with decision and context
     */
    @PostMapping("/check")
    public Mono<ResponseEntity<AuthorizationResult>> checkAuthorization(
            @Valid @RequestBody AuthorizationRequest request) {

        log.debug("Authorization check request: userId={}, permission={}, eventId={}, orgId={}",
                request.getUserId(), request.getRequiredPermission(),
                request.getEventId(), request.getOrganizationId());

        return authorizationService.checkAuthorization(request)
                .map(result -> {
                    if (result.isAuthorized()) {
                        log.debug("Authorization GRANTED: userId={}, permission={}, source={}",
                                request.getUserId(), request.getRequiredPermission(),
                                result.getAuthorizationSource());
                        return ResponseEntity.ok(result);
                    } else {
                        log.debug("Authorization DENIED: userId={}, permission={}, reason={}",
                                request.getUserId(), request.getRequiredPermission(),
                                result.getReason());
                        return ResponseEntity.status(403).body(result);
                    }
                });
    }

    /**
     * Check if user can perform an action on events within an organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @param permission Required permission
     * @return Authorization result
     */
    @GetMapping("/event-permission")
    public Mono<ResponseEntity<AuthorizationResult>> checkEventPermission(
            @RequestParam String userId,
            @RequestParam String organizationId,
            @RequestParam String permission) {

        log.debug("Event permission check: userId={}, orgId={}, permission={}",
                userId, organizationId, permission);

        return authorizationService.checkEventPermission(userId, organizationId, permission)
                .map(result -> result.isAuthorized()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(403).body(result));
    }

    /**
     * Check if user has access to a specific event.
     *
     * @param userId User ID (from JWT)
     * @param eventId Event ID
     * @param organizationId Organization ID (optional, for fallback)
     * @param permission Required permission
     * @return Authorization result
     */
    @GetMapping("/event-access")
    public Mono<ResponseEntity<AuthorizationResult>> checkEventAccess(
            @RequestParam String userId,
            @RequestParam String eventId,
            @RequestParam(required = false) String organizationId,
            @RequestParam String permission) {

        log.debug("Event access check: userId={}, eventId={}, orgId={}, permission={}",
                userId, eventId, organizationId, permission);

        return authorizationService.checkEventAccess(userId, eventId, organizationId, permission)
                .map(result -> result.isAuthorized()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(403).body(result));
    }

    /**
     * Check if user is a member of an organization with at least the specified role.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @param minimumRole Minimum role required
     * @return Authorization result
     */
    @GetMapping("/membership")
    public Mono<ResponseEntity<AuthorizationResult>> checkMembership(
            @RequestParam String userId,
            @RequestParam String organizationId,
            @RequestParam String minimumRole) {

        log.debug("Membership check: userId={}, orgId={}, minimumRole={}",
                userId, organizationId, minimumRole);

        return authorizationService.checkMembership(userId, organizationId, minimumRole)
                .map(result -> result.isAuthorized()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(403).body(result));
    }

    /**
     * Check if user is the owner of an organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @return Authorization result
     */
    @GetMapping("/ownership")
    public Mono<ResponseEntity<AuthorizationResult>> checkOwnership(
            @RequestParam String userId,
            @RequestParam String organizationId) {

        log.debug("Ownership check: userId={}, orgId={}", userId, organizationId);

        return authorizationService.checkOwnership(userId, organizationId)
                .map(result -> result.isAuthorized()
                        ? ResponseEntity.ok(result)
                        : ResponseEntity.status(403).body(result));
    }

    /**
     * Get the default organization for a user.
     *
     * <p>Returns the organization where the user is owner, or where they can create events.</p>
     *
     * @param userId User ID (from JWT)
     * @return Organization ID or 404 if none found
     */
    @GetMapping("/default-organization")
    public Mono<ResponseEntity<OrganizationResponse>> getDefaultOrganization(
            @RequestParam String userId) {

        log.debug("Get default organization: userId={}", userId);

        return authorizationService.getDefaultOrganizationForUser(userId)
                .map(orgId -> ResponseEntity.ok(new OrganizationResponse(orgId)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Find organization by owner ID.
     *
     * <p>Used when we have organizerId but need organizationId.</p>
     *
     * @param ownerId Owner user ID
     * @return Organization ID or 404 if none found
     */
    @GetMapping("/organization-by-owner")
    public Mono<ResponseEntity<OrganizationResponse>> findOrganizationByOwner(
            @RequestParam String ownerId) {

        log.debug("Find organization by owner: ownerId={}", ownerId);

        return authorizationService.findOrganizationByOwnerId(ownerId)
                .map(orgId -> ResponseEntity.ok(new OrganizationResponse(orgId)))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    /**
     * Simple response containing organization ID.
     */
    public record OrganizationResponse(String organizationId) {}

    // ─────────────────────────────────────────────────────────────────────────
    // ORGANIZATION MEMBERSHIP ENDPOINTS (for query resolver authorization)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Check if a user is an active member of an organization.
     *
     * <p>Used by other services to validate organization membership before
     * returning organization-scoped data. This is a critical OWASP A01:2021
     * control for multi-tenant data isolation.</p>
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID to check membership for
     * @return MembershipCheckResponse with membership status and role
     */
    @GetMapping("/check-organization-membership")
    public Mono<ResponseEntity<MembershipCheckResponse>> checkOrganizationMembership(
            @RequestParam String userId,
            @RequestParam String organizationId) {

        log.debug("Organization membership check: userId={}, orgId={}", userId, organizationId);

        return authorizationService.checkOrganizationMembership(userId, organizationId)
                .map(result -> ResponseEntity.ok(result))
                .defaultIfEmpty(ResponseEntity.ok(MembershipCheckResponse.notMember()));
    }

    /**
     * Check if two users belong to the same organization.
     *
     * <p>Used when organizerId is provided and we need to verify the requesting
     * user belongs to the same organization (for team member access).</p>
     *
     * @param requestingUserId The user making the request (from JWT)
     * @param targetOrganizerId The organizer whose data is being requested
     * @return SharedOrganizationResponse with shared organization status
     */
    @GetMapping("/check-same-organization")
    public Mono<ResponseEntity<SharedOrganizationResponse>> checkSameOrganization(
            @RequestParam String requestingUserId,
            @RequestParam String targetOrganizerId) {

        log.debug("Same organization check: requestingUserId={}, targetOrganizerId={}",
                requestingUserId, targetOrganizerId);

        return authorizationService.checkSameOrganization(requestingUserId, targetOrganizerId)
                .map(result -> ResponseEntity.ok(result))
                .defaultIfEmpty(ResponseEntity.ok(SharedOrganizationResponse.noSharedOrganization()));
    }

    /**
     * Get all organization IDs a user is a member of.
     *
     * <p>Used for JWT enrichment and organization context resolution.</p>
     *
     * @param userId User ID (from JWT)
     * @return List of organization memberships
     */
    @GetMapping("/user-organizations")
    public Mono<ResponseEntity<UserOrganizationsResponse>> getUserOrganizations(
            @RequestParam String userId) {

        log.debug("Get user organizations: userId={}", userId);

        return authorizationService.getUserOrganizations(userId)
                .collectList()
                .map(orgs -> ResponseEntity.ok(new UserOrganizationsResponse(orgs)));
    }

    /**
     * Response for organization membership check.
     */
    public record MembershipCheckResponse(
            boolean isMember,
            boolean isActive,
            String role,
            String organizationId
    ) {
        public static MembershipCheckResponse notMember() {
            return new MembershipCheckResponse(false, false, null, null);
        }
    }

    /**
     * Response for same organization check.
     */
    public record SharedOrganizationResponse(
            boolean sharesOrganization,
            String sharedOrganizationId,
            String requestingUserRole,
            String targetUserRole
    ) {
        public static SharedOrganizationResponse noSharedOrganization() {
            return new SharedOrganizationResponse(false, null, null, null);
        }
    }

    /**
     * Response for user organizations query.
     */
    public record UserOrganizationsResponse(
            java.util.List<OrganizationMembershipInfo> organizations
    ) {}

    /**
     * Organization membership info.
     */
    public record OrganizationMembershipInfo(
            String organizationId,
            String organizationName,
            String role,
            boolean isOwner,
            boolean isActive
    ) {}
}
