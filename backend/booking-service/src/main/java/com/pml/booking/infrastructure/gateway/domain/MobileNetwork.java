package com.pml.booking.infrastructure.gateway.domain;

/**
 * Provider-agnostic mobile network enum.
 * Maps to provider-specific codes via adapters.
 */
public enum MobileNetwork {
    MTN("mtn", "MTN Mobile Money"),
    AIRTEL("airtel", "Airtel Money"),
    ZAMTEL("zamtel", "Zamtel Kwacha");

    private final String code;
    private final String displayName;

    MobileNetwork(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Detect network from phone number prefix.
     * Zambian phone numbers: +260 9X XXX XXXX
     *
     * @param phoneNumber E.164 format phone number
     * @return Detected network or null if not recognized
     */
    public static MobileNetwork fromPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }

        // Normalize: remove + and spaces
        String normalized = phoneNumber.replaceAll("[+\\s-]", "");

        // Zambian prefixes (260 country code)
        if (normalized.startsWith("26097") || normalized.startsWith("26096")) {
            return MTN;
        }
        if (normalized.startsWith("26077") || normalized.startsWith("26076")) {
            return AIRTEL;
        }
        if (normalized.startsWith("26095")) {
            return ZAMTEL;
        }

        // Without country code
        if (normalized.startsWith("097") || normalized.startsWith("096")) {
            return MTN;
        }
        if (normalized.startsWith("077") || normalized.startsWith("076")) {
            return AIRTEL;
        }
        if (normalized.startsWith("095")) {
            return ZAMTEL;
        }

        return null;
    }
}
