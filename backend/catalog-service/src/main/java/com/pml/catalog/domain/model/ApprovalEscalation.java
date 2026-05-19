package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.EscalationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * ApprovalEscalation Model
 *
 * Tracks auto-escalation events when SLA is breached.
 * Supports reminder tracking and escalation resolution.
 */
@Document(collection = "approval_escalations")
@CompoundIndex(name = "status_escalatedTo_idx", def = "{'status': 1, 'escalatedTo': 1}")
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
    @Indexed
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
    @Indexed
    private EscalationStatus status;

    /**
     * Reason for escalation (e.g., "SLA breach - 24 hours overdue")
     */
    private String reason;

    // ═══════════════════════════════════════════════════════════════════════════
    // TIMING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * When the escalation was triggered
     */
    @CreatedDate
    private LocalDateTime triggeredAt;

    /**
     * When a senior admin acknowledged the escalation
     */
    private LocalDateTime acknowledgedAt;

    /**
     * When the escalation was resolved
     */
    private LocalDateTime resolvedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // ACTORS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * User ID of the senior admin this was escalated to
     */
    @Indexed
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
    private LocalDateTime lastReminderAt;

    /**
     * When the next reminder should be sent
     */
    @Indexed
    private LocalDateTime nextReminderAt;

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
    private LocalDateTime slaDeadline;

    /**
     * Last modified timestamp
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // CALCULATED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate current hours overdue
     */
    public int getCurrentHoursOverdue() {
        if (slaDeadline == null) return hoursOverdue;
        return (int) Duration.between(slaDeadline, LocalDateTime.now()).toHours();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DOMAIN METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Acknowledge the escalation
     */
    public void acknowledge(String adminId, String adminName, String notes) {
        this.status = EscalationStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
        this.acknowledgedBy = adminId;
        this.acknowledgedByName = adminName;
        if (notes != null) {
            this.resolutionNotes = notes;
        }
    }

    /**
     * Resolve the escalation
     */
    public void resolve(String adminId, String adminName, String notes) {
        this.status = EscalationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = adminId;
        this.resolvedByName = adminName;
        this.resolutionNotes = notes;
    }

    /**
     * Mark the escalation as expired
     */
    public void expire() {
        this.status = EscalationStatus.EXPIRED;
    }

    /**
     * Record a reminder was sent
     */
    public void recordReminderSent(int reminderIntervalHours) {
        this.remindersSent++;
        this.lastReminderAt = LocalDateTime.now();
        this.nextReminderAt = LocalDateTime.now().plusHours(reminderIntervalHours);
    }

    /**
     * Check if a reminder is due
     */
    public boolean isReminderDue() {
        if (nextReminderAt == null) return false;
        return LocalDateTime.now().isAfter(nextReminderAt);
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
                                            String reason, LocalDateTime slaDeadline,
                                            String originalReviewerId, String originalReviewerName,
                                            int reminderIntervalHours) {
        LocalDateTime now = LocalDateTime.now();
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
                .nextReminderAt(now.plusHours(reminderIntervalHours))
                .build();
    }
}
