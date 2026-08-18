package com.pml.shared.constants;

/**
 * Status of a team invitation.
 */
/*
 * Lives in shared-library so catalog-service's reference-data bootstrapper can
 * reflect over it. Catalog owns the administrator-editable status list and
 * cannot depend on the service that uses the enum.
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
