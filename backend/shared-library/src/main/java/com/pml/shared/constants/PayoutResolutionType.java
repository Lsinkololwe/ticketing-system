package com.pml.shared.constants;

/**
 * Payout Resolution Type Enum
 *
 * Defines how payout issues are resolved.
 */
public enum PayoutResolutionType {

    AUTO_RESOLVED("AUTO_RESOLVED", "Auto Resolved", "System automatically resolved"),
    MANUAL_APPROVAL("MANUAL_APPROVAL", "Manual Approval", "Admin manually approved"),
    MANUAL_REJECTION("MANUAL_REJECTION", "Manual Rejection", "Admin manually rejected"),
    RETRIED_SUCCESS("RETRIED_SUCCESS", "Retried Successfully", "Successfully retried"),
    ACCOUNT_UPDATED("ACCOUNT_UPDATED", "Account Updated", "Bank account details corrected"),
    REFUNDED_TO_ESCROW("REFUNDED_TO_ESCROW", "Refunded to Escrow", "Amount returned to escrow"),
    WRITTEN_OFF("WRITTEN_OFF", "Written Off", "Written off as loss"),
    ESCALATED("ESCALATED", "Escalated", "Escalated to higher authority");

    private final String code;
    private final String displayName;
    private final String description;

    PayoutResolutionType(String code, String displayName, String description) {
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

    public static PayoutResolutionType fromCode(String code) {
        for (PayoutResolutionType type : values()) {
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
               this == RETRIED_SUCCESS || this == ACCOUNT_UPDATED;
    }

    /**
     * Check if this resolution resulted in returning funds.
     */
    public boolean fundsReturned() {
        return this == REFUNDED_TO_ESCROW;
    }

    /**
     * Check if this resolution resulted in a loss.
     */
    public boolean isLoss() {
        return this == WRITTEN_OFF;
    }
}
