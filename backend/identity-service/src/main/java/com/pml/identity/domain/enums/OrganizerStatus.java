package com.pml.identity.domain.enums;

/**
 * Status of an organizer application/account.
 *
 * Follows a staged approval flow:
 * DRAFT → PENDING_DOCUMENTS → PENDING_REVIEW → APPROVED/REJECTED/CHANGES_REQUESTED
 */
public enum OrganizerStatus {
    /**
     * Just applied, filling out business profile
     */
    DRAFT,

    /**
     * Profile complete, awaiting document upload
     */
    PENDING_DOCUMENTS,

    /**
     * Documents submitted, awaiting admin review
     */
    PENDING_REVIEW,

    /**
     * Admin requested modifications to application
     */
    CHANGES_REQUESTED,

    /**
     * Application approved, organizer can create events
     * Organization entity is created upon approval
     */
    APPROVED,

    /**
     * Application rejected by admin
     */
    REJECTED,

    /**
     * Account suspended by admin
     */
    SUSPENDED;

    /**
     * Check if this status allows editing the profile
     */
    public boolean canEditProfile() {
        return this == DRAFT || this == PENDING_DOCUMENTS || this == CHANGES_REQUESTED || this == APPROVED;
    }

    /**
     * Check if this status allows uploading documents
     */
    public boolean canUploadDocuments() {
        return this == DRAFT || this == PENDING_DOCUMENTS || this == CHANGES_REQUESTED || this == APPROVED;
    }

    /**
     * Check if this status allows submitting for review
     */
    public boolean canSubmitForReview() {
        return this == PENDING_DOCUMENTS || this == CHANGES_REQUESTED;
    }

    /**
     * Check if this status allows creating draft events
     */
    public boolean canCreateDraftEvents() {
        return this == PENDING_REVIEW || this == APPROVED;
    }

    /**
     * Check if this status allows publishing events
     */
    public boolean canPublishEvents() {
        return this == APPROVED;
    }

    /**
     * Check if this status allows inviting team members
     */
    public boolean canInviteTeamMembers() {
        return this == APPROVED;
    }
}
