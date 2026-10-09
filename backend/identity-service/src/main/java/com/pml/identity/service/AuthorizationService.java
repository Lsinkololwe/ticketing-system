package com.pml.identity.service;

import com.pml.identity.web.rest.InternalAuthorizationController.OrganizationMembershipInfo;
import com.pml.identity.web.rest.InternalAuthorizationController.SharedOrganizationResponse;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Authorization Service Interface
 *
 * <p>Provides centralized authorization decisions for cross-service access control.
 * Used by other services (catalog-service, booking-service) to verify user permissions
 * on resources owned by organizations.</p>
 *
 * <h2>Authorization Flow</h2>
 * <pre>
 * 1. Check if user is ADMIN → Authorized
 * 2. Check EventAccessGrant for event-specific permissions (OVERRIDES org membership)
 * 3. Check OrganizationMember for organization-level permissions
 * 4. Check if user is the organization owner
 * 5. Deny if none of the above
 * </pre>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Centralized authorization logic</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with multiple checks</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface AuthorizationService {

    /**
     * Check if a user is authorized to perform an action on a resource.
     *
     * <p>This is the primary method for cross-service authorization.
     * The userId must be extracted from the JWT by the calling service.</p>
     *
     * @param request Authorization request with user, resource, and permission details
     * @return AuthorizationResult with decision and context
     */
    Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request);

    /**
     * Check if user can manage events for an organization.
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @param permission a catalogue code, e.g. "event:create"
     * @return AuthorizationResult
     */
    Mono<AuthorizationResult> checkEventPermission(String userId, String organizationId, String permission);

    /**
     * Check if user can manage a specific event.
     *
     * <p>Checks both EventAccessGrant and organization membership.</p>
     *
     * @param userId User ID (from JWT)
     * @param eventId Event ID
     * @param organizationId Organization that owns the event (can be null if unknown)
     * @param permission Required permission
     * @return AuthorizationResult
     */
    Mono<AuthorizationResult> checkEventAccess(String userId, String eventId, String organizationId, String permission);

    /**
     * Check if user owns the organization (is the OWNER).
     *
     * <p>Convenience method that returns a boolean instead of AuthorizationResult.</p>
     *
     * @param userId User ID (from JWT)
     * @param organizationId Organization ID
     * @return Mono emitting true if user is the owner, false otherwise
     */
    Mono<Boolean> isOrganizationOwner(String userId, String organizationId);

    /**
     * Find the organization ID for an event's organizer.
     *
     * <p>Used when we have organizerId (user ID) but need organizationId.
     * Finds the organization where this user is the owner.</p>
     *
     * @param organizerId User ID of the event organizer
     * @return Mono containing organization ID, or empty if not found
     */
    Mono<String> findOrganizationByOwnerId(String organizerId);

    /**
     * Check if two users belong to the same organization.
     *
     * <p>Used when organizerId is provided in a query and we need to verify
     * the requesting user has access to that organizer's data (team member access).</p>
     *
     * @param requestingUserId The user making the request (from JWT)
     * @param targetOrganizerId The organizer whose data is being requested
     * @return SharedOrganizationResponse with shared organization status
     */
    Mono<SharedOrganizationResponse> checkSameOrganization(String requestingUserId, String targetOrganizerId);

    /**
     * Get all organizations a user is a member of.
     *
     * @param userId User ID (from JWT)
     * @return Flux of organization membership info
     */
    Flux<OrganizationMembershipInfo> getUserOrganizations(String userId);
}
