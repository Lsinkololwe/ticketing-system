package com.pml.shared.constants;

/**
 * Refund Request Status Enum
 *
 * Defines the different statuses of refund requests in the system.
 */
public enum RefundRequestStatus {

    PENDING("PENDING", "Pending", "Refund request is pending review"),
    APPROVED("APPROVED", "Approved", "Refund request has been approved"),
    PROCESSING("PROCESSING", "Processing", "Refund is being processed"),
    COMPLETED("COMPLETED", "Completed", "Refund has been completed"),
    REJECTED("REJECTED", "Rejected", "Refund request has been rejected"),
    FAILED("FAILED", "Failed", "Refund processing failed"),
    CANCELLED("CANCELLED", "Cancelled", "Refund request has been cancelled");

    private final String code;
    private final String displayName;
    private final String description;

    RefundRequestStatus(String code, String displayName, String description) {
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

    public static RefundRequestStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RefundRequestStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
