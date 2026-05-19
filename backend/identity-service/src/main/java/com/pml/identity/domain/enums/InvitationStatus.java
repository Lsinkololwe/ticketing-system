package com.pml.identity.domain.enums;

/**
 * Status of a team invitation.
 */
public enum InvitationStatus {
    /**
     * Invitation is pending, awaiting response from invitee
     */
    PENDING,

    /**
     * Invitation was accepted by invitee
     */
    ACCEPTED,

    /**
     * Invitation was declined by invitee
     */
    DECLINED,

    /**
     * Invitation expired (not responded within time limit)
     */
    EXPIRED,

    /**
     * Invitation was revoked by inviter
     */
    REVOKED
}
