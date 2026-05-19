package com.pml.shared.constants;

/**
 * Payment Provider Enum
 */
public enum PaymentProvider {
    MTN_MONEY("MTN_MONEY", "MTN Mobile Money"),
    AIRTEL_MONEY("AIRTEL_MONEY", "Airtel Money"),
    ZAMTEL_MONEY("ZAMTEL_MONEY", "Zamtel Money"),
    BANK_TRANSFER("BANK_TRANSFER", "Bank Transfer"),
    CARD("CARD", "Card Payment");

    private final String code;
    private final String displayName;

    PaymentProvider(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static PaymentProvider fromCode(String code) {
        for (PaymentProvider provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        return null;
    }
}
