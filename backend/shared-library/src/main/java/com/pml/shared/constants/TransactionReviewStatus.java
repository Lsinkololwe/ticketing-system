package com.pml.shared.constants;

/**
 * Transaction Review Status Enum
 *
 * Defines the review status for transactions requiring attention.
 */
public enum TransactionReviewStatus {

    NONE("NONE", "None", "No review needed"),
    PENDING_REVIEW("PENDING_REVIEW", "Pending Review", "Waiting for review"),
    UNDER_REVIEW("UNDER_REVIEW", "Under Review", "Currently being reviewed"),
    REVIEWED("REVIEWED", "Reviewed", "Review completed"),
    ESCALATED("ESCALATED", "Escalated", "Escalated for higher review");

    private final String code;
    private final String displayName;
    private final String description;

    TransactionReviewStatus(String code, String displayName, String description) {
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

    public static TransactionReviewStatus fromCode(String code) {
        for (TransactionReviewStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Check if this status indicates active review is needed.
     */
    public boolean needsAttention() {
        return this == PENDING_REVIEW || this == UNDER_REVIEW || this == ESCALATED;
    }

    /**
     * Check if review is complete.
     */
    public boolean isComplete() {
        return this == REVIEWED || this == NONE;
    }
}
