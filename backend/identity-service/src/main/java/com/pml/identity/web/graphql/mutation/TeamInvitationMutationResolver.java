package com.pml.identity.web.graphql.mutation;

import com.pml.shared.security.Permission;
import com.pml.identity.security.IdentityTenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
import com.pml.identity.web.graphql.dto.organization.InviteMemberInput;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL Mutation Resolver for Team Invitation operations.
 */
@Slf4j


@DgsComponent
@Validated
@RequiredArgsConstructor
public class TeamInvitationMutationResolver {

    private final TeamInvitationService invitationService;
    private final IdentityTenantReads reads;
    private final OrganizationMemberService memberService;

    /**
     * Invite a team member.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> inviteTeamMember(
            @InputArgument String organizationId,
            @Valid @InputArgument InviteMemberInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(inviterId -> log.info("User {} inviting {} to organization {}", inviterId, input.email(), organizationId))
                .flatMap(inviterId -> memberService.requirePermission(inviterId, organizationId, Permission.TEAM_INVITE)
                        .then(Mono.defer(() -> {
                            List<TeamInvitation.EventAccessInput> eventGrants = null;
                            if (input.eventAccessGrants() != null) {
                                eventGrants = input.eventAccessGrants().stream()
                                        .map(g -> TeamInvitation.EventAccessInput.builder()
                                                .eventId(g.eventId())
                                                .role(g.role())
                                                .expiresAt(g.expiresAt())
                                                .build())
                                        .collect(Collectors.toList());
                            }

                            return invitationService.invite(
                                    organizationId,
                                    input.email(),
                                    input.phoneNumber(),
                                    input.inviteeName(),
                                    input.role(),
                                    input.message(),
                                    eventGrants,
                                    inviterId
                            );
                        })));
    }

    /**
     * Bulk invite team members.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Flux<TeamInvitation> bulkInviteTeamMembers(
            @InputArgument String organizationId,
            @Valid @InputArgument List<@Valid InviteMemberInput> invitations) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(inviterId -> log.info("User {} bulk inviting {} members to organization {}", inviterId, invitations.size(), organizationId))
                .flatMapMany(inviterId -> memberService.requirePermission(inviterId, organizationId, Permission.TEAM_INVITE)
                        .thenMany(Flux.defer(() -> {
                            List<TeamInvitationService.InviteRequest> requests = invitations.stream()
                                    .map(input -> {
                                        List<TeamInvitation.EventAccessInput> eventGrants = null;
                                        if (input.eventAccessGrants() != null) {
                                            eventGrants = input.eventAccessGrants().stream()
                                                    .map(g -> TeamInvitation.EventAccessInput.builder()
                                                            .eventId(g.eventId())
                                                            .role(g.role())
                                                            .expiresAt(g.expiresAt())
                                                            .build())
                                                    .collect(Collectors.toList());
                                        }
                                        return new TeamInvitationService.InviteRequest(
                                                input.email(),
                                                input.phoneNumber(),
                                                input.inviteeName(),
                                                input.role(),
                                                input.message(),
                                                eventGrants
                                        );
                                    })
                                    .collect(Collectors.toList());

                            return invitationService.bulkInvite(organizationId, requests, inviterId);
                        })));
    }

    /**
     * Resend invitation email.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> resendInvitation(@InputArgument String invitationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} resending invitation: {}", userId, invitationId))
                .flatMap(userId -> reads.invitationForCaller(invitationId)
                        .flatMap(invitation -> memberService.requirePermission(userId, invitation.getOrganizationId(), Permission.TEAM_INVITE)
                                .then(Mono.defer(() -> {
                                    return invitationService.resend(invitationId);
                                }))));
    }

    /**
     * Revoke invitation.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> revokeInvitation(@InputArgument String invitationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} revoking invitation: {}", userId, invitationId))
                .flatMap(userId -> reads.invitationForCaller(invitationId)
                        .flatMap(invitation -> memberService.requirePermission(userId, invitation.getOrganizationId(), Permission.TEAM_INVITE)
                                .then(Mono.defer(() -> {
                                    return invitationService.revoke(invitationId);
                                }))));
    }

    /**
     * Accept invitation (creates member).
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> acceptInvitation(@InputArgument String token) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} accepting invitation", userId))
                .flatMap(userId -> invitationService.accept(token, userId));
    }

    /**
     * Decline an invitation by its token.
     *
     * <p>Answers with a bare boolean. The token is a bearer credential that gets forwarded, so
     * echoing the invitation back would hand the invitee's email, phone number and name to whoever
     * holds the link; the acceptance page only needs to know the decline happened.</p>
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> declineInvitation(@InputArgument String token) {
        log.info("Declining invitation");
        return invitationService.decline(token).thenReturn(Boolean.TRUE);
    }
}
