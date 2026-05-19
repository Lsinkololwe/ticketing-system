package com.pml.booking.event.domain;

import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Domain Event: Reconciliation Completed
 *
 * <p>Published when a reconciliation run reaches a terminal state
 * (COMPLETED, REQUIRES_REVIEW, or FAILED).</p>
 *
 * <h2>Triggered By</h2>
 * <ul>
 *   <li>ReconciliationService.completeRun()</li>
 *   <li>ReconciliationService.failRun()</li>
 *   <li>Automatic completion after all items matched</li>
 * </ul>
 *
 * <h2>Potential Listeners</h2>
 * <ul>
 *   <li>Notification service (alert finance team of discrepancies)</li>
 *   <li>Reporting service (generate reconciliation reports)</li>
 *   <li>Audit service (log completion for compliance)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Value
@Builder
public class ReconciliationCompletedEvent {

    /**
     * Reconciliation run ID.
     */
    String reconciliationRunId;

    /**
     * Type of reconciliation performed.
     */
    ReconciliationType type;

    /**
     * Final status of the run.
     */
    ReconciliationStatus status;

    /**
     * Date that was reconciled.
     */
    LocalDateTime reconciliationDate;

    /**
     * External data source identifier.
     */
    String dataSource;

    /**
     * Total expected amount from external source.
     */
    BigDecimal expectedTotal;

    /**
     * Total actual amount in internal records.
     */
    BigDecimal actualTotal;

    /**
     * Net variance (expected - actual).
     */
    BigDecimal variance;

    /**
     * Number of items that matched.
     */
    int matchedCount;

    /**
     * Number of items with discrepancies.
     */
    int unmatchedCount;

    /**
     * User who initiated the run.
     */
    String runBy;

    /**
     * When the run started.
     */
    LocalDateTime startedAt;

    /**
     * When the run completed.
     */
    LocalDateTime completedAt;

    /**
     * Failure reason (if status is FAILED).
     */
    String failureReason;

    /**
     * Timestamp when the event was created.
     */
    Instant eventTimestamp;

    /**
     * Factory method for creating from a completed reconciliation run.
     */
    public static ReconciliationCompletedEvent of(
            String reconciliationRunId,
            ReconciliationType type,
            ReconciliationStatus status,
            LocalDateTime reconciliationDate,
            String dataSource,
            BigDecimal expectedTotal,
            BigDecimal actualTotal,
            BigDecimal variance,
            int matchedCount,
            int unmatchedCount,
            String runBy,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String failureReason
    ) {
        return ReconciliationCompletedEvent.builder()
                .reconciliationRunId(reconciliationRunId)
                .type(type)
                .status(status)
                .reconciliationDate(reconciliationDate)
                .dataSource(dataSource)
                .expectedTotal(expectedTotal)
                .actualTotal(actualTotal)
                .variance(variance)
                .matchedCount(matchedCount)
                .unmatchedCount(unmatchedCount)
                .runBy(runBy)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .failureReason(failureReason)
                .eventTimestamp(Instant.now())
                .build();
    }

    /**
     * Check if the reconciliation was successful (no discrepancies).
     */
    public boolean isSuccessful() {
        return status == ReconciliationStatus.COMPLETED && unmatchedCount == 0;
    }

    /**
     * Check if there are discrepancies requiring attention.
     */
    public boolean hasDiscrepancies() {
        return unmatchedCount > 0 || variance.abs().compareTo(BigDecimal.ZERO) > 0;
    }
}
