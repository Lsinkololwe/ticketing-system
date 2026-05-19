package com.pml.booking.repository;

import com.pml.booking.domain.enums.ChargebackReason;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Reactive Repository for Chargeback Records
 *
 * Provides reactive access to the chargebacks collection in MongoDB.
 * This repository supports the full chargeback lifecycle from receipt
 * through resolution and fund recovery.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Status Queries</b>: Find by chargeback status</li>
 *   <li><b>Deadline Monitoring</b>: Find chargebacks approaching deadline</li>
 *   <li><b>Recovery Tracking</b>: Find chargebacks pending recovery</li>
 *   <li><b>Organizer Queries</b>: Find chargebacks for an organizer</li>
 *   <li><b>Statistics</b>: Counts and sums for reporting</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <pre>
 * // Find chargebacks needing response before deadline
 * repository.findByStatusInAndResponseDeadlineBefore(
 *     List.of(RECEIVED, UNDER_REVIEW),
 *     LocalDate.now().plusDays(3))
 *     .doOnNext(cb -> alertService.sendDeadlineWarning(cb));
 *
 * // Find chargebacks pending recovery
 * repository.findByRecoveryStatus(RecoveryStatus.NOT_STARTED)
 *     .filter(ChargebackRecord::isLoss)
 *     .flatMap(recoveryService::startRecovery);
 * </pre>
 *
 * @see ChargebackRecord
 * @since 1.0.0
 */
@Repository
public interface ChargebackRecordRepository extends ReactiveMongoRepository<ChargebackRecord, String> {

    // ========================================================================
    // EXTERNAL ID LOOKUPS
    // ========================================================================

    /**
     * Find chargeback by external gateway ID.
     *
     * @param chargebackId External chargeback ID from gateway
     * @return Mono containing the chargeback if found
     */
    Mono<ChargebackRecord> findByChargebackId(String chargebackId);

    /**
     * Check if chargeback with external ID exists.
     *
     * @param chargebackId External chargeback ID
     * @return Mono<Boolean> true if exists
     */
    Mono<Boolean> existsByChargebackId(String chargebackId);

    // ========================================================================
    // TRANSACTION REFERENCE LOOKUPS
    // ========================================================================

    /**
     * Find chargeback by original transaction ID.
     *
     * @param originalTransactionId The original payment transaction ID
     * @return Mono containing the chargeback if found
     */
    Mono<ChargebackRecord> findByOriginalTransactionId(String originalTransactionId);

    /**
     * Find chargebacks by ticket ID.
     *
     * <p>A ticket could theoretically have multiple chargebacks (rare).</p>
     *
     * @param ticketId The ticket ID
     * @return Flux of chargebacks for this ticket
     */
    Flux<ChargebackRecord> findByTicketId(String ticketId);

    /**
     * Find chargebacks by event ID.
     *
     * @param eventId The event ID
     * @return Flux of chargebacks for this event
     */
    Flux<ChargebackRecord> findByEventId(String eventId);

    // ========================================================================
    // ORGANIZER QUERIES
    // ========================================================================

    /**
     * Find all chargebacks for an organizer.
     *
     * @param organizerId The organizer ID
     * @return Flux of chargebacks for this organizer
     */
    Flux<ChargebackRecord> findByOrganizerId(String organizerId);

    /**
     * Find chargebacks by organizer and status.
     *
     * @param organizerId The organizer ID
     * @param status Chargeback status
     * @return Flux of matching chargebacks
     */
    Flux<ChargebackRecord> findByOrganizerIdAndStatus(String organizerId, ChargebackStatus status);

    /**
     * Count chargebacks by organizer.
     *
     * @param organizerId The organizer ID
     * @return Mono<Long> count
     */
    Mono<Long> countByOrganizerId(String organizerId);

    /**
     * Count chargebacks by organizer and status.
     *
     * @param organizerId The organizer ID
     * @param status Chargeback status
     * @return Mono<Long> count
     */
    Mono<Long> countByOrganizerIdAndStatus(String organizerId, ChargebackStatus status);

    // ========================================================================
    // STATUS QUERIES
    // ========================================================================

    /**
     * Find chargebacks by status.
     *
     * @param status The chargeback status
     * @return Flux of chargebacks with this status
     */
    Flux<ChargebackRecord> findByStatus(ChargebackStatus status);

    /**
     * Find chargebacks by status with pagination.
     *
     * @param status The chargeback status
     * @param pageable Pagination parameters
     * @return Flux of chargebacks
     */
    Flux<ChargebackRecord> findByStatusOrderByReceivedAtDesc(
            ChargebackStatus status,
            Pageable pageable
    );

    /**
     * Count chargebacks by status.
     *
     * @param status The chargeback status
     * @return Mono<Long> count
     */
    Mono<Long> countByStatus(ChargebackStatus status);

    /**
     * Find chargebacks that are still in progress.
     *
     * @return Flux of in-progress chargebacks
     */
    @Query("{ 'status': { $in: ['RECEIVED', 'UNDER_REVIEW', 'DISPUTED'] } }")
    Flux<ChargebackRecord> findInProgress();

    /**
     * Find chargebacks that resulted in a loss.
     *
     * @return Flux of lost chargebacks
     */
    @Query("{ 'status': { $in: ['ACCEPTED', 'LOST'] } }")
    Flux<ChargebackRecord> findLosses();

