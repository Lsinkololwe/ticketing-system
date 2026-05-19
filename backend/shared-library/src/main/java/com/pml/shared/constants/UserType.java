package com.pml.shared.constants;

/**
 * User Type Enum
 *
 * Defines the different types of users in the system.
 */
public enum UserType {

    CUSTOMER("CUSTOMER", "Customer", "Regular ticket buyer"),
    ORGANIZER("ORGANIZER", "Organizer", "Event organizer who can create and manage events"),
    ADMIN("ADMIN", "Administrator", "Platform administrator with full access"),
    SUPER_ADMIN("SUPER_ADMIN", "Super Administrator", "Super administrator with highest privileges"),
    SCANNER("SCANNER", "Scanner", "Ticket scanner/validator at events"),
    FINANCE("FINANCE", "Finance", "Finance team member for payouts and reconciliation");

    private final String code;
    private final String displayName;
    private final String description;

    UserType(String code, String displayName, String description) {
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

    public static UserType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (UserType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean canCreateEvents() {
        return this == ORGANIZER || isAdmin();
    }

    public boolean canScanTickets() {
        return this == SCANNER || this == ORGANIZER || isAdmin();
    }

    public boolean canProcessPayouts() {
        return this == FINANCE || this == SUPER_ADMIN;
    }
}
