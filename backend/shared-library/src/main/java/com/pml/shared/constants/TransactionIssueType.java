package com.pml.shared.constants;

/**
 * Transaction Issue Type Enum
 *
 * Defines the types of issues that can occur with financial transactions
 * requiring review or recovery action.
 */
public enum TransactionIssueType {

    PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", "Payment Timeout", "Payment gateway timeout"),
    WEBHOOK_MISSED("WEBHOOK_MISSED", "Webhook Missed", "Callback not received from payment provider"),
    AMOUNT_MISMATCH("AMOUNT_MISMATCH", "Amount Mismatch", "Payment amount doesn't match expected"),
    DUPLICATE_TRANSACTION("DUPLICATE_TRANSACTION", "Duplicate Transaction", "Potential duplicate detected"),
    PROVIDER_ERROR("PROVIDER_ERROR", "Provider Error", "Payment provider returned error"),
    NETWORK_FAILURE("NETWORK_FAILURE", "Network Failure", "Network communication failure"),
    VALIDATION_FAILURE("VALIDATION_FAILURE", "Validation Failure", "Transaction validation failed"),
    MANUAL_REVIEW_REQUIRED("MANUAL_REVIEW_REQUIRED", "Manual Review Required", "Flagged for manual review"),
    RECONCILIATION_NEEDED("RECONCILIATION_NEEDED", "Reconciliation Needed", "Needs reconciliation with external system"),
    OTHER("OTHER", "Other", "Other unclassified issue");

    private final String code;
    private final String displayName;
    private final String description;

    TransactionIssueType(String code, String displayName, String description) {
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

    public static TransactionIssueType fromCode(String code) {
        for (TransactionIssueType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this issue type typically requires immediate attention.
     */
    public boolean isUrgent() {
        return this == PAYMENT_TIMEOUT || this == AMOUNT_MISMATCH || this == DUPLICATE_TRANSACTION;
    }

    /**
     * Check if this issue type can potentially be auto-resolved.
     */
    public boolean canAutoResolve() {
        return this == PAYMENT_TIMEOUT || this == WEBHOOK_MISSED || this == NETWORK_FAILURE;
    }
}
