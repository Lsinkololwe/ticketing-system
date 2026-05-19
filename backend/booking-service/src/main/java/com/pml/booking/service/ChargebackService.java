package com.pml.booking.service;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.ChargebackReason;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Chargeback Service Interface
 *
 * <p>Manages the complete chargeback lifecycle from receipt through resolution
 * and fund recovery. Chargebacks are involuntary reversals initiated by
 * customers through their payment provider.</p>
 *
 * <h2>Chargeback Lifecycle</h2>
 * <pre>
 * RECEIVED → UNDER_REVIEW → ┬→ ACCEPTED → Recovery Process
 *                           │
 *                           └→ DISPUTED → ┬→ WON (resolved)
 *                                         │
 *                                         └→ LOST → Recovery Process
 * </pre>
 *
 * <h2>Key Timeframes</h2>
 * <ul>
 *   <li><b>Response window</b>: 7-21 days to respond (varies by network)</li>
 *   <li><b>Dispute window</b>: 45-120 days for resolution</li>
 *   <li><b>Second chargeback</b>: Possible if initial dispute fails</li>
 * </ul>
 *
 * <h2>Recovery Waterfall</h2>
 * <p>When we lose a chargeback, funds are recovered in this priority:</p>
 * <ol>
 *   <li><b>ORGANIZER_ESCROW</b>: Deduct from event's escrow account</li>
 *   <li><b>ORGANIZER_FUTURE</b>: Deduct from organizer's future payouts</li>
 *   <li><b>PLATFORM_RESERVE</b>: Use platform's reserve fund</li>
 *   <li><b>WRITE_OFF</b>: Record as bad debt expense (last resort)</li>
 * </ol>
 *
 * <h2>Financial Impact</h2>
 * <ul>
 *   <li>Chargeback amount (full ticket price)</li>
 *   <li>Chargeback fee ($15-25 per occurrence)</li>
 *   <li>Commission clawback (platform loses commission)</li>
 *   <li>Potential organizer relationship impact</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.ChargebackRecord
 * @see com.pml.booking.service.AccountingService#recordChargeback
 * @since 1.0.0
 */
public interface ChargebackService {

    // ========================================================================
    // CHARGEBACK RECEIVING
    // ========================================================================

    /**
     * Records a new chargeback received from the payment gateway.
     *
     * <p>This is typically called by a webhook handler when the gateway
     * notifies us of a chargeback.</p>
     *
     * @param chargebackId         Gateway's chargeback ID
     * @param originalTransactionId Original payment transaction ID
     * @param ticketId             The ticket that was purchased
     * @param eventId              The event ID
     * @param organizerId          The organizer who received the funds
     * @param organizationId       The organization ID (for multi-tenant tracking)
     * @param customerId           The customer who initiated chargeback
     * @param originalAmount       Original transaction amount
     * @param chargebackAmount     Chargeback amount (may differ)
     * @param chargebackFee        Fee charged by gateway
     * @param currency             Currency code
     * @param reason               Chargeback reason category
     * @param responseDeadline     Deadline to respond/dispute
     * @param gatewayMetadata      Additional data from gateway
     * @return Created chargeback record
     */
    Mono<ChargebackRecord> receiveChargeback(
            String chargebackId,
            String originalTransactionId,
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            String customerId,
            BigDecimal originalAmount,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String currency,
            ChargebackReason reason,
            LocalDateTime responseDeadline,
            Map<String, String> gatewayMetadata
    );

    // ========================================================================
    // CHARGEBACK REVIEW
    // ========================================================================