    // ========================================================================
    // DEADLINE QUERIES
    // ========================================================================

    /**
     * Find chargebacks with deadline before a date.
     *
     * <p>For deadline monitoring and alerts.</p>
     *
     * @param deadline The deadline date
     * @return Flux of chargebacks approaching deadline
     */
    @Query("{ 'status': { $in: ['RECEIVED', 'UNDER_REVIEW'] }, 'responseDeadline': { $lte: ?0 } }")
    Flux<ChargebackRecord> findApproachingDeadline(LocalDate deadline);

    /**
     * Find chargebacks that are past deadline and not yet disputed.
     *
     * @param today Current date
     * @return Flux of past-deadline chargebacks
     */
    @Query("{ 'status': { $in: ['RECEIVED', 'UNDER_REVIEW'] }, 'responseDeadline': { $lt: ?0 } }")
    Flux<ChargebackRecord> findPastDeadline(LocalDate today);

    // ========================================================================
    // RECOVERY QUERIES
    // ========================================================================

    /**
     * Find chargebacks by recovery status.
     *
     * @param recoveryStatus The recovery status
     * @return Flux of chargebacks with this recovery status
     */
    Flux<ChargebackRecord> findByRecoveryStatus(RecoveryStatus recoveryStatus);

    /**
     * Find chargebacks pending recovery.
     *
     * <p>Losses that haven't started recovery yet.</p>
     *
     * @return Flux of chargebacks needing recovery
     */
    @Query("{ 'status': { $in: ['ACCEPTED', 'LOST'] }, 'recoveryStatus': 'NOT_STARTED' }")
    Flux<ChargebackRecord> findPendingRecovery();

    /**
     * Find chargebacks with recovery in progress.
     *
     * @return Flux of chargebacks with ongoing recovery
     */
    Flux<ChargebackRecord> findByRecoveryStatusIn(java.util.List<RecoveryStatus> statuses);

    /**
     * Count chargebacks by recovery status.
     *
     * @param recoveryStatus The recovery status
     * @return Mono<Long> count
     */
    Mono<Long> countByRecoveryStatus(RecoveryStatus recoveryStatus);

    // ========================================================================
    // REASON QUERIES
    // ========================================================================

    /**
     * Find chargebacks by reason.
     *
     * @param reason The chargeback reason
     * @return Flux of chargebacks with this reason
     */
    Flux<ChargebackRecord> findByReason(ChargebackReason reason);

    /**
     * Count chargebacks by reason.
     *
     * <p>For analytics on chargeback patterns.</p>
     *
     * @param reason The chargeback reason
     * @return Mono<Long> count
     */
    Mono<Long> countByReason(ChargebackReason reason);

    // ========================================================================
    // TIME RANGE QUERIES
    // ========================================================================

    /**
     * Find chargebacks received within a time range.
     *
     * @param startTime Start of range
     * @param endTime End of range
     * @return Flux of chargebacks received in this period
     */
    Flux<ChargebackRecord> findByReceivedAtBetween(Instant startTime, Instant endTime);

    /**
     * Find chargebacks resolved within a time range.
     *
     * @param startTime Start of range
     * @param endTime End of range
     * @return Flux of chargebacks resolved in this period
     */
    Flux<ChargebackRecord> findByResolvedAtBetween(Instant startTime, Instant endTime);

    // ========================================================================
    // CUSTOMER QUERIES
    // ========================================================================

    /**
     * Find chargebacks by customer ID.
     *
     * <p>For fraud detection - customers with multiple chargebacks.</p>
     *
     * @param customerId The customer ID
     * @return Flux of chargebacks by this customer
     */
    Flux<ChargebackRecord> findByCustomerId(String customerId);

    /**
     * Count chargebacks by customer.
     *
     * @param customerId The customer ID
     * @return Mono<Long> count
     */
    Mono<Long> countByCustomerId(String customerId);

    // ========================================================================
    // AGGREGATION QUERIES
    // ========================================================================

    /**
     * Sum chargeback amounts by organizer and status.
     *
     * @param organizerId The organizer ID
     * @return Mono<BigDecimal> total chargeback amount
     */
    default Mono<BigDecimal> sumChargebackAmountByOrganizerId(String organizerId) {
        return findByOrganizerId(organizerId)
                .map(ChargebackRecord::getChargebackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Sum recovered amounts by organizer.
     *
     * @param organizerId The organizer ID
     * @return Mono<BigDecimal> total recovered amount
     */
    default Mono<BigDecimal> sumRecoveredAmountByOrganizerId(String organizerId) {
        return findByOrganizerId(organizerId)
                .map(cb -> cb.getRecoveredAmount() != null ? cb.getRecoveredAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Sum written-off amounts by organizer.
     *
     * @param organizerId The organizer ID
     * @return Mono<BigDecimal> total written-off amount
     */
    default Mono<BigDecimal> sumWrittenOffAmountByOrganizerId(String organizerId) {
        return findByOrganizerId(organizerId)
                .map(cb -> cb.getWrittenOffAmount() != null ? cb.getWrittenOffAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========================================================================
    // JOURNAL ENTRY LOOKUPS
    // ========================================================================

    /**
     * Find chargeback by journal entry ID.
     *
     * @param journalEntryId The journal entry ID
     * @return Mono containing the chargeback if found
     */
    Mono<ChargebackRecord> findByJournalEntryId(String journalEntryId);
}
