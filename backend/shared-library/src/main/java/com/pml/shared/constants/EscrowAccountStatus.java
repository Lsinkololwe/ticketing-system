package com.pml.shared.constants;

/**
 * Escrow Account Status Enum
 */
public enum EscrowAccountStatus {
    ACTIVE("ACTIVE", "Active", "Account is active"),
    SUSPENDED("SUSPENDED", "Suspended", "Account is suspended"),
    CLOSED("CLOSED", "Closed", "Account is closed"),
    MAINTENANCE("MAINTENANCE", "Maintenance", "Account is under maintenance");

    private final String code;
    private final String displayName;
    private final String description;

    EscrowAccountStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static EscrowAccountStatus fromCode(String code) {
        for (EscrowAccountStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
