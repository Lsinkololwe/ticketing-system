package com.pml.shared.constants;

/**
 * Payout Method Enum
 *
 * Defines the different payout methods available for organizer payouts.
 */
public enum PayoutMethod {

    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer", "Direct bank transfer to organizer account"),
    MOBILE_MONEY("MOBILE_MONEY", "Mobile Money", "Mobile money transfer (MTN, Airtel, Zamtel)"),
    CHEQUE("CHEQUE", "Cheque", "Physical cheque payment");

    private final String code;
    private final String displayName;
    private final String description;

    PayoutMethod(String code, String displayName, String description) {
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

    public static PayoutMethod fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayoutMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return method;
            }
        }
        return null;
    }
}
