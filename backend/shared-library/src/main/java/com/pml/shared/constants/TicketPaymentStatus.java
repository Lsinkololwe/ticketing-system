package com.pml.shared.constants;

/**
 * Ticket Payment Status Enum
 */
public enum TicketPaymentStatus {
    PENDING("PENDING", "Pending", "Payment is pending"),
    COMPLETED("COMPLETED", "Completed", "Payment has been completed"),
    FAILED("FAILED", "Failed", "Payment has failed"),
    REFUNDED("REFUNDED", "Refunded", "Payment has been refunded");

    private final String code;
    private final String displayName;
    private final String description;

    TicketPaymentStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public boolean isSuccessful() { return this == COMPLETED; }
    public boolean isPending() { return this == PENDING; }
    public boolean isFailed() { return this == FAILED; }
    public boolean isRefunded() { return this == REFUNDED; }

    public static TicketPaymentStatus fromCode(String code) {
        for (TicketPaymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
