package com.pml.identity.service;

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
     * Check if user is a member of organization
     */
    Mono<Boolean> isMember(String userId, String organizationId);

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
     * Find members by organization and role
     */
    Flux<OrganizationMember> findByOrganizationAndRole(
            String organizationId,
            OrganizationRole role,
            MemberStatus status,
            Pageable pageable
    );

    /**
     * Find active members of an organization
     */
    Flux<OrganizationMember> findActiveMembers(String organizationId, Pageable pageable);

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
     * Count members in organization
     */
    Mono<Long> countMembers(String organizationId);

    /**
     * Count active members in organization
     */
    Mono<Long> countActiveMembers(String organizationId);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create owner member (internal - called when organization is created)
     */
    Mono<OrganizationMember> createOwner(String organizationId, String userId);

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

    /**
     * Transfer ownership from current owner to new owner
     */
    Mono<OrganizationMember> transferOwnership(String organizationId, String newOwnerId);

    /**
     * Update last active timestamp
     */
    Mono<OrganizationMember> updateLastActive(String memberId);

    // ─────────────────────────────────────────────────────────────────────
    // Permission Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user has permission in organization
     */
    Mono<Boolean> hasPermission(String userId, String organizationId, String permission);

    /**
     * Get user's role in organization
     */
    Mono<OrganizationRole> getUserRole(String userId, String organizationId);

    /**
     * Check if user can modify another member
     */
    Mono<Boolean> canModifyMember(String actorUserId, String targetMemberId, String organizationId);
}
