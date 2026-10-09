package com.pml.booking.repository;

import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.domain.model.ReconciliationRun;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Reactive Repository for Reconciliation Runs
 *
 * Provides reactive access to the reconciliation_runs collection in MongoDB.
 * This repository supports the full reconciliation lifecycle from initiation
 * through completion and discrepancy resolution.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Type Queries</b>: Find runs by reconciliation type (GATEWAY, BANK, ESCROW)</li>
 *   <li><b>Status Queries</b>: Find runs by status (RUNNING, COMPLETED, REQUIRES_REVIEW)</li>
 *   <li><b>Date Queries</b>: Find runs for specific dates or ranges</li>
 *   <li><b>Monitoring</b>: Find runs needing attention</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <pre>
 * // Start daily gateway reconciliation
 * ReconciliationRun run = ReconciliationRun.create(
 *     "REC-GATEWAY-20240115-001",
 *     LocalDate.of(2024, 1, 15),
 *     ReconciliationType.GATEWAY,
 *     "PawaPay Settlement Report",
 *     "system");
 * repository.save(run);
 *
 * // Find runs requiring review
 * repository.findByStatus(ReconciliationStatus.REQUIRES_REVIEW)
 *     .doOnNext(run -> alertService.sendReviewNeeded(run));
 *
 * // Get latest gateway reconciliation
 * repository.findFirstByTypeOrderByReconciliationDateDesc(ReconciliationType.GATEWAY);
 * </pre>
 *
 * @see ReconciliationRun
 * @since 1.0.0
 */
@Repository
public interface ReconciliationRunRepository extends ReactiveMongoRepository<ReconciliationRun, String> {

    // ========================================================================
    // TYPE QUERIES
    // ========================================================================

    /**
     * Find runs by type.
     *
     * @param type Reconciliation type
     * @return Flux of runs of this type
     */
    Flux<ReconciliationRun> findByType(ReconciliationType type);

    // ========================================================================
    // STATUS QUERIES
    // ========================================================================

    /**
     * Find runs by status.
     *
     * @param status Reconciliation status
     * @return Flux of runs with this status
     */
    Flux<ReconciliationRun> findByStatus(ReconciliationStatus status);

    /**
     * Count runs by status.
     *
     * @param status Reconciliation status
     * @return Mono<Long> count
     */
    Mono<Long> countByStatus(ReconciliationStatus status);

    /**
     * Find runs requiring review.
     *
     * <p>Convenience query for monitoring dashboard.</p>
     *
     * @return Flux of runs needing review
     */
    default Flux<ReconciliationRun> findRequiringReview() {
        return findByStatus(ReconciliationStatus.REQUIRES_REVIEW);
    }

    /**
     * Find runs within a date range.
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Flux of runs within range
     */
    Flux<ReconciliationRun> findByReconciliationDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find runs within a date range for a specific type (alternate parameter order).
     *
     * @param type Reconciliation type
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Flux of matching runs
     */
    Flux<ReconciliationRun> findByTypeAndReconciliationDateBetween(
            ReconciliationType type,
            LocalDate startDate,
            LocalDate endDate
    );

    // ========================================================================
    // RUN NUMBER GENERATION SUPPORT
    // ========================================================================

    /**
     * Count runs for a specific date and type.
     *
     * <p>Used for generating sequential run numbers.</p>
     *
     * @param reconciliationDate The date
     * @param type Reconciliation type
     * @return Mono<Long> count
     */
    Mono<Long> countByReconciliationDateAndType(LocalDate reconciliationDate, ReconciliationType type);
}
