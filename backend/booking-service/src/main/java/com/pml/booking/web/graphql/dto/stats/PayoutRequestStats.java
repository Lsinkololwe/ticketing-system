package com.pml.booking.web.graphql.dto.stats;

import java.math.BigDecimal;

/**
 * Payout Request Statistics DTO for GraphQL
 */
public record PayoutRequestStats(
    int totalPayoutRequests,
    int pendingPayoutRequests,
    int approvedPayoutRequests,
    int processingPayoutRequests,
    int completedPayoutRequests,
    int failedPayoutRequests,
    BigDecimal totalPayoutAmount,
    BigDecimal pendingPayoutAmount
) {
    /**
     * Factory for backward compatibility with existing code.
     */
    public static PayoutRequestStats of(
            int totalPayoutRequests,
            int pendingPayoutRequests,
            int approvedPayoutRequests,
            int processingPayoutRequests,
            int completedPayoutRequests,
            int failedPayoutRequests
    ) {
        return new PayoutRequestStats(
            totalPayoutRequests,
            pendingPayoutRequests,
            approvedPayoutRequests,
            processingPayoutRequests,
            completedPayoutRequests,
            failedPayoutRequests,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}
