package com.pml.catalog.domain.enums;

/**
 * EscalationStatus Enum
 *
 * Defines the different states of an approval escalation.
 */
public enum EscalationStatus {

    PENDING("PENDING", "Pending", "Escalation triggered, not yet handled"),
    ACKNOWLEDGED("ACKNOWLEDGED", "Acknowledged", "Senior admin acknowledged the escalation"),
    RESOLVED("RESOLVED", "Resolved", "Escalation resolved"),
    EXPIRED("EXPIRED", "Expired", "Escalation expired without action");

    private final String code;
    private final String displayName;
    private final String description;

    EscalationStatus(String code, String displayName, String description) {
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

    public static EscalationStatus fromCode(String code) {
        for (EscalationStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Check if the escalation is still active and needs attention
     */
    public boolean isActive() {
        return this == PENDING || this == ACKNOWLEDGED;
    }

    /**
     * Check if the escalation has been finalized
     */
    public boolean isFinal() {
        return this == RESOLVED || this == EXPIRED;
    }
}
