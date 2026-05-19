package com.pml.shared.constants;

/**
 * Transaction Chain Enum
 */
public enum TransactionChain {
    TICKET_SALE_CHAIN("TICKET_SALE_CHAIN", "Ticket Sale Chain"),
    PAYOUT_CHAIN("PAYOUT_CHAIN", "Payout Chain"),
    REFUND_CHAIN("REFUND_CHAIN", "Refund Chain"),
    COMMISSION_CHAIN("COMMISSION_CHAIN", "Commission Chain"),
    REVERSAL_CHAIN("REVERSAL_CHAIN", "Reversal Chain");

    private final String code;
    private final String displayName;

    TransactionChain(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static TransactionChain fromCode(String code) {
        for (TransactionChain chain : values()) {
            if (chain.code.equals(code)) {
                return chain;
            }
        }
        return null;
    }
}
