package com.pml.shared.constants;

/**
 * Refund Request Type Enum
 */
public enum RefundRequestType {
    USER_REQUESTED("USER_REQUESTED", "User Requested"),
    ADMIN_INITIATED("ADMIN_INITIATED", "Admin Initiated"),
    SYSTEM_AUTOMATIC("SYSTEM_AUTOMATIC", "System Automatic"),
    EVENT_CANCELLED("EVENT_CANCELLED", "Event Cancelled"),
    TICKET_EXPIRED("TICKET_EXPIRED", "Ticket Expired"),
    FULL("FULL", "Full Refund"),
    PARTIAL("PARTIAL", "Partial Refund");

    private final String code;
    private final String displayName;

    RefundRequestType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static RefundRequestType fromCode(String code) {
        for (RefundRequestType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
