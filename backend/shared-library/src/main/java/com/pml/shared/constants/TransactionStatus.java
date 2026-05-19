package com.pml.shared.constants;

/**
 * Transaction Status Enum
 *
 * Defines the different statuses of financial transactions in the system.
 */
public enum TransactionStatus {

    PENDING("PENDING", "Pending", "Transaction is pending"),
    PROCESSING("PROCESSING", "Processing", "Transaction is being processed"),
    COMPLETED("COMPLETED", "Completed", "Transaction has been completed"),
    FAILED("FAILED", "Failed", "Transaction has failed"),
    CANCELLED("CANCELLED", "Cancelled", "Transaction has been cancelled"),
    REVERSED("REVERSED", "Reversed", "Transaction has been reversed"),
    ROLLED_BACK("ROLLED_BACK", "Rolled Back", "Transaction has been rolled back"),
    FULFILLED("FULFILLED", "Fulfilled", "Transaction has been fulfilled"),
    RETRYING("RETRYING", "Retrying", "Transaction is retrying"),
    TIMED_OUT("TIMED_OUT", "Timed Out", "Transaction has timed out"),
    PENDING_VERIFICATION("PENDING_VERIFICATION", "Pending Verification", "Transaction requires manual verification");

    private final String code;
    private final String displayName;
    private final String description;

    TransactionStatus(String code, String displayName, String description) {
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

    public static TransactionStatus fromCode(String code) {
        for (TransactionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isSuccessful() {
        return this == COMPLETED || this == FULFILLED;
    }

    public boolean isFailed() {
        return this == FAILED || this == CANCELLED || this == REVERSED || this == ROLLED_BACK;
    }

    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING || this == RETRYING;
    }
}
