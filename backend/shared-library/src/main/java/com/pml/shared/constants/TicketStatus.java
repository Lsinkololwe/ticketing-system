package com.pml.shared.constants;

/**
 * Ticket Status Enum
 *
 * Defines the different states of tickets in the system.
 */
public enum TicketStatus {

    PENDING_PAYMENT("PENDING_PAYMENT", "Pending Payment", "Payment is pending for the ticket"),
    PENDING_VERIFICATION("PENDING_VERIFICATION", "Pending Verification", "Ticket purchase completed but awaiting buyer verification"),
    PURCHASED("PURCHASED", "Purchased", "Ticket has been purchased"),
    CONFIRMED("CONFIRMED", "Confirmed", "Ticket has been confirmed after payment"),
    VALIDATED("VALIDATED", "Validated", "Ticket has been validated"),
    USED("USED", "Used", "Ticket has been used for entry"),
    EXPIRED("EXPIRED", "Expired", "Ticket has expired"),
    CANCELLED("CANCELLED", "Cancelled", "Ticket has been cancelled"),
    REFUNDED("REFUNDED", "Refunded", "Ticket has been refunded"),
    CHARGEDBACK("CHARGEDBACK", "Charged Back", "Payment was chargebacked - ticket invalidated"),
    PAYMENT_FAILED("PAYMENT_FAILED", "Payment Failed", "Payment failed for the ticket");

    private final String code;
    private final String displayName;
    private final String description;

    TicketStatus(String code, String displayName, String description) {
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

    public static TicketStatus fromCode(String code) {
        for (TicketStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isValid() {
        return this == PURCHASED || this == CONFIRMED || this == VALIDATED;
    }

    public boolean isPendingVerification() {
        return this == PENDING_VERIFICATION;
    }

    public boolean isUnusable() {
        return this == REFUNDED || this == EXPIRED || this == CANCELLED || this == PAYMENT_FAILED || this == CHARGEDBACK;
    }

    public boolean isUsed() {
        return this == USED;
    }

    public boolean isChargedback() {
        return this == CHARGEDBACK;
    }

    /**
     * Check if this status allows the ticket to be charged back.
     * Only PURCHASED, CONFIRMED, or VALIDATED tickets can be chargebacked.
     */
    public boolean isChargebackEligible() {
        return this == PURCHASED || this == CONFIRMED || this == VALIDATED;
    }

    public boolean isPendingPayment() {
        return this == PENDING_PAYMENT;
    }

    public boolean isPaymentFailed() {
        return this == PAYMENT_FAILED;
    }

    public boolean isConfirmed() {
        return this == CONFIRMED;
    }
}
