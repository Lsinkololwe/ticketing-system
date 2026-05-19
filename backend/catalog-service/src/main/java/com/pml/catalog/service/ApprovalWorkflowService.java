package com.pml.catalog.service;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.dto.ApprovalStats;
import reactor.core.publisher.Mono;

/**
 * Main orchestration service for the approval workflow.
 * Coordinates between timelines, escalations, notifications, and event status.
 */
public interface ApprovalWorkflowService {

    // ==========================================
    // Submission Operations
    // ==========================================

    /**
     * Submit an event for approval.
     * Creates timeline, calculates SLA deadline, sends notifications.
     *
     * @param eventId the event ID
     * @param organizerId the organizer submitting
     * @param organizerName the organizer's display name
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> submitForApproval(String eventId, String organizerId, String organizerName);

    /**
     * Resubmit an event after making requested changes.
     *
     * @param eventId the event ID
     * @param organizerId the organizer resubmitting
     * @param organizerName the organizer's display name
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> resubmitForApproval(String eventId, String organizerId, String organizerName);

    // ==========================================
    // Reviewer Operations
    // ==========================================

    /**
     * Assign a reviewer to an event.
     *
     * @param eventId the event ID
     * @param reviewerId the reviewer's user ID
     * @param reviewerName the reviewer's display name
     * @param assignedBy the admin who assigned (may be same as reviewer)
     * @param internalNotes optional internal notes
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> assignReviewer(String eventId, String reviewerId, String reviewerName,
                                          String assignedBy, String internalNotes);

    /**
     * Unassign a reviewer from an event.
     *
     * @param eventId the event ID
     * @param reason optional reason for unassignment
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> unassignReviewer(String eventId, String reason);

    // ==========================================
    // Approval Decision Operations
    // ==========================================

    /**
     * Approve an event.
     *
     * @param eventId the event ID
     * @param reviewerId the reviewer approving
     * @param reviewerName the reviewer's display name
     * @param comments optional approval comments
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> approveEvent(String eventId, String reviewerId, String reviewerName, String comments);

    /**
     * Reject an event.
     *
     * @param eventId the event ID
     * @param reviewerId the reviewer rejecting
     * @param reviewerName the reviewer's display name
     * @param comments required rejection comments
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> rejectEvent(String eventId, String reviewerId, String reviewerName, String comments);

    /**
     * Request changes to an event.
     *
     * @param eventId the event ID
     * @param reviewerId the reviewer requesting changes
     * @param reviewerName the reviewer's display name
     * @param comments required comments explaining requested changes
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> requestChanges(String eventId, String reviewerId, String reviewerName, String comments);

    /**
     * Add an internal comment to the timeline without changing status.
     *
     * @param eventId the event ID
     * @param adminId the admin adding the comment
     * @param adminName the admin's display name
     * @param comment the comment text
     * @param isInternal whether the comment is internal-only
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> addComment(String eventId, String adminId, String adminName,
                                      String comment, boolean isInternal);

    // ==========================================
    // Escalation Operations
    // ==========================================

    /**
     * Acknowledge an escalation.
     *
     * @param escalationId the escalation ID
     * @param adminId the admin acknowledging
     * @param adminName the admin's display name
     * @param notes optional notes
     * @return the updated escalation
     */
    Mono<ApprovalEscalation> acknowledgeEscalation(String escalationId, String adminId,
                                                    String adminName, String notes);

    /**
     * Resolve an escalation (must also make a decision on the event).
     *
     * @param escalationId the escalation ID
     * @param adminId the admin resolving
     * @param adminName the admin's display name
     * @param resolutionNotes the resolution notes
     * @return the updated escalation
     */
    Mono<ApprovalEscalation> resolveEscalation(String escalationId, String adminId,
                                               String adminName, String resolutionNotes);

    /**
     * Manually trigger an escalation.
     *
     * @param eventId the event ID
     * @param reason the reason for manual escalation
     * @param escalateTo the user ID to escalate to
     * @param escalateToName the name of the escalation recipient
     * @return the created escalation
     */
    Mono<ApprovalEscalation> triggerManualEscalation(String eventId, String reason,
                                                      String escalateTo, String escalateToName);

    // ==========================================
    // Statistics
    // ==========================================

    /**
     * Get approval workflow statistics for admin dashboard.
     */
    Mono<ApprovalStats> getApprovalStats();

    // ==========================================
    // Scheduled Operations
    // ==========================================

    /**
     * Process overdue approvals and trigger escalations.
     * Called by scheduled job.
     */
    Mono<Void> processOverdueApprovals();

    /**
     * Send reminder notifications for pending approvals.
     * Called by scheduled job.
     */
    Mono<Void> sendPendingReminders();

    /**
     * Process escalation reminders.
     * Called by scheduled job.
     */
    Mono<Void> processEscalationReminders();
}
