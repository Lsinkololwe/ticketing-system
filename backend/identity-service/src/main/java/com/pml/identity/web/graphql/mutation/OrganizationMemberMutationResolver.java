package com.pml.identity.web.graphql.mutation;

import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import com.pml.identity.security.IdentityTenantReads;
import com.pml.identity.web.graphql.dto.organization.UpdateMemberRoleInput;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.PermissionResolutionService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import com.pml.shared.security.revocation.FailClosedOnRevocation;

/**
 * GraphQL Mutation Resolver for Organization Member operations.
 */
@Slf4j


@DgsComponent
@Validated
@RequiredArgsConstructor
public class OrganizationMemberMutationResolver {

    private final OrganizationMemberService memberService;
    private final IdentityTenantReads reads;
    private final PermissionResolutionService permissions;

    /**
     * Update member role and permissions.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.updateMemberRole")
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> updateMemberRole(
            @InputArgument String memberId,
            @Valid @InputArgument UpdateMemberRoleInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} updating member role: {}", actorUserId, memberId))
                .flatMap(actorUserId -> reads.memberForCaller(memberId)
                        .flatMap(member -> memberService.requirePermission(actorUserId, member.getOrganizationId(), Permission.TEAM_ROLE)
                                .then(Mono.defer(() -> permissions.requireDelegable(actorUserId, member.getOrganizationId(),
                                        union(input.customPermissions(), input.deniedPermissions()), Permission.Scope.ORGANIZATION)))
                                .then(Mono.defer(() -> {
                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "Cannot modify this member's role"));
                                                }

                                                return memberService.updateRole(
                                                        memberId,
                                                        input.newRole(),
                                                        input.customPermissions(),
                                                        input.deniedPermissions()
                                                );
                                            });
                                }))));
    }

    /**
     * Suspend a member.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.suspendMember")
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> suspendMember(
            @InputArgument String memberId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} suspending member: {} - Reason: {}", actorUserId, memberId, reason))
                .flatMap(actorUserId -> reads.memberForCaller(memberId)
                        .flatMap(member -> memberService.requirePermission(actorUserId, member.getOrganizationId(), Permission.TEAM_REMOVE)
                                .then(Mono.defer(() -> {
                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "Cannot suspend this member"));
                                                }

                                                return memberService.suspend(memberId, reason);
                                            });
                                }))));
    }

    /**
     * Reactivate a suspended member.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.reactivateMember")
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> reactivateMember(@InputArgument String memberId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} reactivating member: {}", actorUserId, memberId))
                .flatMap(actorUserId -> reads.memberForCaller(memberId)
                        .flatMap(member -> memberService.requirePermission(actorUserId, member.getOrganizationId(), Permission.TEAM_REMOVE)
                                .then(Mono.defer(() -> {
                                    return memberService.reactivate(memberId);
                                }))));
    }

    /**
     * Remove member from organization.
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.removeMember")
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> removeMember(
            @InputArgument String memberId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorUserId -> log.info("User {} removing member: {} - Reason: {}", actorUserId, memberId, reason))
                .flatMap(actorUserId -> reads.memberForCaller(memberId)
                        .flatMap(member -> memberService.requirePermission(actorUserId, member.getOrganizationId(), Permission.TEAM_REMOVE)
                                .then(Mono.defer(() -> {
                                    return memberService.canModifyMember(actorUserId, memberId, member.getOrganizationId())
                                            .flatMap(canModify -> {
                                                if (!canModify) {
                                                    return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED, "Cannot remove this member"));
                                                }

                                                return memberService.remove(memberId, reason).thenReturn(true);
                                            });
                                }))));
    }

    /**
     * Leave organization (self-removal).
     */
    @DgsMutation
    @FailClosedOnRevocation("organizer.leaveOrganization")
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> leaveOrganization(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} leaving organization: {}", userId, organizationId))
                .flatMap(userId -> memberService.leave(userId, organizationId).thenReturn(true));
    }


    /**
     * Custom and denied codes together: both must name catalogue permissions the actor holds, so
     * nobody grants a permission they lack or withholds one they could not have granted.
     */
    private static java.util.Set<String> union(java.util.Set<String> custom, java.util.Set<String> denied) {
        java.util.Set<String> all = new java.util.HashSet<>();
        if (custom != null) all.addAll(custom);
        if (denied != null) all.addAll(denied);
        return all;
    }
}
