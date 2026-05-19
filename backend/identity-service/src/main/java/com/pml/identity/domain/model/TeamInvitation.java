package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.InvitationStatus;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * Team Invitation Model
 *
 * Represents a pending invitation to join an organization.
 *
 * INVITATION FLOW:
 * ===============
 * 1. Owner/Admin creates invitation with email and role
 * 2. System generates unique invitation token
 * 3. System sends email/SMS with acceptance link
 * 4. Invitee clicks link:
 *    - If not registered: sign up flow with invitation context
 *    - If registered: show accept/decline options
 * 5. On acceptance:
 *    - Create OrganizationMember
 *    - Create EventAccessGrants if specified
 *    - Add to Keycloak group
 *    - Notify organization owner
 */
@Document(collection = "team_invitations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "email_org_idx", def = "{'email': 1, 'organizationId': 1}"),
    @CompoundIndex(name = "org_status_idx", def = "{'organizationId': 1, 'status': 1}")
})
public class TeamInvitation {

    @Id
    private String id;

    /**
     * Email address of the invitee
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Indexed
    private String email;

    /**
     * Phone number of the invitee (optional)
     */
    private String phoneNumber;

    /**
     * Name of the invitee (optional, for display)
     */
    private String inviteeName;

    /**
     * Organization ID
     */
    @NotBlank(message = "Organization ID is required")
    @Indexed
    private String organizationId;

    /**
     * Role to assign upon acceptance
     */
    @NotNull(message = "Proposed role is required")
    private OrganizationRole proposedRole;

    /**
     * Event-specific access to grant upon acceptance (optional)
     * List of event IDs with their respective roles
     */
    private List<EventAccessInput> eventAccessGrants;

    /**
     * User ID of who sent the invitation
     */
    @NotBlank(message = "Inviter is required")
    private String invitedById;

    /**
     * Personal message from the inviter (optional)
     */
    private String message;

    /**
     * Unique token for acceptance link
     */
    @NotBlank(message = "Invitation token is required")
    @Indexed(unique = true)
    private String invitationToken;

    /**
     * When the invitation expires
     */
    @NotNull(message = "Expiry date is required")
    @Indexed
    private Instant expiresAt;

    /**
     * Invitation status
     */
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreatedDate
    private Instant createdAt;

    /**
     * When the invitation was accepted
     */
    private Instant acceptedAt;

    /**
     * When the invitation was declined
     */
    private Instant declinedAt;

    /**
     * Check if invitation is still valid
     */
    public boolean isValid() {
        return status == InvitationStatus.PENDING &&
               expiresAt != null &&
               expiresAt.isAfter(Instant.now());
    }

    /**
     * Check if invitation has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Embedded class for event access grants in invitations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventAccessInput {
        private String eventId;
        private EventRole role;
        private Instant expiresAt;
    }
}
