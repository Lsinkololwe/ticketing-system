package com.pml.identity.service;

import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.model.TeamInvitation;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Team Invitation Service Interface
 *
 * Manages team invitations workflow.
 */
public interface TeamInvitationService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find invitation by ID
     */
    Mono<TeamInvitation> findById(String id);

    /**
     * Find invitation by token
     */
    Mono<TeamInvitation> findByToken(String invitationToken);

    /**
     * Find pending invitations for an organization with pagination
     */
    Flux<TeamInvitation> findPendingByOrganization(String organizationId, Pageable pageable);

    /**
     * Find pending invitations for an organization
     */
    Flux<TeamInvitation> findPendingByOrganization(String organizationId);

    /**
     * Find all invitations for an organization with pagination
     */
    Flux<TeamInvitation> findByOrganization(String organizationId, Pageable pageable);

    /**
     * Find pending invitations for a user (by email)
     */
    Flux<TeamInvitation> findPendingByEmail(String email);

    /** Pending invitations sent to a WhatsApp number (E.164). */
    Flux<TeamInvitation> findPendingByPhone(String phoneNumber);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Create and send invitation
     */
    Mono<TeamInvitation> invite(
            String organizationId,
            String email,
            String phoneNumber,
            String inviteeName,
            OrganizationRole role,
            String message,
            List<TeamInvitation.EventAccessInput> eventAccessGrants,
            String invitedById
    );

    /**
     * Bulk invite team members
     */
    Flux<TeamInvitation> bulkInvite(
            String organizationId,
            List<InviteRequest> invites,
            String invitedById
    );

    /**
     * Resend invitation email
     */
    Mono<TeamInvitation> resend(String invitationId);

    /**
     * Revoke invitation
     */
    Mono<TeamInvitation> revoke(String invitationId);

    /**
     * Accept invitation (creates member)
     */
    Mono<OrganizationMember> accept(String invitationToken, String userId);

    /**
     * Decline invitation
     */
    Mono<TeamInvitation> decline(String invitationToken);

    // ─────────────────────────────────────────────────────────────────────
    // Helper Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bulk invite request
     */
    record InviteRequest(
            String email,
            String phoneNumber,
            String inviteeName,
            OrganizationRole role,
            String message,
            List<TeamInvitation.EventAccessInput> eventAccessGrants
    ) {}
}
