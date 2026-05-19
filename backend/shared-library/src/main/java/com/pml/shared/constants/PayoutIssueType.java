package com.pml.shared.constants;

/**
 * Payout Issue Type Enum
 *
 * Defines the types of issues that can occur with payout requests
 * requiring review or recovery action.
 */
public enum PayoutIssueType {

    BANK_REJECTED("BANK_REJECTED", "Bank Rejected", "Bank rejected the payout"),
    INVALID_ACCOUNT_DETAILS("INVALID_ACCOUNT_DETAILS", "Invalid Account Details", "Account details are invalid"),
    INSUFFICIENT_ESCROW("INSUFFICIENT_ESCROW", "Insufficient Escrow", "Not enough funds in escrow"),
    COMPLIANCE_HOLD("COMPLIANCE_HOLD", "Compliance Hold", "Held for compliance review"),
    SUSPECTED_FRAUD("SUSPECTED_FRAUD", "Suspected Fraud", "Flagged for potential fraud"),
    TECHNICAL_ERROR("TECHNICAL_ERROR", "Technical Error", "Technical failure during processing"),
    PROVIDER_ERROR("PROVIDER_ERROR", "Provider Error", "Payment provider error"),
    TIMEOUT("TIMEOUT", "Timeout", "Payout processing timed out"),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST", "Duplicate Request", "Potential duplicate payout"),
    OTHER("OTHER", "Other", "Other unclassified issue");

    private final String code;
    private final String displayName;
    private final String description;

    PayoutIssueType(String code, String displayName, String description) {
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

    public static PayoutIssueType fromCode(String code) {
        for (PayoutIssueType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this issue type requires immediate attention.
     */
    public boolean isUrgent() {
        return this == SUSPECTED_FRAUD || this == COMPLIANCE_HOLD || this == DUPLICATE_REQUEST;
    }

    /**
     * Check if this issue type can potentially be auto-resolved by retry.
     */
    public boolean canAutoResolve() {
        return this == TIMEOUT || this == TECHNICAL_ERROR || this == PROVIDER_ERROR;
    }

    /**
     * Check if this issue requires bank account update to resolve.
     */
    public boolean requiresAccountUpdate() {
        return this == BANK_REJECTED || this == INVALID_ACCOUNT_DETAILS;
    }
}
