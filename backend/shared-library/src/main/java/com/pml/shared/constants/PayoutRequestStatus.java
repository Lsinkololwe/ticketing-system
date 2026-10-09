package com.pml.shared.constants;

/**
 * Payout request lifecycle.
 *
 * <p>Exactly seven states. A request sitting in the finance queue waiting on a
 * human is {@link #PENDING}, and only {@link #PENDING}: two codes for one state
 * means every query has to remember both, and the one that forgets under-reports
 * the approval backlog — the queue looks shorter than it is, which is the
 * direction nobody investigates.
 *
 * <p>{@link #COMPLETED}, {@link #REJECTED} and {@link #CANCELLED} are terminal.
 * {@link #FAILED} is not: a failed payout may be retried or re-requested.
 */
public enum PayoutRequestStatus {

    PENDING("PENDING", "Pending", "Payout request is pending review"),
    APPROVED("APPROVED", "Approved", "Payout request has been approved"),
    PROCESSING("PROCESSING", "Processing", "Payout is being processed"),
    COMPLETED("COMPLETED", "Completed", "Payout has been completed"),
    REJECTED("REJECTED", "Rejected", "Payout request has been rejected"),
    FAILED("FAILED", "Failed", "Payout processing failed"),
    CANCELLED("CANCELLED", "Cancelled", "Payout request has been cancelled");

    private final String code;
    private final String displayName;
    private final String description;

    PayoutRequestStatus(String code, String displayName, String description) {
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

    public static PayoutRequestStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayoutRequestStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == FAILED || this == CANCELLED;
    }
}
