package com.pml.identity.repository;

import java.util.Collection;
import com.pml.shared.constants.InvitationStatus;
import com.pml.identity.domain.model.TeamInvitation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /** Invitations sent to a WhatsApp number (stored in E.164). */
    Flux<TeamInvitation> findByPhoneNumberAndStatus(String phoneNumber, InvitationStatus status);

    /**
     * Count pending invitations for an organization
     */
    Mono<Long> countByOrganizationIdAndStatus(String organizationId, InvitationStatus status);

    Mono<TeamInvitation> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);
}
