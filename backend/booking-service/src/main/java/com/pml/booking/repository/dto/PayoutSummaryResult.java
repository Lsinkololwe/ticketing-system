package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for payout request aggregation results.
 */
public record PayoutSummaryResult(
        long totalPayoutRequests,
        long pendingPayoutRequests,
        long approvedPayoutRequests,
        long processingPayoutRequests,
        long completedPayoutRequests,
        long failedPayoutRequests,
        long rejectedPayoutRequests,
        BigDecimal totalPayoutAmount
) {
    public static PayoutSummaryResult empty() {
        return new PayoutSummaryResult(
                0L, 0L, 0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO
        );
    }
}
