package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.shared.security.Permission;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.service.AuthorizationService;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
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
 * Answers the permission checks catalog and booking make before acting for a user.
 *
 * <p>Permissions are catalogue codes ({@code com.pml.shared.security.Permission}); a name outside
 * the catalogue is refused. On an event, an active access grant from the event's own organization
 * decides on its own; otherwise the user's membership of the organization decides, under that
 * organization's settings and lifecycle status. The role sets themselves live on
 * {@code OrganizationRole} and {@code EventRole}.
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
    public Mono<AuthorizationResult> checkEventPermission(String userId, String organizationId, String permissionCode) {
        log.debug("Checking event permission: userId={}, orgId={}, permission={}", userId, organizationId, permissionCode);
        Permission permission = Permission.fromCode(permissionCode).orElse(null);
        if (permission == null) {
            return Mono.just(AuthorizationResult.denied("Unknown permission " + permissionCode));
        }

        // Two questions, both must pass: may this member do it (role, the organization's
        // settings, custom and denied permissions), and may this organization do it at all
        // (its lifecycle status — an unapproved organization cannot publish or request payouts).
        return memberService.findByUserAndOrganization(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .flatMap(member -> organizationService.findById(organizationId)
                        .map(org -> decide(member, org, permission))
                        .switchIfEmpty(Mono.just(AuthorizationResult.denied("Organization not found"))))
                .switchIfEmpty(Mono.just(AuthorizationResult.deniedNotMember()));
    }

    private static AuthorizationResult decide(OrganizationMember member, Organization org, Permission permission) {
        if (!member.hasPermission(permission, org.getSettings())) {
            return AuthorizationResult.deniedInsufficientPermissions(permission.code(), member.getRole().name());
        }
        if (!org.canPerform(permission)) {
            return AuthorizationResult.denied("Organization status " + org.getStatus() + " does not permit " + permission.code());
        }
        return AuthorizationResult.authorizedAsMember(org.getId(), member.getRole().name());
    }

    @Override
    public Mono<AuthorizationResult> checkEventAccess(String userId, String eventId, String organizationId, String permissionCode) {
        log.debug("Checking event access: userId={}, eventId={}, orgId={}, permission={}",
                userId, eventId, organizationId, permissionCode);
        Permission permission = Permission.fromCode(permissionCode).orElse(null);
        if (permission == null) {
            return Mono.just(AuthorizationResult.denied("Unknown permission " + permissionCode));
        }

        // An active grant on the event decides on its own; the holder's organization role is not
        // consulted. A grant only counts on an event of the organization that issued it, so a grant
        // naming another organization's event id authorizes nothing there.
        return eventAccessService.findByUserAndEvent(userId, eventId)
                .filter(grant -> grant.getStatus() == AccessGrantStatus.ACTIVE)
                .filter(grant -> organizationId == null || organizationId.equals(grant.getOrganizationId()))
                .map(grant -> grant.hasPermission(permission)
                        ? AuthorizationResult.authorizedByEventGrant(eventId, grant.getEventRole().name())
                        : AuthorizationResult.deniedInsufficientPermissions(permission.code(), grant.getEventRole().name()))
                .switchIfEmpty(Mono.defer(() -> organizationId != null
                        ? checkEventPermission(userId, organizationId, permissionCode)
                        : Mono.just(AuthorizationResult.denied("No event access grant and organization ID not provided"))));
    }

    @Override
    public Mono<Boolean> isOrganizationOwner(String userId, String organizationId) {
        log.debug("Checking if user {} is owner of organization {}", userId, organizationId);

        return organizationService.findById(organizationId)
                .map(org -> userId.equals(org.getOwnerId()))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<String> findOrganizationByOwnerId(String organizerId) {
        log.debug("Finding organization by owner ID: {}", organizerId);

        return organizationService.findByOwnerId(organizerId)
                .map(Organization::getId);
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
                                    orgId
                            )))
                    .switchIfEmpty(Mono.just(new SharedOrganizationResponse(true, null)));
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
                                    orgId
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
                                member.getRole().name(),
                                member.getStatus() == MemberStatus.ACTIVE
                        )));
    }
}
