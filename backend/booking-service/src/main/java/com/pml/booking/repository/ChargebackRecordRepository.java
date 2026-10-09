package com.pml.booking.repository;

import com.pml.shared.constants.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /**
     * Chargebacks against one event in the given states.
     *
     * <p>Used to answer "are there open disputes" for payout eligibility. It
     * counts by STATE rather than by presence, because a resolved chargeback
     * must not block a payout forever — an event that won a dispute two months
     * ago is not still disputed.
     */
    Mono<Long> countByEventIdAndStatusIn(
            String eventId, java.util.Collection<ChargebackStatus> statuses);

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
     * Count chargebacks by status.
     *
     * @param status The chargeback status
     * @return Mono<Long> count
     */
    Mono<Long> countByStatus(ChargebackStatus status);

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
