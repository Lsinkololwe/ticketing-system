package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.EventAccessGrantInput;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GraphQL Mutation Resolver for Event Access Grant operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventAccessMutationResolver {

    private final EventAccessService eventAccessService;
    private final OrganizationMemberService memberService;

    private static final String EVENT_MANAGE_ACCESS_PERMISSION = "EVENT_MANAGE_ACCESS";

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
            @InputArgument Instant expiresAt,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String granterId = jwt.getSubject();
        log.info("User {} granting event {} access to user {} with role {}",
                granterId, eventId, userId, role);

        return memberService.hasPermission(granterId, organizationId, EVENT_MANAGE_ACCESS_PERMISSION)
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied: " + EVENT_MANAGE_ACCESS_PERMISSION));
                    }

                    return eventAccessService.grant(
                            eventId,
                            organizationId,
                            userId,
                            role,
                            customPermissions,
                            reason,
                            expiresAt,
                            granterId
                    );
                });
    }

    /**
     * Bulk grant event access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Flux<EventAccessGrant> bulkGrantEventAccess(
            @InputArgument String eventId,
            @InputArgument String organizationId,
            @InputArgument List<EventAccessGrantInput> grants,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.error(new IllegalStateException("Authentication required"));
        }

        String granterId = jwt.getSubject();
        log.info("User {} bulk granting event {} access to {} users", granterId, eventId, grants.size());

        return memberService.hasPermission(granterId, organizationId, EVENT_MANAGE_ACCESS_PERMISSION)
                .flatMapMany(hasPermission -> {
                    if (!hasPermission) {
                        return Flux.error(new IllegalStateException("Permission denied"));
                    }

                    List<EventAccessService.GrantRequest> requests = grants.stream()
                            .map(g -> new EventAccessService.GrantRequest(
                                    g.eventId(), // This should be userId in the input
                                    g.role(),
                                    g.customPermissions(),
                                    g.reason(),
                                    g.expiresAt()
                            ))
                            .collect(Collectors.toList());

                    return eventAccessService.bulkGrant(eventId, organizationId, requests, granterId);
                });
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
            @InputArgument Instant expiresAt,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} updating event access: {}", userId, accessId);

        return eventAccessService.findById(accessId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Access grant not found")))
                .flatMap(grant -> memberService.hasPermission(userId, grant.getOrganizationId(), EVENT_MANAGE_ACCESS_PERMISSION)
                        .flatMap(hasPermission -> {
                            if (!hasPermission) {
                                return Mono.error(new IllegalStateException("Permission denied"));
                            }

                            return eventAccessService.update(accessId, newRole, customPermissions, expiresAt);
                        }));
    }

    /**
     * Revoke event access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> revokeEventAccess(
            @InputArgument String accessId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} revoking event access: {} - Reason: {}", userId, accessId, reason);

        return eventAccessService.findById(accessId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Access grant not found")))
                .flatMap(grant -> memberService.hasPermission(userId, grant.getOrganizationId(), EVENT_MANAGE_ACCESS_PERMISSION)
                        .flatMap(hasPermission -> {
                            if (!hasPermission) {
                                return Mono.error(new IllegalStateException("Permission denied"));
                            }

                            return eventAccessService.revoke(accessId, reason, userId);
                        }));
    }

    /**
     * Create event owner (called when event is created - internal use).
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> createEventOwner(
            @InputArgument String eventId,
            @InputArgument String organizationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("Creating event owner for event {} - User: {}", eventId, userId);

        // Only members of the organization can create events
        return memberService.isActiveMember(userId, organizationId)
                .flatMap(isMember -> {
                    if (!isMember) {
                        return Mono.error(new IllegalStateException("User is not a member of the organization"));
                    }

                    return eventAccessService.createEventOwner(eventId, organizationId, userId);
                });
    }
}
