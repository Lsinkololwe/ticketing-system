package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import com.pml.shared.constants.InvitationStatus;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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
@Document(collection = IdentityCollections.TEAM_INVITATIONS)
@TypeAlias("team_invitations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TeamInvitation {

    @Id
    private String id;

    /**
     * Email address of the invitee
     */
    /** Where an email invitation goes; null for an invitation sent to a WhatsApp number. */
    @Email(message = "Email should be valid")
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
    private String invitationToken;

    /**
     * When the invitation expires
     */
    @NotNull(message = "Expiry date is required")
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
    public boolean isValid(Instant now) {
        return status == InvitationStatus.PENDING
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    /**
     * Check if invitation has expired
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * Whether {@code candidate} is the person this invitation was addressed to.
     *
     * <p>An invitation names an email, a phone number, or both, and the token travels to that
     * address. The token alone cannot answer who is accepting: it is forwarded, screenshotted and
     * pasted into group chats, so the holder and the addressee are routinely different people.
     *
     * <p>Either identifier matching is enough — a Zambian invitee may hold the phone number the
     * invitation was sent to and an email the inviter guessed, or the reverse. Requiring both
     * would refuse the ordinary case; requiring neither is what let a forwarded link join an
     * organization.
     *
     * <p>Comparison is case-insensitive on email and exact on phone, which is E.164 and has one
     * spelling.
     */
    public boolean addressedTo(String candidateEmail, String candidatePhone) {
        boolean emailMatches = email != null && !email.isBlank()
                && candidateEmail != null && email.equalsIgnoreCase(candidateEmail);
        boolean phoneMatches = phoneNumber != null && !phoneNumber.isBlank()
                && candidatePhone != null && phoneNumber.equals(candidatePhone);
        return emailMatches || phoneMatches;
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
