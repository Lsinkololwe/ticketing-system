package com.pml.shared.constants;

/**
 * Ticket Refund Status Enum
 */
public enum TicketRefundStatus {
    PENDING("PENDING", "Pending", "Refund is pending"),
    PROCESSING("PROCESSING", "Processing", "Refund is being processed"),
    COMPLETED("COMPLETED", "Completed", "Refund has been completed"),
    FAILED("FAILED", "Failed", "Refund has failed"),
    CANCELLED("CANCELLED", "Cancelled", "Refund has been cancelled");

    private final String code;
    private final String displayName;
    private final String description;

    TicketRefundStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static TicketRefundStatus fromCode(String code) {
        for (TicketRefundStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
