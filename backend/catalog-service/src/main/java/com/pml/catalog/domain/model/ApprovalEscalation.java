package com.pml.catalog.domain.model;

import com.pml.catalog.persistence.CatalogCollections;

import com.pml.catalog.domain.enums.EscalationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

/**
 * ApprovalEscalation Model
 *
 * Tracks auto-escalation events when SLA is breached.
 * Supports reminder tracking and escalation resolution.
 */
@Document(collection = CatalogCollections.APPROVAL_ESCALATIONS)
@TypeAlias("approval_escalations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalEscalation {

    @Id
    private String id;

    /**
     * Event ID this escalation is for
     */
    private String eventId;

    /**
     * Denormalized event title for display
     */
    private String eventTitle;

    // ═══════════════════════════════════════════════════════════════════════════
    // ESCALATION DETAILS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Current status of the escalation
     */
    private EscalationStatus status;

    /**
     * Reason for escalation (e.g., "SLA breach - 24 hours overdue")
     */
    private String reason;

    /**
     * The SLA level this escalation records: 1 at the SLA, 2 at twice it, 3 at four
     * times. Zero for an escalation an operator raised by hand.
     */
    private int level;

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the escalation was triggered
     */
    @CreatedDate
    private Instant triggeredAt;

    /**
     * When a senior admin acknowledged the escalation
     */
    private Instant acknowledgedAt;

    /**
     * When the escalation was resolved
     */
    private Instant resolvedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * User ID of the senior admin this was escalated to
     */
    private String escalatedTo;

    /**
     * Denormalized name of the escalation recipient
     */
    private String escalatedToName;

    /**
     * User ID of the admin who acknowledged the escalation
     */
    private String acknowledgedBy;

    /**
     * Denormalized name of the acknowledging admin
     */
    private String acknowledgedByName;

    /**
     * User ID of the admin who resolved the escalation
     */
    private String resolvedBy;

    /**
     * Denormalized name of the resolving admin
     */
    private String resolvedByName;

    /**
     * Notes added when resolving the escalation
     */
    private String resolutionNotes;

    // ═══════════════════════════════════════════════════════════════════════════
    // ORIGINAL ASSIGNMENT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Original reviewer who failed to meet SLA (may be null if unassigned)
     */
    private String originalReviewerId;

    /**
     * Denormalized original reviewer name
     */
    private String originalReviewerName;

    // ═══════════════════════════════════════════════════════════════════════════
    // REMINDER TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Number of reminders sent for this escalation
     */
    @Builder.Default
    private int remindersSent = 0;

    /**
     * When the last reminder was sent
     */
    private Instant lastReminderAt;

    /**
     * When the next reminder should be sent
     */
    private Instant nextReminderAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA CONTEXT
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Hours overdue when escalation was triggered
     */
    private int hoursOverdue;

    /**
     * The SLA deadline that was missed
     */
    private Instant slaDeadline;

    /**
     * Last modified timestamp
     */
    @LastModifiedDate
    private Instant updatedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Acknowledge the escalation
     */
    public void acknowledge(String adminId, String adminName, String notes, Instant now) {
        this.status = EscalationStatus.ACKNOWLEDGED;
        this.acknowledgedAt = now;
        this.acknowledgedBy = adminId;
        this.acknowledgedByName = adminName;
        if (notes != null) {
            this.resolutionNotes = notes;
        }
    }

    /**
     * Resolve the escalation
     */
    public void resolve(String adminId, String adminName, String notes, Instant now) {
        this.status = EscalationStatus.RESOLVED;
        this.resolvedAt = now;
        this.resolvedBy = adminId;
        this.resolvedByName = adminName;
        this.resolutionNotes = notes;
    }

    /**
     * Check if the escalation is still active
     */
    public boolean isActive() {
        return status == EscalationStatus.PENDING || status == EscalationStatus.ACKNOWLEDGED;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a new escalation for an overdue approval
     */
    public static ApprovalEscalation create(String eventId, String eventTitle,
                                            String escalatedTo, String escalatedToName,
                                            String reason, Instant slaDeadline,
                                            String originalReviewerId, String originalReviewerName,
                                            int reminderIntervalHours, Instant now) {
        int hoursOverdue = (int) Duration.between(slaDeadline, now).toHours();

        return ApprovalEscalation.builder()
                .eventId(eventId)
                .eventTitle(eventTitle)
                .status(EscalationStatus.PENDING)
                .reason(reason)
                .triggeredAt(now)
                .escalatedTo(escalatedTo)
                .escalatedToName(escalatedToName)
                .originalReviewerId(originalReviewerId)
                .originalReviewerName(originalReviewerName)
                .hoursOverdue(hoursOverdue)
                .slaDeadline(slaDeadline)
                .nextReminderAt(now.plus(Duration.ofHours(reminderIntervalHours)))
                .build();
    }
}
