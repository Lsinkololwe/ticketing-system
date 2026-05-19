package com.pml.catalog.domain.valueobject;

import com.pml.catalog.domain.enums.ApprovalAction;
import com.pml.shared.constants.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * TimelineEvent Value Object
 *
 * Represents a single event in the approval workflow timeline.
 * This is a value object embedded in ApprovalTimeline documents.
 *
 * Each timeline event captures:
 * - What happened (action)
 * - Who did it (actor)
 * - When it happened (timestamp)
 * - What changed (previous/new status)
 * - Additional context (comments, notes)
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEvent {

    /**
     * Unique identifier for this timeline event
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * Event ID this timeline event belongs to
     */
    private String eventId;

    /**
     * When this action occurred
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * The type of action that occurred
     */
    private ApprovalAction action;

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTOR INFORMATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * ID of the user who performed this action
     */
    private String actorId;

    /**
     * Display name of the actor
     */
    private String actorName;

    /**
     * Role of the actor (ORGANIZER, ADMIN, SENIOR_ADMIN, SYSTEM)
     */
    private String actorRole;

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS CHANGE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Event status before this action (null if no change)
     */
    private EventStatus previousStatus;

    /**
     * Event status after this action (null if no change)
     */
    private EventStatus newStatus;

    // ═══════════════════════════════════════════════════════════════════════════
    // DETAILS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Human-readable description of the action
     */
    private String description;

    /**
     * Comments visible to the organizer (feedback, instructions)
     */
    private String comments;

    /**
     * Internal notes visible only to admins
     */
    private String internalNotes;

    /**
     * Whether this event is related to an escalation
     */
    @Builder.Default
    private boolean isEscalationRelated = false;

    /**
     * Additional metadata for extensibility
     */
    private Map<String, Object> metadata;

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a submission timeline event
     */
    public static TimelineEvent submission(String eventId, String organizerId, String organizerName) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.SUBMITTED)
                .actorId(organizerId)
                .actorName(organizerName)
                .actorRole("ORGANIZER")
                .previousStatus(EventStatus.DRAFT)
                .newStatus(EventStatus.PENDING_APPROVAL)
                .description("Event submitted for approval")
                .build();
    }

    /**
     * Create an assignment timeline event
     */
    public static TimelineEvent assignment(String eventId, String adminId, String adminName,
                                           String reviewerId, String reviewerName) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.ASSIGNED)
                .actorId(adminId)
                .actorName(adminName)
                .actorRole("ADMIN")
                .description("Event assigned to " + reviewerName + " for review")
                .build();
    }

    /**
     * Create an approval timeline event
     */
    public static TimelineEvent approval(String eventId, String reviewerId, String reviewerName, String comments) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.APPROVED)
                .actorId(reviewerId)
                .actorName(reviewerName)
                .actorRole("ADMIN")
                .previousStatus(EventStatus.PENDING_APPROVAL)
                .newStatus(EventStatus.APPROVED)
                .description("Event approved")
                .comments(comments)
                .build();
    }

    /**
     * Create a rejection timeline event
     */
    public static TimelineEvent rejection(String eventId, String reviewerId, String reviewerName, String comments) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.REJECTED)
                .actorId(reviewerId)
                .actorName(reviewerName)
                .actorRole("ADMIN")
                .previousStatus(EventStatus.PENDING_APPROVAL)
                .newStatus(EventStatus.REJECTED)
                .description("Event rejected")
                .comments(comments)
                .build();
    }

    /**
     * Create a changes requested timeline event
     */
    public static TimelineEvent changesRequested(String eventId, String reviewerId, String reviewerName, String comments) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.CHANGES_REQUESTED)
                .actorId(reviewerId)
                .actorName(reviewerName)
                .actorRole("ADMIN")
                .previousStatus(EventStatus.PENDING_APPROVAL)
                .newStatus(EventStatus.CHANGES_REQUESTED)
                .description("Changes requested")
                .comments(comments)
                .build();
    }

    /**
     * Create a resubmission timeline event
     */
    public static TimelineEvent resubmission(String eventId, String organizerId, String organizerName) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.RESUBMITTED)
                .actorId(organizerId)
                .actorName(organizerName)
                .actorRole("ORGANIZER")
                .previousStatus(EventStatus.CHANGES_REQUESTED)
                .newStatus(EventStatus.PENDING_APPROVAL)
                .description("Event resubmitted after changes")
                .build();
    }

    /**
     * Create an escalation timeline event
     */
    public static TimelineEvent escalation(String eventId, String escalatedToName, String reason) {
        return TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.ESCALATED)
                .actorId("SYSTEM")
                .actorName("System")
                .actorRole("SYSTEM")
                .description("Auto-escalated to " + escalatedToName + ": " + reason)
                .isEscalationRelated(true)
                .build();
    }

    /**
     * Create a comment timeline event
     */
    public static TimelineEvent comment(String eventId, String adminId, String adminName,
                                        String comment, boolean isInternal) {
        TimelineEvent.TimelineEventBuilder builder = TimelineEvent.builder()
                .eventId(eventId)
                .action(ApprovalAction.COMMENT_ADDED)
                .actorId(adminId)
                .actorName(adminName)
                .actorRole("ADMIN")
                .description("Comment added");

        if (isInternal) {
            builder.internalNotes(comment);
        } else {
            builder.comments(comment);
        }

        return builder.build();
    }
}
