package com.pml.identity.service.impl;

import com.pml.shared.security.Permission;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Organization Member Service Implementation
 *
 * Manages team membership within organizations including:
 * - Adding/removing members
 * - Role management
 * - Permission checks
 * - Keycloak group mirroring (marked on the row, applied by the group-mirror workflow)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationMemberServiceImpl implements OrganizationMemberService {

    private final OrganizationMemberRepository memberRepository;
    private final com.pml.identity.service.OneOrganizationPerPerson oneOrganizationPerPerson;
    private final OrganizationRepository organizationRepository;
    private final PermissionResolutionService permissionResolutionService;

    /** The grants a removal must revoke. */
    private final EventAccessGrantRepository grantRepository;

    /** The status and the revocations are only meaningful together. */
    private final TransactionalOperator transactionalOperator;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<OrganizationMember> findById(String id) {
        return memberRepository.findById(id);
    }

    @Override
    public Mono<OrganizationMember> findByUserAndOrganization(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId);
    }

    @Override
    public Mono<Boolean> isActiveMember(String userId, String organizationId) {
        return memberRepository.existsByUserIdAndOrganizationIdAndStatus(userId, organizationId, MemberStatus.ACTIVE);
    }

    @Override
    public Flux<OrganizationMember> findByOrganization(String organizationId, Pageable pageable) {
        return memberRepository.findByOrganizationId(organizationId, pageable);
    }

    @Override
    public Flux<OrganizationMember> findByOrganization(String organizationId) {
        return memberRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Mono<OrganizationMember> findOwner(String organizationId) {
        return memberRepository.findOwnerByOrganizationId(organizationId);
    }

    @Override
    public Flux<OrganizationMember> findByUser(String userId) {
        return memberRepository.findByUserId(userId);
    }

    @Override
    public Flux<OrganizationMember> findActiveByUser(String userId) {
        return memberRepository.findByUserIdAndStatus(userId, MemberStatus.ACTIVE);
    }

    @Override
    public Mono<Long> countActiveMembers(String organizationId) {
        return memberRepository.countByOrganizationIdAndStatus(organizationId, MemberStatus.ACTIVE);
    }

    @Override
    public Mono<OrganizationMember> createFromInvitation(
            String organizationId,
            String userId,
            OrganizationRole role,
            String invitedById) {
        log.info("Creating member from invitation for organization: {} user: {} role: {}",
                organizationId, userId, role);

        return oneOrganizationPerPerson.require(userId, organizationId)
                .then(memberRepository.existsByUserIdAndOrganizationId(userId, organizationId))
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException("User is already a member of this organization"));
                    }

                    // Cannot create OWNER through invitation
                    if (role == OrganizationRole.OWNER) {
                        return Mono.error(new IllegalArgumentException("Cannot create OWNER through invitation"));
                    }

                    OrganizationMember member = OrganizationMember.builder()
                            .userId(userId)
                            .organizationId(organizationId)
                            .role(role)
                            .status(MemberStatus.ACTIVE)
                            .invitedById(invitedById)
                            .joinedAt(clock.instant())
                            .lastActiveAt(clock.instant())
                            .mirrorPending(true)
                            .build();

                    return memberRepository.save(member)
                            .flatMap(saved -> updateOrganizationMemberCount(organizationId).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Member created from invitation: {}", saved.getId()));
                });
    }

    @Override
    public Mono<OrganizationMember> updateRole(
            String memberId,
            OrganizationRole newRole,
            Set<String> customPermissions,
            Set<String> deniedPermissions) {
        // The resolver's reads.memberForCaller(memberId) already proved ownership before calling
        // this; reading the scope again here is defense in depth, same as endMembership() below.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        memberRepository.findById(memberId),
                        organizationIds -> memberRepository.findByIdAndOrganizationIdIn(memberId, organizationIds),
                        ErrorCode.MEMBER_UNKNOWN,
                        "organization member " + memberId))
                .flatMap(member -> {
                    // Cannot demote OWNER through this method
                    if (member.getRole() == OrganizationRole.OWNER && newRole != OrganizationRole.OWNER) {
                        return Mono.error(new IllegalStateException("Use ownership transfer to change owner"));
                    }
                    // Cannot promote to OWNER through this method
                    if (newRole == OrganizationRole.OWNER) {
                        return Mono.error(new IllegalStateException("Use ownership transfer to assign owner"));
                    }

                    OrganizationRole previousRole = member.getRole();
                    member.setRole(newRole);
                    member.setMirrorPending(true);

                    if (customPermissions != null) {
                        member.setCustomPermissions(customPermissions);
                    }
                    if (deniedPermissions != null) {
                        member.setDeniedPermissions(deniedPermissions);
                    }

                    return memberRepository.save(member)
                            .doOnSuccess(saved -> log.info("Member role updated: {} from {} to {}",
                                    saved.getId(), previousRole, newRole));
                });
    }

    @Override
    public Mono<OrganizationMember> updateStatus(String memberId, MemberStatus status) {
        // Reached only through suspend()/reactivate(), both called after the resolver's
        // reads.memberForCaller(memberId) already proved ownership; defense in depth here too.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        memberRepository.findById(memberId),
                        organizationIds -> memberRepository.findByIdAndOrganizationIdIn(memberId, organizationIds),
                        ErrorCode.MEMBER_UNKNOWN,
                        "organization member " + memberId))
                .flatMap(member -> {
                    // Cannot change owner status
                    if (member.getRole() == OrganizationRole.OWNER && status != MemberStatus.ACTIVE) {
                        return Mono.error(new IllegalStateException("Cannot change owner status"));
                    }

                    member.setStatus(status);
                    return memberRepository.save(member);
                });
    }

    @Override
    public Mono<OrganizationMember> suspend(String memberId, String reason) {
        log.info("Suspending member: {} - Reason: {}", memberId, reason);
        return updateStatus(memberId, MemberStatus.SUSPENDED);
    }

    @Override
    public Mono<OrganizationMember> reactivate(String memberId) {
        log.info("Reactivating member: {}", memberId);
        return updateStatus(memberId, MemberStatus.ACTIVE);
    }

    @Override
    public Mono<Void> remove(String memberId, String reason) {
        log.info("Removing member: {} - Reason: {}", memberId, reason);
        // The resolver's reads.memberForCaller(memberId) already proved ownership before calling
        // this; defense in depth here too.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        memberRepository.findById(memberId),
                        organizationIds -> memberRepository.findByIdAndOrganizationIdIn(memberId, organizationIds),
                        ErrorCode.MEMBER_UNKNOWN,
                        "organization member " + memberId))
                .flatMap(member -> {
                    // Cannot remove owner
                    if (member.getRole() == OrganizationRole.OWNER) {
                        return Mono.error(new IllegalStateException("Cannot remove owner"));
                    }

                    return endMembership(member).then();
                });
    }

    @Override
    public Mono<Void> leave(String userId, String organizationId) {
        log.info("User {} leaving organization {}", userId, organizationId);
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User is not a member of this organization")))
                .flatMap(member -> {
                    // Owner cannot leave - must transfer ownership first
                    if (member.getRole() == OrganizationRole.OWNER) {
                        return Mono.error(new IllegalStateException("Owner cannot leave. Transfer ownership first."));
                    }

                    return endMembership(member).then();
                });
    }

    /**
     * Removal is a status, and it takes event access with it.
     *
     * <h2>Why the grants must go in the same transaction</h2>
     * Organization membership and event access are two independent grants, and a member can hold
     * an event grant the organization role never gave them — a freelancer given EDITOR on one
     * festival, a scanner given access to one gate. Marking the membership {@code REMOVED} says
     * nothing about those rows.
     *
     * <p>A removed member must lose access on their next request. Were the grants left in place,
     * they could keep editing the event they were removed over, indefinitely, with the
     * organization's own team screen showing them gone.
     *
     * <p>The revocations and the status move are one transaction because the halves are only
     * meaningful together: a status without the revocations is the defect itself, and revocations
     * without the status remove somebody's access while leaving them on the team.
     *
     * <h2>Retained, not deleted</h2>
     * The record survives removal. A re-invited member gets a new row and the old one keeps
     * its {@code removedAt}, so "was removed in March and re-invited in June" stays legible —
     * which is the history a dispute is settled from.
     */
    private Mono<OrganizationMember> endMembership(OrganizationMember member) {
        Instant now = clock.instant();
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(now);
        member.setMirrorPending(true);

        return revokeEventGrants(member, now)
                .then(memberRepository.save(member))
                .as(transactionalOperator::transactional)
                // The member count is outside the boundary deliberately: it is not the source of truth.
                // Keycloak is not written here at all: the row carries the mirror marker, and the
                // group-mirror workflow applies it, so a Keycloak outage cannot block a removal.
                .flatMap(saved -> updateOrganizationMemberCount(saved.getOrganizationId())
                        .thenReturn(saved));
    }

    /**
     * Every grant this member holds for this organization's events, revoked.
     *
     * <p>Scoped to the organization rather than the user: a member removed from one organization
     * keeps whatever access they hold in another, and revoking by user alone would take it.
     */
    private Mono<Void> revokeEventGrants(OrganizationMember member, Instant now) {
        return grantRepository
                .findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .filter(grant -> grant.getStatus() == AccessGrantStatus.ACTIVE)
                .flatMap(grant -> {
                    grant.setStatus(AccessGrantStatus.REVOKED);
                    grant.setRevokedAt(now);
                    grant.setRevocationReason("Removed from organization");
                    return grantRepository.save(grant);
                })
                .then();
    }

    // ========================================================================
    // PERMISSION OPERATIONS
    // ========================================================================

    @Override
    public Mono<Boolean> hasPermission(String userId, String organizationId, Permission permission) {
        return permissionResolutionService.hasOrganizationPermission(userId, organizationId, permission);
    }

    @Override
    public Mono<Void> requirePermission(String userId, String organizationId, Permission permission) {
        return permissionResolutionService.requireOrganizationPermission(userId, organizationId, permission);
    }

    @Override
    public Mono<Boolean> canModifyMember(String actorUserId, String targetMemberId, String organizationId) {
        // The target is scoped to the same organization the actor belongs to: a target id from a
        // different organization must never be compared against the actor's role as if it were
        // one of their own teammates.
        return memberRepository.findByUserIdAndOrganizationId(actorUserId, organizationId)
                .flatMap(actor -> memberRepository.findByIdAndOrganizationIdIn(targetMemberId, Set.of(organizationId))
                        .map(target -> actor.canModifyMember(target)))
                .defaultIfEmpty(false);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<Void> updateOrganizationMemberCount(String organizationId) {
        return countActiveMembers(organizationId)
                .flatMap(count -> organizationRepository.findById(organizationId)
                        .flatMap(org -> {
                            if (org.getStats() == null) {
                                org.setStats(new com.pml.identity.domain.valueobject.OrganizationStats());
                            }
                            org.getStats().setMemberCount(count.intValue());
                            return organizationRepository.save(org);
                        }))
                .then();
    }
}
