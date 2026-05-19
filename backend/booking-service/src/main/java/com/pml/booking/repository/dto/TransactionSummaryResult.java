package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for transaction aggregation results.
 *
 * Uses MongoDB $facet to compute multiple aggregations in a single query:
 * - Status counts (PENDING, COMPLETED, FAILED, etc.)
 * - Financial totals (volume, commissions)
 *
 * This is the scalable approach for analytics on billions of records.
 */
public record TransactionSummaryResult(
        long totalTransactions,
        long pendingTransactions,
        long processingTransactions,
        long completedTransactions,
        long failedTransactions,
        long cancelledTransactions,
        BigDecimal totalVolume,
        BigDecimal totalCommissions
) {
    public static TransactionSummaryResult empty() {
        return new TransactionSummaryResult(
                0L, 0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
