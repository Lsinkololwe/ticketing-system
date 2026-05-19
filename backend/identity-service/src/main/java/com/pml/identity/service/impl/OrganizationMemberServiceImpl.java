package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.PermissionResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
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
    public Mono<Boolean> isMember(String userId, String organizationId) {
        return memberRepository.existsByUserIdAndOrganizationId(userId, organizationId);
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
    public Flux<OrganizationMember> findByOrganizationAndRole(
            String organizationId,
            OrganizationRole role,
            MemberStatus status,
            Pageable pageable) {
        if (status != null) {
            return memberRepository.findByOrganizationIdAndRoleAndStatus(organizationId, role, status);
        }
        return memberRepository.findByOrganizationIdAndRole(organizationId, role);
    }

    @Override
    public Flux<OrganizationMember> findActiveMembers(String organizationId, Pageable pageable) {
        return memberRepository.findByOrganizationIdAndStatus(organizationId, MemberStatus.ACTIVE, pageable);
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
    public Mono<Long> countMembers(String organizationId) {
        return memberRepository.countByOrganizationId(organizationId);
    }

    @Override
    public Mono<Long> countActiveMembers(String organizationId) {
        return memberRepository.countByOrganizationIdAndStatus(organizationId, MemberStatus.ACTIVE);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<OrganizationMember> createOwner(String organizationId, String userId) {
        log.info("Creating owner member for organization: {} with user: {}", organizationId, userId);

        return memberRepository.existsByUserIdAndOrganizationId(userId, organizationId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException("User is already a member of this organization"));
                    }

                    OrganizationMember owner = OrganizationMember.builder()
                            .userId(userId)
                            .organizationId(organizationId)
                            .role(OrganizationRole.OWNER)
                            .status(MemberStatus.ACTIVE)
                            .joinedAt(Instant.now())
                            .lastActiveAt(Instant.now())
                            .build();

                    return memberRepository.save(owner)
                            .flatMap(saved -> addToKeycloakGroup(saved).thenReturn(saved))
                            .doOnSuccess(saved -> log.info("Owner member created: {}", saved.getId()));
                });
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
                            .joinedAt(Instant.now())
                            .lastActiveAt(Instant.now())
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

                    member.setStatus(MemberStatus.REMOVED);
                    return memberRepository.save(member)
                            .flatMap(saved -> removeFromKeycloakGroup(saved).thenReturn(saved))
                            .flatMap(saved -> updateOrganizationMemberCount(saved.getOrganizationId()))
                            .then();
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

                    member.setStatus(MemberStatus.REMOVED);
                    return memberRepository.save(member)
                            .flatMap(saved -> removeFromKeycloakGroup(saved).thenReturn(saved))
                            .flatMap(saved -> updateOrganizationMemberCount(organizationId))
                            .then();
                });
    }

    @Override
    public Mono<OrganizationMember> transferOwnership(String organizationId, String newOwnerId) {
        log.info("Transferring ownership of organization {} to user {}", organizationId, newOwnerId);

        return findOwner(organizationId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organization owner not found")))
                .flatMap(currentOwner -> memberRepository.findByUserIdAndOrganizationId(newOwnerId, organizationId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("New owner must be an existing member")))
                        .flatMap(newOwnerMember -> {
                            // Demote current owner to ADMIN
                            currentOwner.setRole(OrganizationRole.ADMIN);

                            // Promote new owner
                            newOwnerMember.setRole(OrganizationRole.OWNER);

                            return memberRepository.save(currentOwner)
                                    .then(memberRepository.save(newOwnerMember))
                                    .flatMap(saved -> {
                                        // Update organization's ownerId
                                        return organizationRepository.findById(organizationId)
                                                .flatMap(org -> {
                                                    org.setOwnerId(newOwnerId);
                                                    return organizationRepository.save(org);
                                                })
                                                .thenReturn(saved);
                                    })
                                    .flatMap(saved -> {
                                        // Update Keycloak groups
                                        return updateKeycloakGroup(currentOwner, OrganizationRole.OWNER)
                                                .then(updateKeycloakGroup(saved, OrganizationRole.ADMIN))
                                                .thenReturn(saved);
                                    })
                                    .doOnSuccess(saved -> log.info("Ownership transferred successfully to: {}", newOwnerId));
                        }));
    }

    @Override
    public Mono<OrganizationMember> updateLastActive(String memberId) {
        return memberRepository.findById(memberId)
                .flatMap(member -> {
                    member.setLastActiveAt(Instant.now());
                    return memberRepository.save(member);
                });
    }

    // ========================================================================
    // PERMISSION OPERATIONS
    // ========================================================================

    @Override
    public Mono<Boolean> hasPermission(String userId, String organizationId, String permission) {
        return permissionResolutionService.hasOrganizationPermission(userId, organizationId, permission);
    }

    @Override
    public Mono<OrganizationRole> getUserRole(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .map(OrganizationMember::getRole);
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
                        member.getRole().name().toLowerCase()))
                .onErrorResume(e -> {
                    log.warn("Failed to add user to Keycloak group: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> removeFromKeycloakGroup(OrganizationMember member) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(org -> keycloakService.removeUserFromOrganizationGroup(
                        member.getUserId(),
                        org.getSlug(),
                        member.getRole().name().toLowerCase()))
                .onErrorResume(e -> {
                    log.warn("Failed to remove user from Keycloak group: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> updateKeycloakGroup(OrganizationMember member, OrganizationRole previousRole) {
        return organizationRepository.findById(member.getOrganizationId())
                .flatMap(org -> keycloakService.removeUserFromOrganizationGroup(
                                member.getUserId(),
                                org.getSlug(),
                                previousRole.name().toLowerCase())
                        .then(keycloakService.addUserToOrganizationGroup(
                                member.getUserId(),
                                org.getSlug(),
                                member.getRole().name().toLowerCase())))
                .onErrorResume(e -> {
                    log.warn("Failed to update Keycloak group: {}", e.getMessage());
                    return Mono.empty();
                });
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
