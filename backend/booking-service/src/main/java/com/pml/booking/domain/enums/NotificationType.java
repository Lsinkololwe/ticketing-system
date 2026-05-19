package com.pml.booking.domain.enums;

/**
 * Types of notifications sent by the booking service.
 *
 * <p>Each type has a default priority and email template association.</p>
 *
 * @since 1.0.0
 */
public enum NotificationType {

    // ========================================================================
    // CHARGEBACK NOTIFICATIONS
    // ========================================================================

    /**
     * New chargeback received from payment provider.
     * Sent to: Organizer, Finance Team
     */
    CHARGEBACK_RECEIVED("Chargeback Received", AlertPriority.HIGH,
            "A chargeback has been filed against your event"),

    /**
     * Chargeback dispute outcome determined.
     * Sent to: Organizer, Finance Team
     */
    CHARGEBACK_RESOLVED("Chargeback Resolved", AlertPriority.MEDIUM,
            "A chargeback dispute has been resolved"),

    /**
     * Chargeback resulted in write-off (unrecoverable loss).
     * Sent to: Finance Team
     */
    CHARGEBACK_WRITE_OFF("Chargeback Write-Off", AlertPriority.CRITICAL,
            "A chargeback has resulted in an unrecoverable loss"),

    // ========================================================================
    // RECONCILIATION NOTIFICATIONS
    // ========================================================================

    /**
     * Reconciliation run completed with discrepancies.
     * Sent to: Finance Team
     */
    RECONCILIATION_DISCREPANCY("Reconciliation Discrepancy", AlertPriority.HIGH,
            "Reconciliation found discrepancies requiring review"),

    /**
     * Reconciliation has been pending for too long.
     * Sent to: Finance Team
     */
    RECONCILIATION_OVERDUE("Reconciliation Overdue", AlertPriority.HIGH,
            "Reconciliation requires attention - overdue for resolution"),

    /**
     * Reconciliation variance exceeds threshold.
     * Sent to: Finance Team
     */
    RECONCILIATION_HIGH_VARIANCE("High Variance Alert", AlertPriority.CRITICAL,
            "Reconciliation variance exceeds acceptable threshold"),

    // ========================================================================
    // PAYOUT NOTIFICATIONS
    // ========================================================================

    /**
     * Payout request submitted by organizer.
     * Sent to: Finance Team
     */
    PAYOUT_REQUESTED("Payout Requested", AlertPriority.LOW,
            "A new payout request has been submitted"),

    /**
     * Payout has been approved.
     * Sent to: Organizer
     */
    PAYOUT_APPROVED("Payout Approved", AlertPriority.MEDIUM,
            "Your payout request has been approved"),

    /**
     * Payout has been completed.
     * Sent to: Organizer
     */
    PAYOUT_COMPLETED("Payout Completed", AlertPriority.LOW,
            "Your payout has been processed"),

    // ========================================================================
    // SYSTEM ALERTS
    // ========================================================================

    /**
     * Generic system alert.
     * Sent to: Finance Team
     */
    SYSTEM_ALERT("System Alert", AlertPriority.MEDIUM,
            "A system event requires attention");

    private final String displayName;
    private final AlertPriority defaultPriority;
    private final String defaultSubject;

    NotificationType(String displayName, AlertPriority defaultPriority, String defaultSubject) {
        this.displayName = displayName;
        this.defaultPriority = defaultPriority;
        this.defaultSubject = defaultSubject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AlertPriority getDefaultPriority() {
        return defaultPriority;
    }

    public String getDefaultSubject() {
        return defaultSubject;
    }

    /**
     * Returns an emoji representing this notification type.
     *
     * @return Emoji string for UI/Slack messages
     */
    public String getEmoji() {
        return switch (this) {
            case CHARGEBACK_RECEIVED -> "⚠️";
            case CHARGEBACK_RESOLVED -> "✅";
            case CHARGEBACK_WRITE_OFF -> "🔥";
            case RECONCILIATION_DISCREPANCY -> "📊";
            case RECONCILIATION_OVERDUE -> "⏰";
            case RECONCILIATION_HIGH_VARIANCE -> "🚨";
            case PAYOUT_REQUESTED -> "📝";
            case PAYOUT_APPROVED -> "✓";
            case PAYOUT_COMPLETED -> "💰";
            case SYSTEM_ALERT -> "ℹ️";
        };
    }
}
