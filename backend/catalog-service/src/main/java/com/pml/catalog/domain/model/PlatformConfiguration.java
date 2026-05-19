package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.ApprovalNotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * PlatformConfiguration Model
 *
 * Stores platform-wide configuration settings for the approval workflow.
 * Settings are stored in the database for runtime flexibility without redeployment.
 *
 * This is a singleton document - there should only be one configuration per platform.
 */
@Document(collection = "platform_configuration")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlatformConfiguration {

    public static final String DEFAULT_ID = "platform-config";

    @Id
    @Builder.Default
    private String id = DEFAULT_ID;

    // ═══════════════════════════════════════════════════════════════════════════
    // APPROVAL SLA SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Default SLA for event approval in hours.
     * Default: 72 hours (3 business days)
     */
    @Builder.Default
    private int approvalSlaHours = 72;

    /**
     * Warning threshold before SLA deadline in hours.
     * Triggers SLA_WARNING notifications to reviewers.
     * Default: 24 hours before deadline
     */
    @Builder.Default
    private int approvalWarningThresholdHours = 24;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTO-ESCALATION SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether auto-escalation is enabled when SLA is breached.
     */
    @Builder.Default
    private boolean autoEscalationEnabled = true;

    /**
     * Hours after SLA breach before triggering escalation.
     * Allows grace period before escalation.
     * Default: 0 (escalate immediately on breach)
     */
    @Builder.Default
    private int escalationDelayHours = 0;

    /**
     * Role to escalate to when SLA is breached.
     * Should match a role in Keycloak (e.g., "SENIOR_ADMIN", "PLATFORM_ADMIN")
     */
    @Builder.Default
    private String escalationRecipientRole = "SENIOR_ADMIN";

    /**
     * Hours between reminder notifications after escalation.
     * Default: 24 hours
     */
    @Builder.Default
    private int escalationReminderIntervalHours = 24;

    /**
     * Maximum number of reminders to send before marking as critical.
     * Default: 3 reminders
     */
    @Builder.Default
    private int maxEscalationReminders = 3;

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Notification channel for organizers (submission, approval, rejection)
     */
    @Builder.Default
    private ApprovalNotificationChannel organizerNotificationChannel = ApprovalNotificationChannel.BOTH;

    /**
     * Notification channel for admins (assignments, reminders, escalations)
     */
    @Builder.Default
    private ApprovalNotificationChannel adminNotificationChannel = ApprovalNotificationChannel.BOTH;

    /**
     * Whether to send SLA warning notifications to reviewers
     */
    @Builder.Default
    private boolean sendSlaWarningNotifications = true;

    /**
     * Whether to send escalation notifications
     */
    @Builder.Default
    private boolean sendEscalationNotifications = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // WORKFLOW SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Whether comments are required when rejecting an event.
     */
    @Builder.Default
    private boolean requireCommentsOnRejection = true;

    /**
     * Whether comments are required when requesting changes.
     */
    @Builder.Default
    private boolean requireCommentsOnChangesRequested = true;

    /**
     * Whether organizers can approve their own events (for platform admins who are also organizers).
     * Default: false for separation of duties
     */
    @Builder.Default
    private boolean allowSelfApproval = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * ID of the admin who last updated the configuration
     */
    private String updatedBy;

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a default configuration with sensible defaults.
     */
    public static PlatformConfiguration createDefault() {
        return PlatformConfiguration.builder()
                .id(DEFAULT_ID)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculate the SLA deadline from submission time.
     */
    public LocalDateTime calculateSlaDeadline(LocalDateTime submittedAt) {
        return submittedAt.plusHours(approvalSlaHours);
    }

    /**
     * Calculate when SLA warning should be triggered.
     */
    public LocalDateTime calculateWarningTime(LocalDateTime slaDeadline) {
        return slaDeadline.minusHours(approvalWarningThresholdHours);
    }

    /**
     * Calculate when escalation should be triggered after SLA breach.
     */
    public LocalDateTime calculateEscalationTime(LocalDateTime slaDeadline) {
        return slaDeadline.plusHours(escalationDelayHours);
    }
}
