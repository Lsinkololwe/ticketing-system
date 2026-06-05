package com.pml.identity.domain.enums;

/**
 * Know Your Business (KYB) verification status.
 *
 * Follows industry standard progressive onboarding:
 * 1. Organization created → NOT_STARTED (can create draft events)
 * 2. User starts KYB form → IN_PROGRESS
 * 3. User submits documents → PENDING_REVIEW
 * 4. Admin reviews → VERIFIED / REJECTED / CHANGES_REQUESTED
 */
public enum KybStatus {
    /**
     * KYB not started yet.
     * Organization can create draft events but cannot publish or receive payouts.
     */
    NOT_STARTED,

    /**
     * KYB form partially filled, documents being collected
     */
    IN_PROGRESS,

    /**
     * KYB submitted, awaiting admin review
     */
    PENDING_REVIEW,

    /**
     * Admin requested changes to KYB submission
     */
    CHANGES_REQUESTED,

    /**
     * KYB verified, organization can receive payouts
     */
    VERIFIED,

    /**
     * KYB rejected by admin
     */
    REJECTED;

    /**
     * Check if KYB allows creating draft events
     */
    public boolean canCreateDraftEvents() {
        return this != REJECTED;
    }

    /**
     * Check if KYB allows publishing events
     */
    public boolean canPublishEvents() {
        return this == VERIFIED;
    }

    /**
     * Check if KYB allows receiving payouts
     */
    public boolean canReceivePayouts() {
        return this == VERIFIED;
    }

    /**
     * Check if KYB is in a state that allows editing
     */
    public boolean canEditKyb() {
        return this == NOT_STARTED || this == IN_PROGRESS || this == CHANGES_REQUESTED;
    }

    /**
     * Check if KYB can be submitted for review
     */
    public boolean canSubmitForReview() {
        return this == IN_PROGRESS || this == CHANGES_REQUESTED;
    }
}
