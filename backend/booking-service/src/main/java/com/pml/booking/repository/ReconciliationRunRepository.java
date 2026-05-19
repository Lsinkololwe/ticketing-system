package com.pml.booking.repository;

import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.domain.model.ReconciliationRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
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
    // RUN NUMBER LOOKUPS
    // ========================================================================

    /**
     * Find run by unique run number.
     *
     * @param runNumber The run number (e.g., "REC-GATEWAY-20240115-001")
     * @return Mono containing the run if found
     */
    Mono<ReconciliationRun> findByRunNumber(String runNumber);

    /**
     * Check if run number exists.
     *
     * @param runNumber The run number to check
     * @return Mono<Boolean> true if exists
     */
    Mono<Boolean> existsByRunNumber(String runNumber);

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

    /**
     * Find runs by type with pagination, ordered by date descending.
     *
     * @param type Reconciliation type
     * @param pageable Pagination parameters
     * @return Flux of runs
     */
    Flux<ReconciliationRun> findByTypeOrderByReconciliationDateDesc(
            ReconciliationType type,
            Pageable pageable
    );

    /**
     * Find the most recent run of a type.
     *
     * @param type Reconciliation type
     * @return Mono containing the most recent run
     */
    Mono<ReconciliationRun> findFirstByTypeOrderByReconciliationDateDesc(ReconciliationType type);

    /**
     * Count runs by type.
     *
     * @param type Reconciliation type
     * @return Mono<Long> count
     */
    Mono<Long> countByType(ReconciliationType type);

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
     * Find runs by status with pagination.
     *
     * @param status Reconciliation status
     * @param pageable Pagination parameters
     * @return Flux of runs
     */
    Flux<ReconciliationRun> findByStatusOrderByStartedAtDesc(
            ReconciliationStatus status,
            Pageable pageable
    );

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
     * Find failed runs.
     *
     * @return Flux of failed runs
     */
    default Flux<ReconciliationRun> findFailed() {
        return findByStatus(ReconciliationStatus.FAILED);
    }

    /**
     * Find currently running reconciliations.
     *
     * @return Flux of runs in RUNNING status
     */
    default Flux<ReconciliationRun> findInProgress() {
        return findByStatus(ReconciliationStatus.RUNNING);
    }

    // ========================================================================
    // TYPE + STATUS QUERIES
    // ========================================================================

    /**
     * Find runs by type and status.
     *
     * @param type Reconciliation type
     * @param status Reconciliation status
     * @return Flux of matching runs
     */
    Flux<ReconciliationRun> findByTypeAndStatus(ReconciliationType type, ReconciliationStatus status);

    /**
     * Find latest run of a type with specific status.
     *
     * @param type Reconciliation type
     * @param status Reconciliation status
     * @return Mono containing the most recent matching run
     */
    Mono<ReconciliationRun> findFirstByTypeAndStatusOrderByReconciliationDateDesc(
            ReconciliationType type,
            ReconciliationStatus status
    );

    // ========================================================================
    // DATE QUERIES
    // ========================================================================

    /**
     * Find runs for a specific reconciliation date.
     *
     * @param reconciliationDate The date being reconciled
     * @return Flux of runs for that date
     */
    Flux<ReconciliationRun> findByReconciliationDate(LocalDate reconciliationDate);

    /**
     * Find runs for a specific date and type.
     *
     * @param reconciliationDate The date being reconciled
     * @param type Reconciliation type
     * @return Flux of matching runs
     */
    Flux<ReconciliationRun> findByReconciliationDateAndType(
            LocalDate reconciliationDate,
            ReconciliationType type
    );

    /**
     * Find runs within a date range.
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Flux of runs within range
     */
    Flux<ReconciliationRun> findByReconciliationDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find runs within a date range for a specific type.
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param type Reconciliation type
     * @return Flux of matching runs
     */
    Flux<ReconciliationRun> findByReconciliationDateBetweenAndType(
            LocalDate startDate,
            LocalDate endDate,
            ReconciliationType type
    );

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

    /**
     * Check if a successful reconciliation exists for a date and type.
     *
     * <p>Useful to check if today's reconciliation has been run.</p>
     *
     * @param reconciliationDate The date
     * @param type Reconciliation type
     * @param status Expected status (usually COMPLETED)
     * @return Mono<Boolean> true if exists
     */
    Mono<Boolean> existsByReconciliationDateAndTypeAndStatus(
            LocalDate reconciliationDate,
            ReconciliationType type,
            ReconciliationStatus status
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

    /**
     * Find the last run number starting with a prefix.
     *
     * @param prefix Run number prefix
     * @return Mono containing the last run
     */
    Mono<ReconciliationRun> findFirstByRunNumberStartingWithOrderByRunNumberDesc(String prefix);

    // ========================================================================
    // USER QUERIES
    // ========================================================================

    /**
     * Find runs initiated by a specific user.
     *
     * @param userId The user ID
     * @return Flux of runs by that user
     */
    Flux<ReconciliationRun> findByRunBy(String userId);

    // ========================================================================
    // DISCREPANCY QUERIES
    // ========================================================================

    /**
     * Find runs with unresolved discrepancies.
     *
     * <p>Runs where unmatchedCount > resolvedCount.</p>
     *
     * @return Flux of runs with unresolved items
     */
    @Query("{ 'unmatchedCount': { $gt: 0 }, $expr: { $gt: ['$unmatchedCount', '$resolvedCount'] } }")
    Flux<ReconciliationRun> findWithUnresolvedDiscrepancies();

    /**
     * Find runs with high variance.
     *
     * <p>For alerting on significant discrepancies.</p>
     *
     * @param threshold Minimum variance amount
     * @return Flux of runs exceeding threshold
     */
    @Query("{ 'variance': { $gte: ?0 } }")
    Flux<ReconciliationRun> findWithVarianceGreaterThan(java.math.BigDecimal threshold);

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Get total runs for a type in a date range.
     *
     * @param type Reconciliation type
     * @param startDate Start of range
     * @param endDate End of range
     * @return Mono<Long> count
     */
    Mono<Long> countByTypeAndReconciliationDateBetween(
            ReconciliationType type,
            LocalDate startDate,
            LocalDate endDate
    );
}
