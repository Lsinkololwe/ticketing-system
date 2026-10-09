package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.ApprovalNotificationChannel;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.shared.config.model.PlatformPaymentDefaults;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * PlatformConfiguration Model
 *
 * Stores platform-wide configuration settings for the approval workflow.
 * Settings are stored in the database for runtime flexibility without redeployment.
 *
 * This is a singleton document - there should only be one configuration per platform.
 */
@Document(collection = CatalogCollections.PLATFORM_CONFIGURATION)
@TypeAlias("platformConfiguration")
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
     * Default: 48 hours (the value the organizer and admin apps are specified against,
     * PLATFORM_CONFIG.md; the earlier 72 was reconciled down to it).
     */
    @Builder.Default
    private int approvalSlaHours = 48;

    /**
     * Warning threshold before SLA deadline in hours.
     * Triggers SLA_WARNING notifications to reviewers.
     * Default: warn at 36 hours into the 48 hour SLA
     */
    @Builder.Default
    private int approvalWarningThresholdHours = 36;

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
     * Default: 12 hours
     */
    @Builder.Default
    private int escalationDelayHours = 12;

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
    // PAYMENT / PAYOUT / COMMISSION DEFAULTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Platform-wide payment/payout/commission defaults applied to a new organization's
     * payout configuration at creation. Seeded in {@link #createDefault()} — no default is
     * baked into the field so the values come from the configured source of truth.
     *
     * @see PlatformPaymentDefaults
     */
    private PlatformPaymentDefaults payment;

    // ═══════════════════════════════════════════════════════════════════════════
    // RUNTIME RULES (obeyed by organizers and buyers)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Hold and grace minutes, escrow days, refund cutoff, ticket cap, reschedule limit, currency
     * and the refund policies. Read by identity-service's {@code platformRules} through
     * shared-library's {@code PlatformConfigurationReader}. Null only on a document written before
     * this section existed; the repository backfills it on first read.
     */
    private com.pml.shared.config.model.PlatformRulesSection rules;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * ID of the admin who last updated the configuration
     */
    private String updatedBy;

    /** Actor recorded for the documented defaults, before any administrator has saved. */
    public static final String SYSTEM_ACTOR = "system";

    /** Never null: the schema declares it non-null, and a configuration nobody has saved has no editor. */
    public String getUpdatedBy() {
        return updatedBy == null || updatedBy.isBlank() ? SYSTEM_ACTOR : updatedBy;
    }

    /** Never null, for the same reason; a document without a timestamp reads as the epoch. */
    public Instant getUpdatedAt() {
        return updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL READ MODEL (derived; none of these is persisted)
    // ═══════════════════════════════════════════════════════════════════════════

    private com.pml.shared.config.model.PlatformRulesSection rulesOrDefaults() {
        return rules != null ? rules : com.pml.shared.config.model.PlatformRulesSection.defaults();
    }

    /** Default commission as a percentage (5.0 = 5%). */
    public Double getCommissionDefault() {
        return payment == null || payment.getCommissionRate() == null ? null
                : java.math.BigDecimal.valueOf(payment.getCommissionRate()).movePointRight(2).doubleValue();
    }

    public java.math.BigDecimal getMinimumPayout() {
        return payment == null ? null : payment.getMinimumPayoutAmount();
    }

    public int getReservationHoldMinutes() { return rulesOrDefaults().getReservationHoldMinutes(); }

    public int getReservationGraceMinutes() { return rulesOrDefaults().getReservationGraceMinutes(); }

    public int getEscrowHoldDays() { return rulesOrDefaults().getEscrowHoldDays(); }

    public int getRefundCutoffHours() { return rulesOrDefaults().getRefundCutoffHours(); }

    public int getMaxTicketsPerBooking() { return rulesOrDefaults().getMaxTicketsPerBooking(); }

    public int getRescheduleLimit() { return rulesOrDefaults().getRescheduleLimit(); }

    public String getCurrency() { return rulesOrDefaults().getCurrency(); }

    public long getVersion() { return rulesOrDefaults().getVersion(); }

    public java.util.List<java.util.Map<String, Object>> getRefundPolicies() {
        return rulesOrDefaults().getRefundPolicies().entrySet().stream()
                .map(e -> java.util.Map.<String, Object>of(
                        "code", e.getKey(),
                        "label", e.getValue().getLabel(),
                        "summary", e.getValue().getSummary(),
                        "rules", e.getValue().getRules()))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Create a default configuration with sensible defaults.
     */
    public static PlatformConfiguration createDefault() {
        return PlatformConfiguration.builder()
                .id(DEFAULT_ID)
                .updatedBy(SYSTEM_ACTOR)
                .rules(com.pml.shared.config.model.PlatformRulesSection.defaults())
                .payment(PlatformPaymentDefaults.builder()
                        .commissionRate(0.05)
                        .payoutMethod("MOBILE_MONEY")
                        .payoutSchedule("WEEKLY")
                        .minimumPayoutAmount(new java.math.BigDecimal("100.00"))
                        .build())
                .build();
    }
}