    /**
     * Starts review of a chargeback.
     *
     * <p>Transitions status from RECEIVED to UNDER_REVIEW.
     * The review involves gathering evidence and deciding whether to
     * accept or dispute.</p>
     *
     * @param chargebackId The chargeback ID
     * @param reviewedBy   User starting the review
     * @param notes        Initial review notes
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> startReview(String chargebackId, String reviewedBy, String notes);

    /**
     * Gets dispute recommendation based on chargeback details.
     *
     * <p>Analyzes:</p>
     * <ul>
     *   <li>Chargeback reason (fraud vs. service issues)</li>
     *   <li>Available evidence (ticket usage, attendance)</li>
     *   <li>Historical win rates for similar cases</li>
     *   <li>Cost-benefit of dispute vs. acceptance</li>
     * </ul>
     *
     * @param chargebackId The chargeback ID
     * @return Recommendation with reasoning
     */
    Mono<DisputeRecommendation> getDisputeRecommendation(String chargebackId);

    /**
     * Dispute recommendation result.
     */
    record DisputeRecommendation(
            boolean shouldDispute,
            double estimatedWinProbability,
            List<String> reasons,
            List<String> requiredEvidence,
            List<String> availableEvidence
    ) {}

    // ========================================================================
    // CHARGEBACK DECISIONS
    // ========================================================================

    /**
     * Accepts a chargeback without dispute.
     *
     * <p>Use when:</p>
     * <ul>
     *   <li>Evidence clearly supports customer's claim</li>
     *   <li>Cost of dispute exceeds potential recovery</li>
     *   <li>Ticket was genuinely not delivered</li>
     * </ul>
     *
     * <p>Triggers the recovery process to recoup funds.</p>
     *
     * @param chargebackId The chargeback ID
     * @param acceptedBy   User accepting the chargeback
     * @param reason       Reason for acceptance
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> acceptChargeback(String chargebackId, String acceptedBy, String reason);

    /**
     * Submits a dispute for a chargeback.
     *
     * <p>Gathers and submits evidence to the gateway to fight the chargeback.</p>
     *
     * @param chargebackId    The chargeback ID
     * @param disputedBy      User submitting the dispute
     * @param evidenceBundle  Evidence documents/data
     * @param disputeNotes    Notes explaining the dispute
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> disputeChargeback(
            String chargebackId,
            String disputedBy,
            DisputeEvidence evidenceBundle,
            String disputeNotes
    );

    /**
     * Evidence bundle for chargeback disputes.
     */
    record DisputeEvidence(
            String ticketValidationProof,
            String customerCommunicationLog,
            String deliveryConfirmation,
            String termsAcceptanceProof,
            String additionalDocuments,
            Map<String, String> metadata
    ) {}

    // ========================================================================
    // OUTCOME RECORDING
    // ========================================================================

    /**
     * Records a dispute win.
     *
     * <p>Called when we successfully dispute a chargeback.
     * The funds are returned and no recovery is needed.</p>
     *
     * @param chargebackId The chargeback ID
     * @param wonAt        When the win was confirmed
     * @param notes        Notes about the resolution
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> recordWin(String chargebackId, LocalDateTime wonAt, String notes);

    /**
     * Records a dispute loss.
     *
     * <p>Called when we lose a chargeback dispute.
     * Triggers the recovery process to recoup funds from organizer.</p>
     *
     * @param chargebackId The chargeback ID
     * @param lostAt       When the loss was confirmed
     * @param notes        Notes about the resolution
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> recordLoss(String chargebackId, LocalDateTime lostAt, String notes);

    // ========================================================================
    // FUND RECOVERY
    // ========================================================================

    /**
     * Starts the fund recovery process.
     *
     * <p>Automatically follows the recovery waterfall:</p>
     * <ol>
     *   <li>Try organizer escrow</li>
     *   <li>Try future organizer payouts</li>
     *   <li>Try platform reserve</li>
     *   <li>Write off if all else fails</li>
     * </ol>
     *
     * @param chargebackId The chargeback ID
     * @return Updated chargeback record with recovery details
     */
    Mono<ChargebackRecord> startRecovery(String chargebackId);

