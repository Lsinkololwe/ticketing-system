package com.pml.identity.web.rest;

import com.pml.identity.service.AuthorizationService;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
 * <p>The shared {@code /api/internal/**} filter chain ({@code SecurityConfig}) requires {@code
 * SCOPE_internal-read} for the GET endpoints below and {@code SCOPE_internal-write} for {@code
 * /check} (or {@code ROLE_INTERNAL_SERVICE}/{@code ROLE_SYSTEM} for either), enforced once at the
 * chain rather than repeated here where a mixed read/write class could only state one scope and
 * be wrong for the other method.</p>
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
     * The organization's public display name, for a caller (catalog, booking) that denormalizes
     * it for display (e.g. the organizer name shown on an event) and only has the id.
     *
     * <p>Never used for an authorization decision — a blank or unknown id answers 404, not an
     * error, since "no name on file" is routine (a deleted or bad-data organization id), not a
     * fault the caller needs to retry.</p>
     *
     * @param organizationId Organization ID
     * @return The organization's name, or 404 if blank or not found
     */
    @GetMapping("/organization-name")
    public Mono<ResponseEntity<OrganizationNameResponse>> getOrganizationName(
            @RequestParam String organizationId) {

        log.debug("Get organization name: organizationId={}", organizationId);

        return authorizationService.getOrganizationName(organizationId)
                .map(name -> ResponseEntity.ok(new OrganizationNameResponse(name)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Response for same organization check.
     */
    public record SharedOrganizationResponse(
            boolean sharesOrganization,
            String sharedOrganizationId
    ) {
        public static SharedOrganizationResponse noSharedOrganization() {
            return new SharedOrganizationResponse(false, null);
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
            String role,
            boolean isActive
    ) {}

    /**
     * Response for the organization name lookup.
     */
    public record OrganizationNameResponse(String name) {}
}
