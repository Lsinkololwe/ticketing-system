package com.pml.identity.web.graphql.mutation;

import com.pml.shared.security.Permission;
import com.pml.identity.security.IdentityTenantReads;
import com.pml.identity.web.graphql.dto.organization.BulkEventAccessGrantInput;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.PermissionResolutionService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

/**
 * GraphQL Mutation Resolver for Event Access Grant operations.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class EventAccessMutationResolver {

    private final EventAccessService eventAccessService;
    private final IdentityTenantReads reads;
    private final OrganizationMemberService memberService;
    private final PermissionResolutionService permissions;

    /**
     * Grant event access to a user.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> grantEventAccess(
            @InputArgument String eventId,
            @InputArgument String organizationId,
            @InputArgument String userId,
            @InputArgument EventRole role,
            @InputArgument Set<String> customPermissions,
            @InputArgument String reason,
            @InputArgument Instant expiresAt) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(granterId -> log.info("User {} granting event {} access to user {} with role {}",
                        granterId, eventId, userId, role))
                .flatMap(granterId -> memberService.requirePermission(granterId, organizationId, Permission.EVENT_ACCESS_GRANT)
                        .then(Mono.defer(() -> permissions.requireDelegable(granterId, organizationId, customPermissions, Permission.Scope.EVENT)))
                        .then(Mono.defer(() -> eventAccessService.grant( eventId, organizationId, userId, role, customPermissions, reason, expiresAt, granterId ))));
    }

    /**
     * Bulk grant event access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Flux<EventAccessGrant> bulkGrantEventAccess(
            @InputArgument String eventId,
            @InputArgument String organizationId,
            @Valid @InputArgument List<@Valid BulkEventAccessGrantInput> grants) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(granterId -> log.info("User {} bulk granting event {} access to {} users", granterId, eventId, grants.size()))
                .flatMapMany(granterId -> memberService.requirePermission(granterId, organizationId, Permission.EVENT_ACCESS_GRANT)
                        .then(Mono.defer(() -> permissions.requireDelegable(granterId, organizationId,
                                grants.stream().filter(g -> g.customPermissions() != null)
                                        .flatMap(g -> g.customPermissions().stream()).collect(Collectors.toSet()),
                                Permission.Scope.EVENT)))
                        .thenMany(Flux.defer(() -> {
                            List<EventAccessService.GrantRequest> requests = grants.stream()
                                    .map(g -> new EventAccessService.GrantRequest(
                                            g.userId(),
                                            g.role(),
                                            g.customPermissions(),
                                            g.reason(),
                                            g.expiresAt()
                                    ))
                                    .collect(Collectors.toList());

                            return eventAccessService.bulkGrant(eventId, organizationId, requests, granterId);
                        })));
    }

    /**
     * Update event access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> updateEventAccess(
            @InputArgument String accessId,
            @InputArgument EventRole newRole,
            @InputArgument Set<String> customPermissions,
            @InputArgument Instant expiresAt) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} updating event access: {}", userId, accessId))
                .flatMap(userId -> reads.grantForCaller(accessId)
                        .flatMap(grant -> memberService.requirePermission(userId, grant.getOrganizationId(), Permission.EVENT_ACCESS_GRANT)
                                .then(Mono.defer(() -> permissions.requireDelegable(userId, grant.getOrganizationId(), customPermissions, Permission.Scope.EVENT)))
                                .then(Mono.defer(() -> eventAccessService.update(accessId, newRole, customPermissions, expiresAt)))));
    }

    /**
     * Revoke event access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> revokeEventAccess(
            @InputArgument String accessId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} revoking event access: {} - Reason: {}", userId, accessId, reason))
                .flatMap(userId -> reads.grantForCaller(accessId)
                        .flatMap(grant -> memberService.requirePermission(userId, grant.getOrganizationId(), Permission.EVENT_ACCESS_GRANT)
                                .then(Mono.defer(() -> eventAccessService.revoke(accessId, reason, userId)))));
    }
}
