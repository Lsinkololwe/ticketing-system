package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The {@code ReconciliationSummary} GraphQL type, field for field.
 *
 * @param totalRuns Total number of reconciliation runs
 * @param completedRuns Runs completed successfully
 * @param pendingReviewRuns Runs requiring manual review
 * @param failedRuns Runs that failed
 * @param totalVariance Net variance across all runs
 * @param resolvedVariance Variance on items already resolved
 * @param unresolvedVariance Variance still awaiting resolution
 * @param lastCompletedDate Start of the day the latest completed run covered, or null
 * @param oldestPendingDate Start of the day the oldest run awaiting review covered, or null
 */
public record ReconciliationSummary(
    long totalRuns,
    long completedRuns,
    long pendingReviewRuns,
    long failedRuns,
    BigDecimal totalVariance,
    BigDecimal resolvedVariance,
    BigDecimal unresolvedVariance,
    Instant lastCompletedDate,
    Instant oldestPendingDate
) {
}
