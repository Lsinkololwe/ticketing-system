package com.pml.identity.domain.enums;

/**
 * Status of an organization (tenant) in the approval workflow.
 *
 * APPROVAL WORKFLOW:
 * ==================
 * 1. User applies to become organizer → DRAFT
 * 2. User fills business details and submits → PENDING_REVIEW
 * 3. Admin reviews:
 *    - Approves → APPROVED (can publish events)
 *    - Requests changes → CHANGES_REQUESTED (user updates and resubmits)
 *    - Rejects → REJECTED
 *
 * POST-APPROVAL:
 * ==============
 * - APPROVED organizations can be SUSPENDED by admin
 * - APPROVED organizations can be set to INACTIVE by owner
 * - PENDING_DELETION for scheduled removal
 *
 * EVENT CREATION:
 * ===============
 * - DRAFT, PENDING_REVIEW, CHANGES_REQUESTED: Can create DRAFT events only
 * - APPROVED, ACTIVE: Can create and PUBLISH events
 * - SUSPENDED, INACTIVE, REJECTED: Cannot create events
 */
public enum OrganizationStatus {

    // ─────────────────────────────────────────────────────────────────────
    // Approval Workflow Statuses
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Initial status - organization application in progress.
     * User is filling in business details.
     */
    DRAFT,

    /**
     * Application submitted for admin review.
     */
    PENDING_REVIEW,

    /**
     * Admin requested changes to the application.
     * User can update details and resubmit.
     */
    CHANGES_REQUESTED,

    /**
     * Application approved by admin.
     * Organization can now publish events.
     */
    APPROVED,

    /**
     * Application rejected by admin.
     * User cannot create events.
     */
    REJECTED,

    // ─────────────────────────────────────────────────────────────────────
    // Operational Statuses (post-approval)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Organization is active and fully operational.
     * Alias for APPROVED in operational context.
     */
    ACTIVE,

    /**
     * Organization suspended by platform admin.
     * Cannot create or manage events until unsuspended.
     */
    SUSPENDED,

    /**
     * Organization deactivated by owner.
     * Can be reactivated by owner.
     */
    INACTIVE,

    /**
     * Organization scheduled for deletion.
     */
    PENDING_DELETION;

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if organization can create draft events.
     * Allowed during application process and after approval.
     */
    public boolean canCreateDraftEvents() {
        return this == DRAFT ||
               this == PENDING_REVIEW ||
               this == CHANGES_REQUESTED ||
               this == APPROVED ||
               this == ACTIVE;
    }

    /**
     * Check if organization can publish events.
     * Only allowed after approval.
     */
    public boolean canPublishEvents() {
        return this == APPROVED || this == ACTIVE;
    }

    /**
     * Check if organization is in the approval workflow.
     */
    public boolean isInApprovalWorkflow() {
        return this == DRAFT ||
               this == PENDING_REVIEW ||
               this == CHANGES_REQUESTED;
    }

    /**
     * Check if organization application was rejected.
     */
    public boolean isRejected() {
        return this == REJECTED;
    }

    /**
     * Check if organization is operational (approved and not suspended).
     */
    public boolean isOperational() {
        return this == APPROVED || this == ACTIVE;
    }

    /**
     * Check if organization can be edited by owner.
     */
    public boolean canBeEdited() {
        return this == DRAFT ||
               this == CHANGES_REQUESTED ||
               this == APPROVED ||
               this == ACTIVE;
    }
}
