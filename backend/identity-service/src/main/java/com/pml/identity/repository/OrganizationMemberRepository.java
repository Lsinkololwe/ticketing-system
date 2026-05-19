package com.pml.identity.repository;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Organization Member Repository
 */
@Repository
public interface OrganizationMemberRepository extends ReactiveMongoRepository<OrganizationMember, String> {

    /**
     * Find member by user ID and organization ID (unique combination)
     */
    Mono<OrganizationMember> findByUserIdAndOrganizationId(String userId, String organizationId);

    /**
     * Check if user is member of organization
     */
    Mono<Boolean> existsByUserIdAndOrganizationId(String userId, String organizationId);

    /**
     * Check if user is active member of organization
     */
    Mono<Boolean> existsByUserIdAndOrganizationIdAndStatus(String userId, String organizationId, MemberStatus status);

    /**
     * Find all members of an organization
     */
    Flux<OrganizationMember> findByOrganizationId(String organizationId);

    /**
     * Find all members of an organization with pagination
     */
    Flux<OrganizationMember> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Find all active members of an organization
     */
    Flux<OrganizationMember> findByOrganizationIdAndStatus(String organizationId, MemberStatus status);

    /**
     * Find all active members with pagination
     */
    Flux<OrganizationMember> findByOrganizationIdAndStatus(String organizationId, MemberStatus status, Pageable pageable);

    /**
     * Find all members with a specific role
     */
    Flux<OrganizationMember> findByOrganizationIdAndRole(String organizationId, OrganizationRole role);

    /**
     * Find all members with a specific role and status
     */
    Flux<OrganizationMember> findByOrganizationIdAndRoleAndStatus(
            String organizationId,
            OrganizationRole role,
            MemberStatus status
    );

    /**
     * Find single member by organization and role (use for OWNER lookup)
     */
    Mono<OrganizationMember> findFirstByOrganizationIdAndRole(String organizationId, OrganizationRole role);

    /**
     * Find all organizations a user is a member of
     */
    Flux<OrganizationMember> findByUserId(String userId);

    /**
     * Find all active memberships for a user
     */
    Flux<OrganizationMember> findByUserIdAndStatus(String userId, MemberStatus status);

    /**
     * Count members in an organization
     */
    Mono<Long> countByOrganizationId(String organizationId);

    /**
     * Count active members in an organization
     */
    Mono<Long> countByOrganizationIdAndStatus(String organizationId, MemberStatus status);

    /**
     * Count members by role
     */
    Mono<Long> countByOrganizationIdAndRole(String organizationId, OrganizationRole role);

    /**
     * Delete all members of an organization
     */
    Mono<Void> deleteByOrganizationId(String organizationId);

    /**
     * Find the organization owner
     */
    @Query("{ 'organizationId': ?0, 'role': 'OWNER' }")
    Mono<OrganizationMember> findOwnerByOrganizationId(String organizationId);
}
