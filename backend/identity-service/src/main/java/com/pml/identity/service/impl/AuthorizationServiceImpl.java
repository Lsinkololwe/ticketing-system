package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.shared.security.Permission;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.AuthorizationService;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.identity.service.PermissionResolutionService.Decision;
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
 * the catalogue is refused. The decision itself is made by {@link PermissionResolutionService};
 * this class only turns its outcome into the response the callers expect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final PermissionResolutionService resolution;
    private final OrganizationMemberService memberService;
    private final OrganizationService organizationService;

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
        // Platform roles are not consulted: this check answers for membership of the organization.
        return resolution.decideOrganization(userId, organizationId, permission, false)
                .map(decision -> toResult(decision, permission, null));
    }

    @Override
    public Mono<AuthorizationResult> checkEventAccess(String userId, String eventId, String organizationId, String permissionCode) {
        log.debug("Checking event access: userId={}, eventId={}, orgId={}, permission={}",
                userId, eventId, organizationId, permissionCode);
        Permission permission = Permission.fromCode(permissionCode).orElse(null);
        if (permission == null) {
            return Mono.just(AuthorizationResult.denied("Unknown permission " + permissionCode));
        }
        return resolution.decideEvent(userId, eventId, organizationId, permission, false)
                .map(decision -> toResult(decision, permission, eventId));
    }

    /** Words the resolution's decision in this endpoint's response shape and refusal texts. */
    private static AuthorizationResult toResult(Decision decision, Permission permission, String eventId) {
        return switch (decision.outcome()) {
            case EVENT_GRANT -> AuthorizationResult.authorizedByEventGrant(eventId, decision.role());
            case MEMBER -> AuthorizationResult.authorizedAsMember(decision.organizationId(), decision.role());
            case GRANT_LACKS_PERMISSION, MEMBER_LACKS_PERMISSION ->
                    AuthorizationResult.deniedInsufficientPermissions(permission.code(), decision.role());
            case ORGANIZATION_STATUS -> AuthorizationResult.denied(
                    "Organization status " + decision.organizationStatus() + " does not permit " + permission.code());
            case NOT_A_MEMBER -> AuthorizationResult.deniedNotMember();
            case ORGANIZATION_UNKNOWN -> AuthorizationResult.denied("Organization not found");
            case NO_ORGANIZATION -> AuthorizationResult.denied("No event access grant and organization ID not provided");
            // Anything else, including a platform-role outcome this endpoint never asks for, refuses.
            default -> AuthorizationResult.denied("Not authorized");
        };
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
    public Mono<String> getOrganizationName(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Mono.empty();
        }
        return organizationService.findById(organizationId).map(Organization::getName);
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
