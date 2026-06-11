package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.service.AuthorizationService;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.rest.InternalAuthorizationController.MembershipCheckResponse;
import com.pml.identity.web.rest.InternalAuthorizationController.OrganizationMembershipInfo;
import com.pml.identity.web.rest.InternalAuthorizationController.SharedOrganizationResponse;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.dto.authorization.AuthorizationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * Authorization Service Implementation
 *
 * <p>Implements centralized authorization logic combining organization membership
 * and event access grants.</p>
 *
 * <h2>Permission Mapping</h2>
 * <pre>
 * Permission          | Required Role(s)
 * --------------------|------------------
 * EVENT_CREATE        | OWNER, ADMIN, MANAGER
 * EVENT_EDIT          | OWNER, ADMIN, MANAGER, EDITOR (event-level)
 * EVENT_DELETE        | OWNER, ADMIN
 * EVENT_PUBLISH       | OWNER, ADMIN, MANAGER
 * EVENT_VIEW          | All members
 * TICKET_SCAN         | OWNER, ADMIN, MANAGER, CHECK_IN (event-level)
 * FINANCIAL_VIEW      | OWNER, ADMIN, MANAGER
 * PAYOUT_REQUEST      | OWNER, ADMIN
 * MEMBER_INVITE       | OWNER, ADMIN
 * MEMBER_REMOVE       | OWNER, ADMIN
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final OrganizationMemberService memberService;
    private final OrganizationService organizationService;
    private final EventAccessService eventAccessService;

    @Override
    public Mono<AuthorizationResult> checkAuthorization(AuthorizationRequest request) {
        log.debug("Checking authorization: userId={}, permission={}, eventId={}, orgId={}",
                request.getUserId(), request.getRequiredPermission(),
                request.getEventId(), request.getOrganizationId());

        // Step 1: If event-specific check, use event access flow
        if (request.getEventId() != null) {
            return checkEventAccess(
                    request.getUserId(),
                    request.getEventId(),
                    request.getOrganizationId(),
                    request.getRequiredPermission()
            );
        }

        // Step 2: Organization-level check
        String organizationId = request.getOrganizationId();

        // If we have organizerOwnerId but not organizationId, find the organization
        if (organizationId == null && request.getOrganizationOwnerId() != null) {
            return findOrganizationByOwnerId(request.getOrganizationOwnerId())
                    .flatMap(orgId -> checkEventPermission(request.getUserId(), orgId, request.getRequiredPermission()))
                    .switchIfEmpty(Mono.just(AuthorizationResult.denied("Organization not found for owner")));
        }

        if (organizationId == null) {
            return Mono.just(AuthorizationResult.denied("Organization ID is required for authorization"));
        }

        return checkEventPermission(request.getUserId(), organizationId, request.getRequiredPermission());
    }

    @Override
    public Mono<AuthorizationResult> checkEventPermission(String userId, String organizationId, String permission) {
        log.debug("Checking event permission: userId={}, orgId={}, permission={}", userId, organizationId, permission);

        return memberService.findByUserAndOrganization(userId, organizationId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .flatMap(member -> {
                    OrganizationRole role = member.getRole();

                    // Check if role has the required permission
                    if (hasOrganizationPermission(role, permission)) {
                        Set<String> permissions = getPermissionsForRole(role);
                        // Add custom permissions, remove denied
                        if (member.getCustomPermissions() != null) {
                            permissions.addAll(member.getCustomPermissions());
                        }
                        if (member.getDeniedPermissions() != null) {
                            permissions.removeAll(member.getDeniedPermissions());
                        }

                        return Mono.just(AuthorizationResult.authorizedAsMember(
                                organizationId,
                                role.name(),
                                permissions
                        ));
                    }

                    return Mono.just(AuthorizationResult.deniedInsufficientPermissions(permission, role.name()));
                })
                .switchIfEmpty(Mono.just(AuthorizationResult.deniedNotMember()));
    }

    @Override
    public Mono<AuthorizationResult> checkEventAccess(String userId, String eventId, String organizationId, String permission) {
        log.debug("Checking event access: userId={}, eventId={}, orgId={}, permission={}",
                userId, eventId, organizationId, permission);

        // Step 1: Check EventAccessGrant first (overrides organization membership)
        return eventAccessService.findByUserAndEvent(userId, eventId)
                .filter(grant -> grant.getStatus() == com.pml.identity.domain.enums.AccessGrantStatus.ACTIVE)
                .flatMap(grant -> {
                    EventRole eventRole = grant.getEventRole();

                    if (hasEventPermission(eventRole, permission)) {
                        return Mono.just(AuthorizationResult.authorizedByEventGrant(eventId, eventRole.name()));
                    }

                    // Has event access but not the required permission
                    return Mono.just(AuthorizationResult.deniedInsufficientPermissions(permission, eventRole.name()));
                })
                // Step 2: Fall back to organization membership check
                .switchIfEmpty(Mono.defer(() -> {
                    if (organizationId != null) {
                        return checkEventPermission(userId, organizationId, permission);
                    }
                    return Mono.just(AuthorizationResult.denied("No event access grant and organization ID not provided"));
                }));
    }

    @Override
    public Mono<AuthorizationResult> checkMembership(String userId, String organizationId, String minimumRole) {
        log.debug("Checking membership: userId={}, orgId={}, minimumRole={}", userId, organizationId, minimumRole);

        return memberService.findByUserAndOrganization(userId, organizationId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .map(member -> {
                    OrganizationRole role = member.getRole();
                    OrganizationRole requiredRole = OrganizationRole.valueOf(minimumRole);

                    if (role.isAtLeast(requiredRole)) {
                        return AuthorizationResult.authorizedAsMember(
                                organizationId,
                                role.name(),
                                getPermissionsForRole(role)
                        );
                    }

                    return AuthorizationResult.deniedInsufficientPermissions(minimumRole, role.name());
                })
                .switchIfEmpty(Mono.just(AuthorizationResult.deniedNotMember()));
    }

    @Override
    public Mono<AuthorizationResult> checkOwnership(String userId, String organizationId) {
        log.debug("Checking ownership: userId={}, orgId={}", userId, organizationId);

        return organizationService.findById(organizationId)
                .map(org -> {
                    if (userId.equals(org.getOwnerId())) {
                        return AuthorizationResult.authorizedAsOwner(organizationId);
                    }
                    return AuthorizationResult.denied("User is not the owner of the organization");
                })
                .switchIfEmpty(Mono.just(AuthorizationResult.denied("Organization not found")));
    }

    @Override
    public Mono<Boolean> isOrganizationOwner(String userId, String organizationId) {
        log.debug("Checking if user {} is owner of organization {}", userId, organizationId);

        return organizationService.findById(organizationId)
                .map(org -> userId.equals(org.getOwnerId()))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<String> getDefaultOrganizationForUser(String userId) {
        log.debug("Getting default organization for user: {}", userId);

        // Find organization where user is owner first
        return organizationService.findByOwnerId(userId)
                .map(Organization::getId)
                // If not owner, find first organization where user can create events
                .switchIfEmpty(memberService.findActiveByUser(userId)
                        .filter(member -> hasOrganizationPermission(member.getRole(), "EVENT_CREATE"))
                        .next()
                        .map(OrganizationMember::getOrganizationId));
    }

    @Override
    public Mono<String> findOrganizationByOwnerId(String organizerId) {
        log.debug("Finding organization by owner ID: {}", organizerId);

        return organizationService.findByOwnerId(organizerId)
                .map(Organization::getId);
    }

    // ========================================================================
    // PERMISSION MAPPING
    // ========================================================================

    /**
     * Check if an organization role has a specific permission.
     */
    private boolean hasOrganizationPermission(OrganizationRole role, String permission) {
        return switch (permission) {
            // Event management
            case "EVENT_CREATE", "EVENT_PUBLISH" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER;

            case "EVENT_EDIT" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER;

            case "EVENT_DELETE" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN;

            case "EVENT_VIEW" ->
                    true; // All members can view

            // Financial operations
            case "FINANCIAL_VIEW" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER;

            case "PAYOUT_REQUEST" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN;

            // Team management
            case "MEMBER_INVITE", "MEMBER_REMOVE", "MEMBER_ROLE_CHANGE" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN;

            // Ticket operations
            case "TICKET_SCAN" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER ||
                    role == OrganizationRole.CONTRIBUTOR;

            // Marketing
            case "PROMOTION_MANAGE" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER ||
                    role == OrganizationRole.MARKETER;

            // Analytics
            case "ANALYTICS_VIEW" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN ||
                    role == OrganizationRole.MANAGER ||
                    role == OrganizationRole.MARKETER;

            // Organization management
            case "ORG_EDIT", "ORG_SETTINGS" ->
                    role == OrganizationRole.OWNER ||
                    role == OrganizationRole.ADMIN;

            case "ORG_DELETE", "OWNERSHIP_TRANSFER" ->
                    role == OrganizationRole.OWNER;

            default -> {
                log.warn("Unknown permission requested: {}", permission);
                yield false;
            }
        };
    }

    /**
     * Check if an event role has a specific permission.
     */
    private boolean hasEventPermission(EventRole role, String permission) {
        return switch (permission) {
            case "EVENT_EDIT" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN ||
                    role == EventRole.EDITOR;

            case "EVENT_DELETE" ->
                    role == EventRole.EVENT_OWNER;

            case "EVENT_PUBLISH" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN;

            case "EVENT_VIEW" ->
                    true; // All event roles can view

            case "TICKET_SCAN" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN ||
                    role == EventRole.EDITOR ||
                    role == EventRole.CHECK_IN;

            case "REFUND_ISSUE" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN;

            case "ATTENDEE_VIEW" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN ||
                    role == EventRole.EDITOR ||
                    role == EventRole.CHECK_IN;

            case "NOTIFICATION_SEND" ->
                    role == EventRole.EVENT_OWNER ||
                    role == EventRole.EVENT_ADMIN ||
                    role == EventRole.EDITOR;

            default -> {
                log.warn("Unknown event permission requested: {}", permission);
                yield false;
            }
        };
    }

    /**
     * Get all permissions for an organization role.
     */
    private Set<String> getPermissionsForRole(OrganizationRole role) {
        Set<String> permissions = new HashSet<>();

        // Add permissions based on role hierarchy
        switch (role) {
            case OWNER:
                permissions.add("ORG_DELETE");
                permissions.add("OWNERSHIP_TRANSFER");
                // Fall through
            case ADMIN:
                permissions.add("MEMBER_INVITE");
                permissions.add("MEMBER_REMOVE");
                permissions.add("MEMBER_ROLE_CHANGE");
                permissions.add("ORG_EDIT");
                permissions.add("ORG_SETTINGS");
                permissions.add("PAYOUT_REQUEST");
                permissions.add("EVENT_DELETE");
                // Fall through
            case MANAGER:
                permissions.add("EVENT_CREATE");
                permissions.add("EVENT_EDIT");
                permissions.add("EVENT_PUBLISH");
                permissions.add("FINANCIAL_VIEW");
                // Fall through
            case MARKETER:
                permissions.add("PROMOTION_MANAGE");
                permissions.add("ANALYTICS_VIEW");
                // Fall through
            case CONTRIBUTOR:
                permissions.add("EVENT_VIEW");
                permissions.add("TICKET_SCAN");
                break;
        }

        return permissions;
    }

    // ========================================================================
    // ORGANIZATION MEMBERSHIP METHODS (OWASP A01:2021 - Multi-tenant isolation)
    // ========================================================================

    @Override
    public Mono<MembershipCheckResponse> checkOrganizationMembership(String userId, String organizationId) {
        log.debug("Checking organization membership: userId={}, orgId={}", userId, organizationId);

        return memberService.findByUserAndOrganization(userId, organizationId)
                .map(member -> new MembershipCheckResponse(
                        true,
                        member.getStatus() == MemberStatus.ACTIVE,
                        member.getRole().name(),
                        organizationId
                ))
                .defaultIfEmpty(MembershipCheckResponse.notMember())
                .doOnSuccess(result -> log.debug("Membership check result: isMember={}, isActive={}, role={}",
                        result.isMember(), result.isActive(), result.role()));
    }

    @Override
    public Mono<SharedOrganizationResponse> checkSameOrganization(String requestingUserId, String targetOrganizerId) {
        log.debug("Checking same organization: requestingUserId={}, targetOrganizerId={}",
                requestingUserId, targetOrganizerId);

        // Same user - always allowed
        if (requestingUserId.equals(targetOrganizerId)) {
            return findOrganizationByOwnerId(targetOrganizerId)
                    .flatMap(orgId -> memberService.findByUserAndOrganization(requestingUserId, orgId)
                            .map(member -> new SharedOrganizationResponse(
                                    true,
                                    orgId,
                                    member.getRole().name(),
                                    member.getRole().name()
                            )))
                    .switchIfEmpty(Mono.just(new SharedOrganizationResponse(true, null, "SELF", "SELF")));
        }

        // Find organizations where requesting user is a member
        return memberService.findActiveByUser(requestingUserId)
                .flatMap(requestingMembership -> {
                    String orgId = requestingMembership.getOrganizationId();

                    // Check if target user is also a member of this organization
                    return memberService.findByUserAndOrganization(targetOrganizerId, orgId)
                            .filter(targetMember -> targetMember.getStatus() == MemberStatus.ACTIVE)
                            .map(targetMember -> new SharedOrganizationResponse(
                                    true,
                                    orgId,
                                    requestingMembership.getRole().name(),
                                    targetMember.getRole().name()
                            ));
                })
                .next()  // Take first matching organization
                .defaultIfEmpty(SharedOrganizationResponse.noSharedOrganization())
                .doOnSuccess(result -> log.debug("Same organization check result: shares={}, orgId={}",
                        result.sharesOrganization(), result.sharedOrganizationId()));
    }

    @Override
    public Flux<OrganizationMembershipInfo> getUserOrganizations(String userId) {
        log.debug("Getting user organizations: userId={}", userId);

        return memberService.findActiveByUser(userId)
                .flatMap(member -> organizationService.findById(member.getOrganizationId())
                        .map(org -> new OrganizationMembershipInfo(
                                org.getId(),
                                org.getName(),
                                member.getRole().name(),
                                member.getRole() == OrganizationRole.OWNER,
                                member.getStatus() == MemberStatus.ACTIVE
                        )));
    }
}
