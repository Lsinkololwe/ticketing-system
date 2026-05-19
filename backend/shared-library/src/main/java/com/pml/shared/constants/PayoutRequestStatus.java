package com.pml.shared.constants;

/**
 * Payout Request Status Enum
 *
 * Defines the different statuses of payout requests in the system.
 */
public enum PayoutRequestStatus {

    PENDING("PENDING", "Pending", "Payout request is pending review"),
    PENDING_FINANCE_APPROVAL("PENDING_FINANCE_APPROVAL", "Pending Finance Approval", "Awaiting finance team approval"),
    APPROVED("APPROVED", "Approved", "Payout request has been approved"),
    PROCESSING("PROCESSING", "Processing", "Payout is being processed"),
    COMPLETED("COMPLETED", "Completed", "Payout has been completed"),
    REJECTED("REJECTED", "Rejected", "Payout request has been rejected"),
    FAILED("FAILED", "Failed", "Payout processing failed"),
    CANCELLED("CANCELLED", "Cancelled", "Payout request has been cancelled");

    private final String code;
    private final String displayName;
    private final String description;

    PayoutRequestStatus(String code, String displayName, String description) {
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

    public static PayoutRequestStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayoutRequestStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isPending() {
        return this == PENDING || this == PENDING_FINANCE_APPROVAL;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == FAILED || this == CANCELLED;
    }

    public boolean canBeCancelled() {
        return this == PENDING || this == PENDING_FINANCE_APPROVAL;
    }
}
