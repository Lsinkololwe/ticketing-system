package com.pml.booking.exception;

import com.pml.booking.domain.enums.ReconciliationType;

import java.math.BigDecimal;

/**
 * Exception thrown when reconciliation finds discrepancies exceeding tolerance.
 *
 * <p>During reconciliation, some small discrepancies may be acceptable
 * (e.g., rounding differences). This exception is thrown when the
 * variance exceeds the configured tolerance threshold.</p>
 *
 * <h2>Discrepancy Types</h2>
 * <ul>
 *   <li><b>Amount Mismatch</b>: Total amounts don't match</li>
 *   <li><b>Count Mismatch</b>: Number of records don't match</li>
 *   <li><b>Missing Records</b>: Records exist in one source but not another</li>
 * </ul>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Gateway fees not separately recorded</li>
 *   <li>Timing differences (settlement delays)</li>
 *   <li>Failed webhooks (missing transactions)</li>
 *   <li>Duplicate entries</li>
 *   <li>Currency conversion differences</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * <ol>
 *   <li>Review the reconciliation run details</li>
 *   <li>Investigate each discrepancy item</li>
 *   <li>Create adjustment journal entries if needed</li>
 *   <li>Mark items as resolved with explanation</li>
 * </ol>
 *
 * @see com.pml.booking.domain.model.ReconciliationRun
 * @since 1.0.0
 */
public class ReconciliationDiscrepancyException extends RuntimeException {

    /**
     * Type of reconciliation being performed.
     */
    private final ReconciliationType reconciliationType;

    /**
     * The variance amount found.
     */
    private final BigDecimal variance;

    /**
     * The tolerance threshold that was exceeded.
     */
    private final BigDecimal toleranceThreshold;

    /**
     * Number of unmatched items.
     */
    private final Integer unmatchedCount;

    /**
     * The reconciliation run ID for reference.
     */
    private final String runId;

    /**
     * Creates a new ReconciliationDiscrepancyException with basic message.
     *
     * @param message Error message
     */
    public ReconciliationDiscrepancyException(String message) {
        super(message);
        this.reconciliationType = null;
        this.variance = null;
        this.toleranceThreshold = null;
        this.unmatchedCount = null;
        this.runId = null;
    }

    /**
     * Creates a new ReconciliationDiscrepancyException with variance details.
     *
     * @param reconciliationType Type of reconciliation
     * @param variance The variance amount
     * @param toleranceThreshold The threshold that was exceeded
     * @param runId The reconciliation run ID
     */
    public ReconciliationDiscrepancyException(
            ReconciliationType reconciliationType,
            BigDecimal variance,
            BigDecimal toleranceThreshold,
            String runId
    ) {
        super(String.format(
                "%s reconciliation found variance of %s which exceeds tolerance of %s. Run ID: %s",
                reconciliationType,
                variance,
                toleranceThreshold,
                runId
        ));
        this.reconciliationType = reconciliationType;
        this.variance = variance;
        this.toleranceThreshold = toleranceThreshold;
        this.unmatchedCount = null;
        this.runId = runId;
    }

    /**
     * Creates a new ReconciliationDiscrepancyException with unmatched count.
     *
     * @param reconciliationType Type of reconciliation
     * @param unmatchedCount Number of unmatched records
     * @param runId The reconciliation run ID
     */
    public ReconciliationDiscrepancyException(
            ReconciliationType reconciliationType,
            Integer unmatchedCount,
            String runId
    ) {
        super(String.format(
                "%s reconciliation found %d unmatched records requiring review. Run ID: %s",
                reconciliationType,
                unmatchedCount,
                runId
        ));
        this.reconciliationType = reconciliationType;
        this.variance = null;
        this.toleranceThreshold = null;
        this.unmatchedCount = unmatchedCount;
        this.runId = runId;
    }

    /**
     * Creates a new ReconciliationDiscrepancyException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public ReconciliationDiscrepancyException(String message, Throwable cause) {
        super(message, cause);
        this.reconciliationType = null;
        this.variance = null;
        this.toleranceThreshold = null;
        this.unmatchedCount = null;
        this.runId = null;
    }

    // Getters

    public ReconciliationType getReconciliationType() {
        return reconciliationType;
    }

    public BigDecimal getVariance() {
        return variance;
    }

    public BigDecimal getToleranceThreshold() {
        return toleranceThreshold;
    }

    public Integer getUnmatchedCount() {
        return unmatchedCount;
    }

    public String getRunId() {
        return runId;
    }
}
