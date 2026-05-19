package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reconciliation summary statistics.
 *
 * @param totalRuns Total number of reconciliation runs
 * @param completedRuns Runs completed successfully
 * @param failedRuns Runs that failed
 * @param pendingReviewRuns Runs requiring manual review
 * @param totalItemsProcessed Total items processed across all runs
 * @param matchedItems Items that matched successfully
 * @param unmatchedItems Items with discrepancies
 * @param totalExpectedAmount Total expected amount from external sources
 * @param totalActualAmount Total actual amount in internal records
 * @param totalVariance Net variance (expected - actual)
 * @param lastRunDate Date of the most recent reconciliation run
 * @param averageMatchRate Average match rate as percentage
 *
 * @since 1.0.0
 */
public record ReconciliationSummary(
    long totalRuns,
    long completedRuns,
    long failedRuns,
    long pendingReviewRuns,
    long totalItemsProcessed,
    long matchedItems,
    long unmatchedItems,
    BigDecimal totalExpectedAmount,
    BigDecimal totalActualAmount,
    BigDecimal totalVariance,
    LocalDateTime lastRunDate,
    BigDecimal averageMatchRate
) {
    /**
     * Create empty summary.
     */
    public static ReconciliationSummary empty() {
        return new ReconciliationSummary(
            0, 0, 0, 0, 0, 0, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            null, BigDecimal.ZERO
        );
    }

    /**
     * Calculate success rate as percentage.
     */
    public BigDecimal getSuccessRate() {
        if (totalRuns == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedRuns * 100.0 / totalRuns);
    }

    /**
     * Check if there are pending issues.
     */
    public boolean hasPendingIssues() {
        return pendingReviewRuns > 0 || unmatchedItems > 0;
    }

    /**
     * Calculate variance percentage.
     */
    public BigDecimal getVariancePercentage() {
        if (totalExpectedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalVariance.abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(totalExpectedAmount, 2, java.math.RoundingMode.HALF_UP);
    }
}
