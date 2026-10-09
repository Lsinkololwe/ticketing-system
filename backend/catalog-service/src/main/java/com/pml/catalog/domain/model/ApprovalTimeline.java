package com.pml.catalog.domain.model;

import com.pml.catalog.persistence.CatalogCollections;

import com.pml.catalog.domain.valueobject.TimelineEvent;
import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ApprovalTimeline Model
 *
 * Tracks the complete approval history for an event.
 * This is the aggregate root for the approval workflow domain.
 *
 * Features:
 * - Complete audit trail via embedded TimelineEvents
 * - SLA tracking with deadline calculations
 * - Escalation reference
 * - Iteration tracking for changes-requested flow
 */
@Document(collection = CatalogCollections.APPROVAL_TIMELINES)
@TypeAlias("approval_timelines")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTimeline {

    @Id
    private String id;

    /**
     * Event ID this timeline belongs to (1:1 relationship)
     */
    private String eventId;

    /**
     * Denormalized event title for display
     */
    private String eventTitle;

    /**
     * Organizer who submitted the event
     */
    private String organizerId;

    /**
     * Denormalized organizer name for display
     */
    private String organizerName;

    // ═══════════════════════════════════════════════════════════════════════════
    // CURRENT STATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Current approval status of the event
     */
    private EventStatus currentStatus;

    /**
     * Currently assigned reviewer (null if unassigned)
     */
    private String assignedReviewerId;

    /**
     * Denormalized reviewer name for display
     */
    private String assignedReviewerName;

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMELINE EVENTS (Audit Trail)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Complete history of actions on this event's approval
     */
    @Builder.Default
    private List<TimelineEvent> timelineEvents = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the event was first submitted for approval
     */
    private Instant submittedAt;

    /**
     * Calculated SLA deadline based on platform configuration
     */
    private Instant slaDeadline;

    /**
     * When the event was actually approved (null if not yet approved)
     */
    private Instant actualApprovalAt;

    /**
     * Whether the approval is overdue (past SLA deadline)
     */
    @Builder.Default
    private boolean isOverdue = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // ESCALATION TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ID of active escalation (null if no active escalation)
     */
    private String escalationId;

    /**
     * Whether there is an active escalation for this timeline
     */
    @Builder.Default
    private boolean hasActiveEscalation = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // ITERATION TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Number of times the event has been submitted (initial + resubmissions)
     */
    @Builder.Default
    private int submissionCount = 0;

    /**
     * Current review iteration (increments on resubmission)
     */
    @Builder.Default
    private int currentIteration = 1;

    // ═══════════════════════════════════════════════════════════════════════════
    // QUICK STATS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Total number of comments on this approval
     */
    @Builder.Default
    private int totalComments = 0;

    /**
     * When the last activity occurred
     */
    @LastModifiedDate
    private Instant lastActivityAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // CALCULATED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Same as slaDeadline for API clarity
     */
    public Instant getExpectedApprovalAt() {
        return slaDeadline;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Add a timeline event and update stats
     */
    public void addTimelineEvent(TimelineEvent event) {
        if (timelineEvents == null) {
            timelineEvents = new ArrayList<>();
        }
        timelineEvents.add(event);
        lastActivityAt = event.getTimestamp();

        if (event.getComments() != null || event.getInternalNotes() != null) {
            totalComments++;
        }

        if (event.getNewStatus() != null) {
            currentStatus = event.getNewStatus();
        }
    }

    /**
     * Record a submission
     */
    public void recordSubmission(String organizerId, String organizerName, Instant slaDeadline, Instant now) {
        this.submittedAt = now;
        this.slaDeadline = slaDeadline;
        this.currentStatus = EventStatus.PENDING_APPROVAL;
        this.submissionCount++;

        addTimelineEvent(TimelineEvent.submission(eventId, organizerId, organizerName, now));
    }

    /**
     * Record a resubmission after changes
     */
    public void recordResubmission(String organizerId, String organizerName, Instant newSlaDeadline,
                                   Instant now) {
        this.slaDeadline = newSlaDeadline;
        this.currentStatus = EventStatus.PENDING_APPROVAL;
        this.submissionCount++;
        this.currentIteration++;
        this.isOverdue = false;

        addTimelineEvent(TimelineEvent.resubmission(eventId, organizerId, organizerName, now));
    }

    /**
     * Record approval
     */
    public void recordApproval(String reviewerId, String reviewerName, String comments, Instant now) {
        this.currentStatus = EventStatus.APPROVED;
        this.actualApprovalAt = now;

        addTimelineEvent(TimelineEvent.approval(eventId, reviewerId, reviewerName, comments, now));
    }

    /**
     * Record rejection
     */
    public void recordRejection(String reviewerId, String reviewerName, String comments, Instant now) {
        this.currentStatus = EventStatus.REJECTED;

        addTimelineEvent(TimelineEvent.rejection(eventId, reviewerId, reviewerName, comments, now));
    }

    /**
     * Record changes requested
     */
    public void recordChangesRequested(String reviewerId, String reviewerName, String comments,
                                       Instant now) {
        this.currentStatus = EventStatus.CHANGES_REQUESTED;

        addTimelineEvent(TimelineEvent.changesRequested(eventId, reviewerId, reviewerName, comments, now));
    }

    /**
     * Assign reviewer
     */
    public void assignReviewer(String adminId, String adminName, String reviewerId,
                               String reviewerName, Instant now) {
        this.assignedReviewerId = reviewerId;
        this.assignedReviewerName = reviewerName;

        addTimelineEvent(TimelineEvent.assignment(eventId, adminId, adminName, reviewerId, reviewerName, now));
    }

    /**
     * Unassign reviewer
     */
    public void unassignReviewer() {
        this.assignedReviewerId = null;
        this.assignedReviewerName = null;
    }

    /**
     * Mark as escalated
     */
    public void markEscalated(String escalationId, String escalatedToName, String reason, Instant now) {
        this.escalationId = escalationId;
        this.hasActiveEscalation = true;

        addTimelineEvent(TimelineEvent.escalation(eventId, escalatedToName, reason, now));
    }

    /**
     * Mark escalation resolved
     */
    public void resolveEscalation() {
        this.hasActiveEscalation = false;
        // Keep escalationId for history
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new approval timeline for an event
     */
    public static ApprovalTimeline create(String eventId, String eventTitle,
                                          String organizerId, String organizerName) {
        return ApprovalTimeline.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .organizerId(organizerId)
                .organizerName(organizerName)
                .currentStatus(EventStatus.DRAFT)
                .timelineEvents(new ArrayList<>())
                .build();
    }
}
