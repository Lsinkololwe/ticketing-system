package com.pml.shared.constants;

/**
 * Payment Method Enum
 */
public enum PaymentMethod {
    MOBILE_MONEY("MOBILE_MONEY", "Mobile Money"),
    CARD("CARD", "Card"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer");

    private final String code;
    private final String displayName;

    PaymentMethod(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static PaymentMethod fromCode(String code) {
        for (PaymentMethod method : values()) {
            if (method.code.equals(code)) {
                return method;
            }
        }
        return null;
    }
}
