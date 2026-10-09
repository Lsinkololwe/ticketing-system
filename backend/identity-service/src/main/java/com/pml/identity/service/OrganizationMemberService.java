package com.pml.identity.service;

import com.pml.shared.security.Permission;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Organization Member Service Interface
 *
 * Manages team membership within organizations.
 */
public interface OrganizationMemberService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find member by ID
     */
    Mono<OrganizationMember> findById(String id);

    /**
     * Find member by user ID and organization ID
     */
    Mono<OrganizationMember> findByUserAndOrganization(String userId, String organizationId);

    /**
     * Check if user is an active member of organization
     */
    Mono<Boolean> isActiveMember(String userId, String organizationId);

    /**
     * Find all members of an organization with pagination
     */
    Flux<OrganizationMember> findByOrganization(String organizationId, Pageable pageable);

    /**
     * Find all members of an organization
     */
    Flux<OrganizationMember> findByOrganization(String organizationId);

    /**
     * Find organization owner
     */
    Mono<OrganizationMember> findOwner(String organizationId);

    /**
     * Find all organizations a user is a member of
     */
    Flux<OrganizationMember> findByUser(String userId);

    /**
     * Find all active memberships for a user
     */
    Flux<OrganizationMember> findActiveByUser(String userId);

    /**
     * Count active members in organization
     */
    Mono<Long> countActiveMembers(String organizationId);

    /**
     * Create member from accepted invitation
     */
    Mono<OrganizationMember> createFromInvitation(
            String organizationId,
            String userId,
            OrganizationRole role,
            String invitedById
    );

    /**
     * Update member role
     */
    Mono<OrganizationMember> updateRole(
            String memberId,
            OrganizationRole newRole,
            Set<String> customPermissions,
            Set<String> deniedPermissions
    );

    /**
     * Update member status
     */
    Mono<OrganizationMember> updateStatus(String memberId, MemberStatus status);

    /**
     * Suspend member
     */
    Mono<OrganizationMember> suspend(String memberId, String reason);

    /**
     * Reactivate member
     */
    Mono<OrganizationMember> reactivate(String memberId);

    /**
     * Remove member from organization
     */
    Mono<Void> remove(String memberId, String reason);

    /**
     * Leave organization (self-removal)
     */
    Mono<Void> leave(String userId, String organizationId);

    // ─────────────────────────────────────────────────────────────────────
    // Permission Operations
    // ─────────────────────────────────────────────────────────────────────

    /** Whether the user holds {@code permission} in the organization. */
    Mono<Boolean> hasPermission(String userId, String organizationId, Permission permission);

    /** Completes when the user holds {@code permission} in the organization; otherwise refuses with {@code ACTOR_NOT_PERMITTED}. */
    Mono<Void> requirePermission(String userId, String organizationId, Permission permission);

    /**
     * Check if user can modify another member
     */
    Mono<Boolean> canModifyMember(String actorUserId, String targetMemberId, String organizationId);
}
