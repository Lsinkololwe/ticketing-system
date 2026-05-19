package com.pml.shared.constants;

/**
 * Transaction Resolution Type Enum
 *
 * Defines how transaction issues are resolved.
 */
public enum TransactionResolutionType {

    AUTO_RESOLVED("AUTO_RESOLVED", "Auto Resolved", "System automatically resolved"),
    MANUAL_APPROVAL("MANUAL_APPROVAL", "Manual Approval", "Admin manually approved"),
    MANUAL_REJECTION("MANUAL_REJECTION", "Manual Rejection", "Admin manually rejected"),
    RETRIED_SUCCESS("RETRIED_SUCCESS", "Retried Successfully", "Successfully retried"),
    REFUNDED("REFUNDED", "Refunded", "Refunded to customer"),
    WRITTEN_OFF("WRITTEN_OFF", "Written Off", "Written off as loss"),
    RECONCILED("RECONCILED", "Reconciled", "Reconciled with external system"),
    ESCALATED("ESCALATED", "Escalated", "Escalated to higher authority");

    private final String code;
    private final String displayName;
    private final String description;

    TransactionResolutionType(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static TransactionResolutionType fromCode(String code) {
        for (TransactionResolutionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this resolution is considered successful.
     */
    public boolean isSuccessful() {
        return this == AUTO_RESOLVED || this == MANUAL_APPROVAL ||
               this == RETRIED_SUCCESS || this == RECONCILED;
    }

    /**
     * Check if this resolution resulted in a financial loss.
     */
    public boolean isLoss() {
        return this == WRITTEN_OFF || this == REFUNDED;
    }
}
