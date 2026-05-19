package com.pml.shared.constants;

/**
 * Transaction Type Enum
 */
public enum TransactionType {
    TICKET_SALE("TICKET_SALE", "Ticket Sale"),
    TICKET_REFUND("TICKET_REFUND", "Ticket Refund"),
    ESCROW_CREDIT("ESCROW_CREDIT", "Escrow Credit"),
    ESCROW_DEBIT("ESCROW_DEBIT", "Escrow Debit"),
    USER_DEBIT("USER_DEBIT", "User Debit"),
    USER_CREDIT("USER_CREDIT", "User Credit"),
    ORGANIZER_CREDIT("ORGANIZER_CREDIT", "Organizer Credit"),
    ORGANIZER_DEBIT("ORGANIZER_DEBIT", "Organizer Debit"),
    COMMISSION_EARNED("COMMISSION_EARNED", "Commission Earned"),
    PLATFORM_REVENUE("PLATFORM_REVENUE", "Platform Revenue"),
    COMMISSION_CALCULATED("COMMISSION_CALCULATED", "Commission Calculated"),
    COMMISSION_COLLECTED("COMMISSION_COLLECTED", "Commission Collected"),
    COMMISSION_REVERSED("COMMISSION_REVERSED", "Commission Reversed"),
    PAYOUT_REQUESTED("PAYOUT_REQUESTED", "Payout Requested"),
    PAYOUT_APPROVED("PAYOUT_APPROVED", "Payout Approved"),
    PAYOUT_PROCESSED("PAYOUT_PROCESSED", "Payout Processed"),
    PAYOUT_REJECTED("PAYOUT_REJECTED", "Payout Rejected"),
    SYSTEM_ADJUSTMENT("SYSTEM_ADJUSTMENT", "System Adjustment"),
    RECONCILIATION("RECONCILIATION", "Reconciliation"),
    CORRECTION("CORRECTION", "Correction"),
    REVERSAL("REVERSAL", "Reversal");

    private final String code;
    private final String displayName;

    TransactionType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static TransactionType fromCode(String code) {
        for (TransactionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
