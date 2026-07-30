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

        // Authorization is two entity-level questions, both must pass:
        //   1. Can the ACTOR do this?  -> OrganizationMember (role + custom/denied permissions)
        //   2. Can the ORGANIZATION do this?  -> Organization (lifecycle status)
        // Neither rule lives in this service; it just asks the entities and combines them.
        return memberService.findByUserAndOrganization(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .flatMap(member -> {
                    // (1) Actor check — the member's authority within the organization.
                    if (!member.hasPermission(permission)) {
                        return Mono.just(AuthorizationResult.deniedInsufficientPermissions(
                                permission, member.getRole().name()));
                    }

                    // (2) Organization check — the lifecycle status must permit the action.
                    // The ORGANIZER realm role is granted at registration, so it does not imply
                    // "approved"; privileged actions (publish, payout) are gated here on the
                    // backend, the single source of truth (OWASP A01:2021).
                    return organizationService.findById(organizationId)
                            .flatMap(org -> {
                                if (!org.canPerform(permission)) {
                                    return Mono.just(AuthorizationResult.denied(
                                            "Organization status " + org.getStatus()
                                                    + " does not permit " + permission));
                                }
                                return Mono.just(AuthorizationResult.authorizedAsMember(
                                        organizationId,
                                        member.getRole().name(),
                                        member.effectivePermissions()
                                ));
                            })
                            .switchIfEmpty(Mono.just(AuthorizationResult.denied("Organization not found")));
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

                    // Actor check on the grant entity (custom permissions + event-role defaults).
                    if (grant.hasPermission(permission)) {
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
                                role.permissions()
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
                        .filter(member -> member.hasPermission("EVENT_CREATE"))
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
