package com.pml.booking.domain.enums;

/**
 * Reconciliation Status Enum - Lifecycle State of a Reconciliation Run
 *
 * This enum tracks the progress and outcome of a reconciliation run.
 * Each reconciliation run goes through these states as it processes
 * and compares records.
 *
 * <h2>Status Lifecycle</h2>
 * <pre>
 *              ┌───────────┐
 *              │  RUNNING  │  Processing external/internal data
 *              └─────┬─────┘
 *                    │
 *        ┌───────────┼───────────┐
 *        │           │           │
 *        ▼           ▼           ▼
 *  ┌───────────┐ ┌──────────────┐ ┌────────┐
 *  │ COMPLETED │ │REQUIRES_REVIEW│ │ FAILED │
 *  │ (all OK)  │ │(discrepancies)│ │(error) │
 *  └───────────┘ └──────────────┘ └────────┘
 * </pre>
 *
 * <h2>Outcome Determination</h2>
 * <ul>
 *   <li><b>COMPLETED</b>: All records matched, variance within tolerance</li>
 *   <li><b>REQUIRES_REVIEW</b>: Discrepancies found that need human review</li>
 *   <li><b>FAILED</b>: Technical error prevented completion (network, data format, etc.)</li>
 * </ul>
 *
 * @see ReconciliationType
 * @see ReconciliationItemStatus
 * @since 1.0.0
 */
public enum ReconciliationStatus {

    /**
     * Running - Reconciliation is in progress.
     *
     * <p>During this state:</p>
     * <ul>
     *   <li>External data is being fetched/parsed</li>
     *   <li>Internal records are being queried</li>
     *   <li>Matching algorithm is running</li>
     *   <li>Discrepancies are being identified</li>
     * </ul>
     *
     * <p>A reconciliation run should not remain in RUNNING for more than
     * a few minutes. Long-running jobs should be monitored.</p>
     */
    RUNNING,

    /**
     * Completed - Reconciliation finished successfully with no issues.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>All external records matched to internal records</li>
     *   <li>All internal records matched to external records</li>
     *   <li>No amount discrepancies (or within tolerance threshold)</li>
     *   <li>completedAt timestamp is set</li>
     * </ul>
     *
     * <p>This is the ideal outcome - it means books are balanced.</p>
     */
    COMPLETED,

    /**
     * Requires Review - Reconciliation found discrepancies needing human attention.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>One or more records are unmatched</li>
     *   <li>And/or amount mismatches exceed tolerance</li>
     *   <li>Human must investigate and resolve each item</li>
     *   <li>Items can be resolved as MATCHED (false positive) or escalated</li>
     * </ul>
     *
     * <p>Common reasons for discrepancies:</p>
     * <ul>
     *   <li>Timing differences (payment in transit)</li>
     *   <li>Missing webhook (gateway callback not received)</li>
     *   <li>Duplicate entry (same transaction recorded twice)</li>
     *   <li>Currency conversion differences</li>
     *   <li>Gateway fees not separately recorded</li>
     * </ul>
     */
    REQUIRES_REVIEW,

    /**
     * Failed - Reconciliation could not complete due to an error.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Technical failure prevented completion</li>
     *   <li>Error details should be logged</li>
     *   <li>May need to retry after fixing the issue</li>
     *   <li>Does NOT affect financial records</li>
     * </ul>
     *
     * <p>Common failure reasons:</p>
     * <ul>
     *   <li>Cannot fetch external data (network error, API down)</li>
     *   <li>External data format unexpected (schema change)</li>
     *   <li>Internal database query timeout</li>
     *   <li>Insufficient permissions to read data</li>
     * </ul>
     */
    FAILED;

    /**
     * Checks if this status indicates the reconciliation is still in progress.
     *
     * @return true if the reconciliation is still running
     */
    public boolean isInProgress() {
        return this == RUNNING;
    }

    /**
     * Checks if this status indicates the reconciliation has finished.
     *
     * <p>Note: Finished means processing stopped - it doesn't mean successful.</p>
     *
     * @return true if the reconciliation is no longer processing
     */
    public boolean isFinished() {
        return this != RUNNING;
    }

    /**
     * Checks if this status indicates a successful reconciliation with no issues.
     *
     * @return true only if all records matched with no discrepancies
     */
    public boolean isSuccessful() {
        return this == COMPLETED;
    }

    /**
     * Checks if this status requires human intervention.
     *
     * @return true if discrepancies need manual review
     */
    public boolean needsReview() {
        return this == REQUIRES_REVIEW;
    }

    /**
     * Checks if this status indicates a technical failure.
     *
     * @return true if the reconciliation failed due to an error
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * Returns the severity level for alerting purposes.
     *
     * @return Severity level: INFO (completed), WARNING (review needed), ERROR (failed)
     */
    public String getSeverityLevel() {
        return switch (this) {
            case RUNNING -> "INFO";
            case COMPLETED -> "INFO";
            case REQUIRES_REVIEW -> "WARNING";
            case FAILED -> "ERROR";
        };
    }
}
