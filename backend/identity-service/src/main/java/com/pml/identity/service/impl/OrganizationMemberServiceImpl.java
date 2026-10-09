package com.pml.identity.service.impl;

import com.pml.shared.security.Permission;
import com.pml.identity.domain.valueobject.OrganizationGroups;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.service.PermissionResolutionService;
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
 * - Keycloak group synchronization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationMemberServiceImpl implements OrganizationMemberService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final KeycloakService keycloakService;
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

        return memberRepository.existsByUserIdAndOrganizationId(userId, organizationId)
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
                            .build();

                    return memberRepository.save(member)
                            .flatMap(saved -> addToKeycloakGroup(saved).thenReturn(saved))
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
        return memberRepository.findById(memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found: " + memberId)))
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

                    if (customPermissions != null) {
                        member.setCustomPermissions(customPermissions);
                    }
                    if (deniedPermissions != null) {
                        member.setDeniedPermissions(deniedPermissions);
                    }

                    return memberRepository.save(member)
                            .flatMap(saved -> updateKeycloakGroup(saved, previousRole).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Member role updated: {} from {} to {}",
                                    saved.getId(), previousRole, newRole));
                });
    }

    @Override
    public Mono<OrganizationMember> updateStatus(String memberId, MemberStatus status) {
        return memberRepository.findById(memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found: " + memberId)))
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
        return memberRepository.findById(memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found: " + memberId)))
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

        return revokeEventGrants(member, now)
                .then(memberRepository.save(member))
                .as(transactionalOperator::transactional)
                // Keycloak and the member count are outside the boundary deliberately: neither is
                // the platform's source of truth, and a Keycloak outage must not prevent a removal.
                // The group-mirror drift sweep is what repairs the mirror.
                .flatMap(saved -> removeFromKeycloakGroup(saved).thenReturn(saved))
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
        return memberRepository.findByUserIdAndOrganizationId(actorUserId, organizationId)
                .flatMap(actor -> memberRepository.findById(targetMemberId)
                        .map(target -> actor.canModifyMember(target)))
                .defaultIfEmpty(false);
    }

    // ========================================================================
    // KEYCLOAK INTEGRATION
    // ========================================================================

    private Mono<Void> addToKeycloakGroup(OrganizationMember member) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(org -> keycloakService.addUserToOrganizationGroup(
                        member.getUserId(),
                        org.getSlug(),
                        OrganizationGroups.of(member.getRole())))
                .onErrorResume(e -> markMirrorPending(member, "Failed to add user to Keycloak group", e));
    }

    private Mono<Void> removeFromKeycloakGroup(OrganizationMember member) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(org -> keycloakService.removeUserFromOrganizationGroup(
                        member.getUserId(),
                        org.getSlug(),
                        OrganizationGroups.of(member.getRole())))
                .onErrorResume(e -> markMirrorPending(member, "Failed to remove user from Keycloak group", e));
    }

    private Mono<Void> updateKeycloakGroup(OrganizationMember member, OrganizationRole previousRole) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(org -> keycloakService.removeUserFromOrganizationGroup(
                                member.getUserId(),
                                org.getSlug(),
                                OrganizationGroups.of(previousRole))
                        .then(keycloakService.addUserToOrganizationGroup(
                                member.getUserId(),
                                org.getSlug(),
                                OrganizationGroups.of(member.getRole()))))
                .onErrorResume(e -> markMirrorPending(member, "Failed to update Keycloak group", e));
    }

    /**
     * Records that Keycloak is behind this membership, and lets the mutation succeed.
     *
     * <h2>The failure must not propagate, and must not vanish either</h2>
     * The change completes anyway: Keycloak mirrors membership rather than owning
     * it, and an organizer removing somebody cannot be blocked by a third party being down — that
     * is precisely the operation you least want blocked.
     *
     * <p>Swallowing the error to a log line achieves the first half and loses the second. The
     * drift is then real, invisible, and unrepairable except by reconciling every member on the
     * platform. Marking the row is what makes the sweep's work finite.
     *
     * <p>Marking is itself best-effort. If MongoDB is unreachable too there is nothing further to
     * do, and failing the mutation at that point would surface a Keycloak outage as a membership
     * error — exactly the coupling this method exists to prevent.
     */
    private Mono<Void> markMirrorPending(OrganizationMember member, String what, Throwable cause) {
        log.warn("securityIncident=false {} for user {} in organization {}: {} — membership "
                        + "committed, mirror marked pending",
                what, member.getUserId(), member.getOrganizationId(), cause.getMessage());

        return memberRepository.findById(member.getId())
                .flatMap(current -> {
                    current.setMirrorPending(true);
                    return memberRepository.save(current);
                })
                .onErrorResume(unreachable -> {
                    log.error("Could not mark mirrorPending for member {}: {}",
                            member.getId(), unreachable.getMessage());
                    return Mono.empty();
                })
                .then();
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
