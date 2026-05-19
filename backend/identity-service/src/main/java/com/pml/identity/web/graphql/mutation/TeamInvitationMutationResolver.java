package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.InviteMemberInput;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL Mutation Resolver for Team Invitation operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TeamInvitationMutationResolver {

    private final TeamInvitationService invitationService;
    private final OrganizationMemberService memberService;

    private static final String MEMBER_INVITE_PERMISSION = "MEMBER_INVITE";

    /**
     * Invite a team member.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> inviteTeamMember(
            @InputArgument String organizationId,
            @InputArgument InviteMemberInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String inviterId = jwt.getSubject();
        log.info("User {} inviting {} to organization {}", inviterId, input.email(), organizationId);

        return memberService.hasPermission(inviterId, organizationId, MEMBER_INVITE_PERMISSION)
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied: " + MEMBER_INVITE_PERMISSION));
                    }

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
                });
    }

    /**
     * Bulk invite team members.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Flux<TeamInvitation> bulkInviteTeamMembers(
            @InputArgument String organizationId,
            @InputArgument List<InviteMemberInput> invitations,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.error(new IllegalStateException("Authentication required"));
        }

        String inviterId = jwt.getSubject();
        log.info("User {} bulk inviting {} members to organization {}", inviterId, invitations.size(), organizationId);

        return memberService.hasPermission(inviterId, organizationId, MEMBER_INVITE_PERMISSION)
                .flatMapMany(hasPermission -> {
                    if (!hasPermission) {
                        return Flux.error(new IllegalStateException("Permission denied: " + MEMBER_INVITE_PERMISSION));
                    }

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
                });
    }

    /**
     * Resend invitation email.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> resendInvitation(
            @InputArgument String invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} resending invitation: {}", userId, invitationId);

        return invitationService.findById(invitationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invitation not found")))
                .flatMap(invitation -> memberService.hasPermission(userId, invitation.getOrganizationId(), MEMBER_INVITE_PERMISSION)
                        .flatMap(hasPermission -> {
                            if (!hasPermission) {
                                return Mono.error(new IllegalStateException("Permission denied"));
                            }
                            return invitationService.resend(invitationId);
                        }));
    }

    /**
     * Revoke invitation.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitation> revokeInvitation(
            @InputArgument String invitationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} revoking invitation: {}", userId, invitationId);

        return invitationService.findById(invitationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invitation not found")))
                .flatMap(invitation -> memberService.hasPermission(userId, invitation.getOrganizationId(), MEMBER_INVITE_PERMISSION)
                        .flatMap(hasPermission -> {
                            if (!hasPermission) {
                                return Mono.error(new IllegalStateException("Permission denied"));
                            }
                            return invitationService.revoke(invitationId);
                        }));
    }

    /**
     * Accept invitation (creates member).
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> acceptInvitation(
            @InputArgument String token,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} accepting invitation", userId);

        return invitationService.accept(token, userId);
    }

    /**
     * Decline invitation.
     */
    @DgsMutation
    public Mono<TeamInvitation> declineInvitation(@InputArgument String token) {
        log.info("Declining invitation");
        return invitationService.decline(token);
    }
}
