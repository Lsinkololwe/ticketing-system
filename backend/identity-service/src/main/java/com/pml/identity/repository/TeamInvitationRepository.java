package com.pml.identity.repository;

import com.pml.identity.domain.enums.InvitationStatus;
import com.pml.identity.domain.model.TeamInvitation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Team Invitation Repository
 */
@Repository
public interface TeamInvitationRepository extends ReactiveMongoRepository<TeamInvitation, String> {

    /**
     * Find invitation by unique token
     */
    Mono<TeamInvitation> findByInvitationToken(String invitationToken);

    /**
     * Check if invitation token exists
     */
    Mono<Boolean> existsByInvitationToken(String invitationToken);

    /**
     * Find invitation by email and organization
     */
    Mono<TeamInvitation> findByEmailAndOrganizationId(String email, String organizationId);

    /**
     * Find pending invitation by email and organization
     */
    Mono<TeamInvitation> findByEmailAndOrganizationIdAndStatus(
            String email,
            String organizationId,
            InvitationStatus status
    );

    /**
     * Find all invitations for an organization
     */
    Flux<TeamInvitation> findByOrganizationId(String organizationId);

    /**
     * Find all invitations for an organization with pagination
     */
    Flux<TeamInvitation> findByOrganizationId(String organizationId, Pageable pageable);

    /**
     * Find invitations by organization and status
     */
    Flux<TeamInvitation> findByOrganizationIdAndStatus(String organizationId, InvitationStatus status);

    /**
     * Find pending invitations for an organization with pagination
     */
    Flux<TeamInvitation> findByOrganizationIdAndStatus(
            String organizationId,
            InvitationStatus status,
            Pageable pageable
    );

    /**
     * Find all invitations sent to an email
     */
    Flux<TeamInvitation> findByEmail(String email);

    /**
     * Find pending invitations sent to an email
     */
    Flux<TeamInvitation> findByEmailAndStatus(String email, InvitationStatus status);

    /**
     * Find expired invitations (for cleanup)
     */
    @Query("{ 'status': 'PENDING', 'expiresAt': { $lt: ?0 } }")
    Flux<TeamInvitation> findExpiredInvitations(Instant now);

    /**
     * Count pending invitations for an organization
     */
    Mono<Long> countByOrganizationIdAndStatus(String organizationId, InvitationStatus status);

    /**
     * Find invitations created by a user
     */
    Flux<TeamInvitation> findByInvitedById(String invitedById);

    /**
     * Delete all invitations for an organization
     */
    Mono<Void> deleteByOrganizationId(String organizationId);

    /**
     * Check if pending invitation exists for email and organization
     */
    Mono<Boolean> existsByEmailAndOrganizationIdAndStatus(String email, String organizationId, InvitationStatus status);

    /**
     * Find pending invitations that have expired
     */
    Flux<TeamInvitation> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant expiresAt);
}