    /**
     * Attempts to recover funds from a specific source.
     *
     * <p>Used for manual recovery or when automatic recovery
     * needs to skip certain sources.</p>
     *
     * @param chargebackId The chargeback ID
     * @param fundSource   Source to recover from
     * @param amount       Amount to recover
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> recoverFromSource(
            String chargebackId,
            ChargebackFundSource fundSource,
            BigDecimal amount
    );

    /**
     * Records a recovery payment.
     *
     * <p>Called when funds are successfully recovered from any source.</p>
     *
     * @param chargebackId  The chargeback ID
     * @param amount        Amount recovered
     * @param fundSource    Source of recovery
     * @param reference     Reference ID (escrow transaction, payout deduction, etc.)
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> recordRecovery(
            String chargebackId,
            BigDecimal amount,
            ChargebackFundSource fundSource,
            String reference
    );

    /**
     * Writes off unrecoverable chargeback amount.
     *
     * <p>Used when all recovery sources are exhausted.
     * Creates a bad debt expense journal entry.</p>
     *
     * @param chargebackId The chargeback ID
     * @param amount       Amount to write off
     * @param approvedBy   Admin approving the write-off
     * @param reason       Reason for write-off
     * @return Updated chargeback record
     */
    Mono<ChargebackRecord> writeOff(
            String chargebackId,
            BigDecimal amount,
            String approvedBy,
            String reason
    );

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Finds all chargeback records.
     *
     * @return All chargeback records
     */
    Flux<ChargebackRecord> findAll();

    /**
     * Finds a chargeback by ID.
     *
     * @param id The chargeback record ID
     * @return The chargeback record, or empty if not found
     */
    Mono<ChargebackRecord> findById(String id);

    /**
     * Finds a chargeback by gateway chargeback ID.
     *
     * @param chargebackId The gateway's chargeback ID
     * @return The chargeback record, or empty if not found
     */
    Mono<ChargebackRecord> findByChargebackId(String chargebackId);

    /**
     * Finds all chargebacks for an organizer.
     *
     * @param organizerId The organizer ID
     * @return All chargebacks for the organizer
     */
    Flux<ChargebackRecord> findByOrganizerId(String organizerId);

    /**
     * Finds all chargebacks for an event.
     *
     * @param eventId The event ID
     * @return All chargebacks for the event
     */
    Flux<ChargebackRecord> findByEventId(String eventId);

    /**
     * Finds chargebacks by status.
     *
     * @param status The chargeback status
     * @return All chargebacks with the specified status
     */
    Flux<ChargebackRecord> findByStatus(ChargebackStatus status);

    /**
     * Finds chargebacks by recovery status.
     *
     * @param recoveryStatus The recovery status
     * @return All chargebacks with the specified recovery status
     */
    Flux<ChargebackRecord> findByRecoveryStatus(RecoveryStatus recoveryStatus);

    /**
     * Finds chargebacks with approaching response deadlines.
     *
     * @param withinHours Hours until deadline
     * @return Chargebacks with deadlines within the specified hours
     */
    Flux<ChargebackRecord> findWithApproachingDeadline(int withinHours);

    /**
     * Finds chargebacks that need recovery action.
     *
     * <p>Returns chargebacks that are:</p>
     * <ul>
     *   <li>ACCEPTED or LOST status</li>
     *   <li>Recovery not complete</li>
     *   <li>Not written off</li>
     * </ul>
     *
     * @return Chargebacks pending recovery
     */
    Flux<ChargebackRecord> findPendingRecovery();

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Gets chargeback statistics for an organizer.
     *
     * @param organizerId The organizer ID
     * @return Chargeback statistics
     */
    Mono<ChargebackStats> getOrganizerStats(String organizerId);

    /**
     * Gets platform-wide chargeback statistics.
     *
     * @return Platform chargeback statistics
     */
    Mono<ChargebackStats> getPlatformStats();

    /**
     * Chargeback statistics.
     */
    record ChargebackStats(
            long totalCount,
            long pendingCount,
            long disputedCount,
            long wonCount,
            long lostCount,
            BigDecimal totalAmount,
            BigDecimal recoveredAmount,
            BigDecimal writtenOffAmount,
            double chargebackRate,
            double winRate
    ) {}
}
