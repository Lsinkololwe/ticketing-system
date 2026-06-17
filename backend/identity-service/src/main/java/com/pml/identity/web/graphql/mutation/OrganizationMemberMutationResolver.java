package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.UpdateMemberRoleInput;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.service.OrganizationMemberService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Organization Member operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationMemberMutationResolver {

    private final OrganizationMemberService memberService;

    private static final String MEMBER_EDIT_ROLE_PERMISSION = "MEMBER_EDIT_ROLE";
    private static final String MEMBER_REMOVE_PERMISSION = "MEMBER_REMOVE";

    /**
     * Update member role and permissions.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> updateMemberRole(
            @InputArgument String memberId,
            @InputArgument UpdateMemberRoleInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} updating member role: {}", actorUserId, memberId))
                .flatMap(actorUserId -> memberService.findById(memberId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found")))
                        .flatMap(member -> memberService.hasPermission(actorUserId, member.getOrganizationId(), MEMBER_EDIT_ROLE_PERMISSION)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new IllegalStateException("Permission denied"));
                                    }

                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new IllegalStateException(
                                                            "Cannot modify this member's role"));
                                                }

                                                return memberService.updateRole(
                                                        memberId,
                                                        input.role(),
                                                        input.customPermissions(),
                                                        input.deniedPermissions()
                                                );
                                            });
                                })));
    }

    /**
     * Suspend a member.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> suspendMember(
            @InputArgument String memberId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} suspending member: {} - Reason: {}", actorUserId, memberId, reason))
                .flatMap(actorUserId -> memberService.findById(memberId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found")))
                        .flatMap(member -> memberService.hasPermission(actorUserId, member.getOrganizationId(), MEMBER_REMOVE_PERMISSION)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new IllegalStateException("Permission denied"));
                                    }

                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new IllegalStateException("Cannot suspend this member"));
                                                }

                                                return memberService.suspend(memberId, reason);
                                            });
                                })));
    }

    /**
     * Reactivate a suspended member.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> reactivateMember(@InputArgument String memberId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} reactivating member: {}", actorUserId, memberId))
                .flatMap(actorUserId -> memberService.findById(memberId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found")))
                        .flatMap(member -> memberService.hasPermission(actorUserId, member.getOrganizationId(), MEMBER_REMOVE_PERMISSION)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new IllegalStateException("Permission denied"));
                                    }

                                    return memberService.reactivate(memberId);
                                })));
    }

    /**
     * Remove member from organization.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> removeMember(
            @InputArgument String memberId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} removing member: {} - Reason: {}", actorUserId, memberId, reason))
                .flatMap(actorUserId -> memberService.findById(memberId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found")))
                        .flatMap(member -> memberService.hasPermission(actorUserId, member.getOrganizationId(), MEMBER_REMOVE_PERMISSION)
                                .flatMap(hasPermission -> {
                                    if (!hasPermission) {
                                        return Mono.error(new IllegalStateException("Permission denied"));
                                    }

                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new IllegalStateException("Cannot remove this member"));
                                                }

                                                return memberService.remove(memberId, reason).thenReturn(true);
                                            });
                                })));
    }

    /**
     * Leave organization (self-removal).
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> leaveOrganization(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} leaving organization: {}", userId, organizationId))
                .flatMap(userId -> memberService.leave(userId, organizationId).thenReturn(true));
    }

    /**
     * Transfer organization ownership.
     * Only the current owner can do this.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> transferOrganizationOwnership(
            @InputArgument String organizationId,
            @InputArgument String newOwnerId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(currentUserId -> log.info("User {} transferring ownership of organization {} to user {}",
                        currentUserId, organizationId, newOwnerId))
                .flatMap(currentUserId -> memberService.findOwner(organizationId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Organization owner not found")))
                        .flatMap(owner -> {
                            if (!owner.getUserId().equals(currentUserId)) {
                                return Mono.error(new IllegalStateException("Only the owner can transfer ownership"));
                            }

                            return memberService.transferOwnership(organizationId, newOwnerId);
                        }));
    }
}
