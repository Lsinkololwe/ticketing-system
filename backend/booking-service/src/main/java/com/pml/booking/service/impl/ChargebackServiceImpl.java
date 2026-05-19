package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.ChargebackReason;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.event.domain.ChargebackReceivedEvent;
import com.pml.booking.event.domain.ChargebackResolvedEvent;
import com.pml.booking.exception.ChargebackProcessingException;
import com.pml.booking.exception.InsufficientRecoveryFundsException;
import com.pml.booking.repository.ChargebackRecordRepository;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.service.PlatformAccountService;
import com.pml.shared.constants.PayoutRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chargeback Service Implementation
 *
 * <p>Manages the complete chargeback lifecycle from receipt through resolution
 * and fund recovery. Implements the recovery waterfall pattern for recouping
 * funds from various sources.</p>
 *
 * <h2>Chargeback Lifecycle</h2>
 * <pre>
 * RECEIVED → UNDER_REVIEW → ┬→ ACCEPTED → Recovery
 *                           └→ DISPUTED → WON/LOST
 * </pre>
 *
 * <h2>Recovery Waterfall</h2>
 * <ol>
 *   <li>ORGANIZER_ESCROW - Deduct from event's escrow</li>
 *   <li>ORGANIZER_FUTURE - Deduct from future payouts</li>
 *   <li>PLATFORM_RESERVE - Use platform's reserve fund</li>
 *   <li>WRITE_OFF - Record as bad debt (last resort)</li>
 * </ol>
 *
 * @see ChargebackService
 * @see ChargebackRecord
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackServiceImpl implements ChargebackService {

    private final ChargebackRecordRepository chargebackRepository;
    private final EventEscrowAccountRepository escrowRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final AccountingService accountingService;
    private final PlatformAccountService platformAccountService;
    private final ApplicationEventPublisher eventPublisher;

    // ========================================================================
    // CHARGEBACK RECEIVING
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChargebackRecord> receiveChargeback(
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
    ) {
        log.info("Receiving chargeback: id={}, ticket={}, amount={}, org={}",
                chargebackId, ticketId, chargebackAmount, organizationId);

        // Check for duplicate
        return chargebackRepository.findByChargebackId(chargebackId)
                .flatMap(existing -> {
                    log.warn("Chargeback already exists: {}", chargebackId);
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    ChargebackRecord record = ChargebackRecord.create(
                            chargebackId,
                            originalTransactionId,
                            ticketId,
                            eventId,
                            organizerId,
                            organizationId,
                            customerId,
                            originalAmount,
                            chargebackAmount,
                            chargebackFee != null ? chargebackFee : BigDecimal.valueOf(15), // Default fee
                            reason,
                            responseDeadline.toLocalDate()
                    );

                    return chargebackRepository.save(record)
                            .flatMap(saved -> {
                                /*
                                 * ACCOUNTING ENTRY: Record chargeback received
                                 *
                                 * When gateway notifies us of a chargeback, money has ALREADY
                                 * been taken from our bank account. We must record this:
                                 *
                                 * IN/OUT Flow:
                                 *   Chargeback Receivable (1023): IN  - We need to recover this
                                 *   Chargeback Fees (5030):       IN  - Direct expense to platform
                                 *   Operating Bank (1011):        OUT - Money was taken
                                 *
                                 * Journal Entry:
                                 *   DR Chargeback Recovery Receivable  K{chargebackAmount}
                                 *   DR Chargeback Fees Expense         K{chargebackFee}
                                 *      CR Operating Bank Account              K{total}
                                 */
                                return accountingService.recordChargebackReceived(
                                        saved.getChargebackId(),
                                        saved.getEventId(),
                                        saved.getTicketId(),
                                        saved.getChargebackAmount(),
                                        saved.getChargebackFee(),
                                        gatewayMetadata.getOrDefault("gatewayReference", chargebackId),
                                        saved.getCurrency()
                                ).thenReturn(saved);
                            })
                            .doOnSuccess(saved -> {
                                log.info("Chargeback recorded: {} with status {} (accounting entries created)",
                                        saved.getId(), saved.getStatus());
                                // Publish event for notification to organizer and finance team
                                eventPublisher.publishEvent(ChargebackReceivedEvent.of(
                                        saved.getId(),
                                        saved.getChargebackId(),
                                        saved.getOriginalTransactionId(),
                                        saved.getTicketId(),
                                        saved.getEventId(),
                                        saved.getOrganizerId(),
                                        saved.getCustomerId(),
                                        saved.getOriginalAmount(),
                                        saved.getChargebackAmount(),
                                        saved.getChargebackFee(),
                                        saved.getCurrency(),
                                        saved.getReason(),
                                        saved.getResponseDeadline().atStartOfDay()
                                ));
                            });
                }));
    }

    // ========================================================================
    // CHARGEBACK REVIEW
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChargebackRecord> startReview(String chargebackId, String reviewedBy, String notes) {
        log.info("Starting review for chargeback: {}", chargebackId);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.startReview(reviewedBy);
                    return chargebackRepository.save(record)
                            .doOnSuccess(saved -> log.info("Chargeback {} now under review", chargebackId));
                });
    }

    @Override
    public Mono<DisputeRecommendation> getDisputeRecommendation(String chargebackId) {
        return findByIdInternal(chargebackId)
                .map(record -> {
                    ChargebackReason reason = record.getReason();
                    int winRate = reason.getAverageWinRate();
                    double winProbability = winRate / 100.0;
                    String[] requiredEvidence = reason.getRequiredEvidence();
                    String difficulty = reason.getDisputeDifficulty();

                    // Build available evidence list based on what we might have
                    List<String> availableEvidence = new java.util.ArrayList<>();
                    availableEvidence.add("Ticket purchase confirmation");
                    availableEvidence.add("Terms of service acceptance");

                    // TODO: Check if ticket was validated (used)
                    // If ticket was scanned, add "Ticket validation proof"

                    // Dispute if win rate >= 40% and difficulty is not HARD
                    boolean shouldDispute = winProbability >= 0.4 &&
                            !difficulty.equals("HARD");

                    List<String> reasons = new java.util.ArrayList<>();
                    if (shouldDispute) {
                        reasons.add("Win probability (" + winRate + "%) suggests dispute is worthwhile");
                        reasons.add("Dispute difficulty is manageable: " + difficulty);
                    } else {
                        reasons.add("Low win probability (" + winRate + "%)");
                        reasons.add("High dispute difficulty: " + difficulty);
                        reasons.add("Consider accepting to save dispute effort");
                    }

                    return new DisputeRecommendation(
                            shouldDispute,
                            winProbability,
                            reasons,
                            List.of(requiredEvidence),
                            availableEvidence
                    );
                });
    }

    // ========================================================================
    // CHARGEBACK DECISIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChargebackRecord> acceptChargeback(String chargebackId, String acceptedBy, String reason) {
        log.info("Accepting chargeback: {}", chargebackId);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.accept(acceptedBy, reason);
                    return chargebackRepository.save(record)
                            .flatMap(this::startRecovery)
                            .doOnSuccess(saved -> log.info("Chargeback {} accepted, recovery initiated", chargebackId));
                });
    }

    @Override
    @Transactional
    public Mono<ChargebackRecord> disputeChargeback(
            String chargebackId,
            String disputedBy,
            DisputeEvidence evidenceBundle,
            String disputeNotes
    ) {
        log.info("Disputing chargeback: {}", chargebackId);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    // Build evidence string from bundle
                    StringBuilder evidence = new StringBuilder();
                    if (evidenceBundle.ticketValidationProof() != null) {
                        evidence.append("Ticket Validation: ").append(evidenceBundle.ticketValidationProof()).append("\n");
                    }
                    if (evidenceBundle.deliveryConfirmation() != null) {
                        evidence.append("Delivery: ").append(evidenceBundle.deliveryConfirmation()).append("\n");
                    }
                    if (evidenceBundle.termsAcceptanceProof() != null) {
                        evidence.append("Terms: ").append(evidenceBundle.termsAcceptanceProof()).append("\n");
                    }

                    List<String> evidenceDocs = new java.util.ArrayList<>();
                    evidenceDocs.add(evidence.toString());
                    record.submitDispute(disputedBy, evidenceDocs, disputeNotes);
                    return chargebackRepository.save(record)
                            .doOnSuccess(saved -> log.info("Chargeback {} dispute submitted", chargebackId));
                });
    }

    // ========================================================================
    // OUTCOME RECORDING
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChargebackRecord> recordWin(String chargebackId, LocalDateTime wonAt, String notes) {
        log.info("Recording chargeback win: {}", chargebackId);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.recordWin(notes != null ? notes : "System");
                    return chargebackRepository.save(record)
                            .doOnSuccess(saved -> {
                                log.info("Chargeback {} won - no recovery needed", chargebackId);
                                // Publish event for analytics and notifications
                                eventPublisher.publishEvent(ChargebackResolvedEvent.of(
                                        saved.getId(),
                                        saved.getChargebackId(),
                                        saved.getTicketId(),
                                        saved.getEventId(),
                                        saved.getOrganizerId(),
                                        saved.getStatus(),
                                        true, // disputeWon = true
                                        saved.getRecoveryStatus(),
                                        saved.getPrimaryFundSource(),
                                        saved.getTotalImpact(),
                                        saved.getRecoveredAmount(),
                                        saved.getWrittenOffAmount(),
                                        saved.getJournalEntryId(),
                                        LocalDateTime.now()
                                ));
                            });
                });
    }

    @Override
    @Transactional
    public Mono<ChargebackRecord> recordLoss(String chargebackId, LocalDateTime lostAt, String notes) {
        log.info("Recording chargeback loss: {}", chargebackId);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.recordLoss(notes != null ? notes : "System");
                    return chargebackRepository.save(record)
                            .flatMap(this::startRecovery)
                            .doOnSuccess(saved -> {
                                log.info("Chargeback {} lost - recovery initiated", chargebackId);
                                // Publish event for analytics and notifications
                                eventPublisher.publishEvent(ChargebackResolvedEvent.of(
                                        saved.getId(),
                                        saved.getChargebackId(),
                                        saved.getTicketId(),
                                        saved.getEventId(),
                                        saved.getOrganizerId(),
                                        saved.getStatus(),
                                        false, // disputeWon = false
                                        saved.getRecoveryStatus(),
                                        saved.getPrimaryFundSource(),
                                        saved.getTotalImpact(),
                                        saved.getRecoveredAmount(),
                                        saved.getWrittenOffAmount(),
                                        saved.getJournalEntryId(),
                                        LocalDateTime.now()
                                ));
                            });
                });
    }

    // ========================================================================
    // FUND RECOVERY
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChargebackRecord> startRecovery(String chargebackId) {
        return findByIdInternal(chargebackId)
                .flatMap(this::startRecovery);
    }

    private Mono<ChargebackRecord> startRecovery(ChargebackRecord record) {
        log.info("Starting recovery for chargeback: {}", record.getChargebackId());

        record.startRecovery();

        BigDecimal amountToRecover = record.getChargebackAmount()
                .add(record.getChargebackFee());

        // Recovery Waterfall Order:
        // 1. ORGANIZER_ESCROW - Money from the event where chargeback occurred
        // 2. ORGANIZER_FUTURE - Pending payouts from organizer's other events
        // 3. PLATFORM_RESERVE - Platform's safety buffer
        // 4. WRITE_OFF - Last resort, record as bad debt
        return attemptRecoveryFromEscrow(record, amountToRecover)
                .flatMap(result -> {
                    if (result.fullyRecovered) {
                        return chargebackRepository.save(record).map(saved -> new RecoveryResult(true, BigDecimal.ZERO));
                    }
                    // Continue to future payouts if not fully recovered
                    return attemptRecoveryFromFuturePayouts(record, result.remaining);
                })
                .flatMap(result -> {
                    if (result.fullyRecovered) {
                        return chargebackRepository.save(record).map(saved -> new RecoveryResult(true, BigDecimal.ZERO));
                    }
                    // Continue to platform reserve if not fully recovered
                    return attemptRecoveryFromReserve(record, result.remaining);
                })
                .flatMap(result -> {
                    if (result.fullyRecovered) {
                        return chargebackRepository.save(record);
                    }
                    // Write off remaining
                    return writeOffRemaining(record, result.remaining);
                });
    }

    private Mono<RecoveryResult> attemptRecoveryFromEscrow(ChargebackRecord record, BigDecimal amount) {
        log.info("Attempting escrow recovery for chargeback {}: {}", record.getChargebackId(), amount);

        return escrowRepository.findByEventId(record.getEventId())
                .flatMap(escrow -> {
                    BigDecimal available = escrow.getCurrentBalance();
                    BigDecimal toRecover = amount.min(available);

                    if (toRecover.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.just(new RecoveryResult(false, amount));
                    }

                    // Record recovery from escrow
                    record.recordRecovery(toRecover, ChargebackFundSource.ORGANIZER_ESCROW,
                            "ESCROW-" + record.getEventId());

                    // Create accounting entries
                    return accountingService.recordChargeback(
                            record.getChargebackId(),
                            record.getEventId(),
                            record.getTicketId(),
                            toRecover,
                            BigDecimal.ZERO,
                            "ORGANIZER_ESCROW",
                            record.getCurrency()
                    ).map(entry -> {
                        record.setJournalEntryId(entry.getId());
                        BigDecimal remaining = amount.subtract(toRecover);
                        return new RecoveryResult(remaining.compareTo(BigDecimal.ZERO) <= 0, remaining);
                    });
                })
                .defaultIfEmpty(new RecoveryResult(false, amount));
    }

    /**
     * Attempts to recover chargeback funds from organizer's pending payout requests.
     *
     * <h2>Business Context</h2>
     * <p>When escrow is insufficient, the organizer's pending payouts are a fair recovery source.
     * The organizer is responsible for chargebacks on their events, and these funds haven't
     * left the platform yet.</p>
     *
     * <h2>Recovery Priority for Payouts</h2>
     * <ol>
     *   <li>APPROVED status payouts (about to be paid)</li>
     *   <li>PENDING status payouts (can still be adjusted)</li>
     *   <li>PENDING_FINANCE_APPROVAL status payouts</li>
     * </ol>
     *
     * <h2>Example</h2>
     * <pre>
     * Chargeback Amount: K525 (K500 + K25 fee)
     * Recovered from Escrow: K200
     * Remaining: K325
     * Organizer's pending payouts:
     *   - PAY-001: K1,000 (APPROVED) → Deduct K325
     * Result: PAY-001 reduced to K675, chargeback fully recovered
     * </pre>
     *
     * @param record The chargeback record being processed
     * @param amount The amount to attempt to recover
     * @return RecoveryResult indicating success and remaining amount
     */
    private Mono<RecoveryResult> attemptRecoveryFromFuturePayouts(ChargebackRecord record, BigDecimal amount) {
        log.info("Attempting future payouts recovery for chargeback {}: {}",
                record.getChargebackId(), amount);

        // Collect payouts in priority order: APPROVED first, then PENDING statuses
        return payoutRequestRepository.findByOrganizerIdAndStatus(record.getOrganizerId(), PayoutRequestStatus.APPROVED)
                .concatWith(payoutRequestRepository.findByOrganizerIdAndStatus(record.getOrganizerId(), PayoutRequestStatus.PENDING))
                .concatWith(payoutRequestRepository.findByOrganizerIdAndStatus(record.getOrganizerId(), PayoutRequestStatus.PENDING_FINANCE_APPROVAL))
                .collectList()
                .flatMap(payouts -> {
                    if (payouts.isEmpty()) {
                        log.info("No pending payouts found for organizer {} to recover from",
                                record.getOrganizerId());
                        return Mono.just(new RecoveryResult(false, amount));
                    }

                    BigDecimal remaining = amount;
                    List<Mono<PayoutRequest>> updates = new ArrayList<>();
                    BigDecimal totalDeducted = BigDecimal.ZERO;

                    for (PayoutRequest payout : payouts) {
                        if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                        BigDecimal availableForDeduction = payout.getNetPayoutAmount();
                        if (availableForDeduction == null || availableForDeduction.compareTo(BigDecimal.ZERO) <= 0) {
                            continue;
                        }

                        BigDecimal deduction = remaining.min(availableForDeduction);
                        BigDecimal newAmount = availableForDeduction.subtract(deduction);

                        // Add audit note explaining the deduction
                        String note = String.format("[%s] CHARGEBACK RECOVERY: K%.2f deducted for chargeback %s (ticket: %s, event: %s)",
                                LocalDateTime.now(),
                                deduction,
                                record.getChargebackId(),
                                record.getTicketId(),
                                record.getEventId());

                        PayoutRequest updated = payout.toBuilder()
                                .netPayoutAmount(newAmount)
                                .notes((payout.getNotes() != null ? payout.getNotes() + "\n" : "") + note)
                                .build();

                        updates.add(payoutRequestRepository.save(updated));
                        remaining = remaining.subtract(deduction);
                        totalDeducted = totalDeducted.add(deduction);

                        log.info("Deducting K{} from payout {} (new balance: K{})",
                                deduction, payout.getRequestId(), newAmount);
                    }

                    if (totalDeducted.compareTo(BigDecimal.ZERO) > 0) {
                        // Record the recovery from future payouts
                        record.recordRecovery(totalDeducted, ChargebackFundSource.ORGANIZER_FUTURE,
                                "FUTURE_PAYOUTS-" + record.getOrganizerId());

                        log.info("Recovered K{} from future payouts for chargeback {}",
                                totalDeducted, record.getChargebackId());
                    }

                    BigDecimal finalRemaining = remaining;
                    return Flux.merge(updates)
                            .then(Mono.just(new RecoveryResult(
                                    finalRemaining.compareTo(BigDecimal.ZERO) <= 0,
                                    finalRemaining.max(BigDecimal.ZERO))));
                });
    }

    private Mono<RecoveryResult> attemptRecoveryFromReserve(ChargebackRecord record, BigDecimal amount) {
        log.info("Attempting reserve recovery for chargeback {}: {}", record.getChargebackId(), amount);

        return platformAccountService.hasSufficientBalance(
                        com.pml.booking.domain.enums.PlatformAccountType.RESERVE, amount)
                .flatMap(hasFunds -> {
                    if (!hasFunds) {
                        return Mono.just(new RecoveryResult(false, amount));
                    }

                    return platformAccountService.recoverFromReserve(record.getChargebackId(), amount)
                            .flatMap(account -> {
                                record.recordRecovery(amount, ChargebackFundSource.PLATFORM_RESERVE,
                                        "RESERVE-" + account.getId());

                                return accountingService.recordChargeback(
                                        record.getChargebackId(),
                                        record.getEventId(),
                                        record.getTicketId(),
                                        amount,
                                        BigDecimal.ZERO,
                                        "PLATFORM_RESERVE",
                                        record.getCurrency()
                                ).map(entry -> new RecoveryResult(true, BigDecimal.ZERO));
                            });
                });
    }

    private Mono<ChargebackRecord> writeOffRemaining(ChargebackRecord record, BigDecimal amount) {
        log.warn("Writing off {} for chargeback {}", amount, record.getChargebackId());

        record.writeOff(amount);

        return accountingService.recordChargeback(
                record.getChargebackId(),
                record.getEventId(),
                record.getTicketId(),
                amount,
                BigDecimal.ZERO,
                "WRITE_OFF",
                record.getCurrency()
        ).then(chargebackRepository.save(record));
    }

    private record RecoveryResult(boolean fullyRecovered, BigDecimal remaining) {}

    @Override
    @Transactional
    public Mono<ChargebackRecord> recoverFromSource(
            String chargebackId,
            ChargebackFundSource fundSource,
            BigDecimal amount
    ) {
        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.recordRecovery(amount, fundSource, fundSource.name() + "-" + System.currentTimeMillis());
                    return chargebackRepository.save(record);
                });
    }

    @Override
    @Transactional
    public Mono<ChargebackRecord> recordRecovery(
            String chargebackId,
            BigDecimal amount,
            ChargebackFundSource fundSource,
            String reference
    ) {
        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.recordRecovery(amount, fundSource, reference);
                    return chargebackRepository.save(record);
                });
    }

    @Override
    @Transactional
    public Mono<ChargebackRecord> writeOff(
            String chargebackId,
            BigDecimal amount,
            String approvedBy,
            String reason
    ) {
        log.info("Writing off {} for chargeback {}: {}", amount, chargebackId, reason);

        return findByIdInternal(chargebackId)
                .flatMap(record -> {
                    record.writeOff(amount);
                    return chargebackRepository.save(record);
                });
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    @Override
    public Flux<ChargebackRecord> findAll() {
        return chargebackRepository.findAll();
    }

    @Override
    public Mono<ChargebackRecord> findById(String id) {
        return chargebackRepository.findById(id);
    }

    @Override
    public Mono<ChargebackRecord> findByChargebackId(String chargebackId) {
        return chargebackRepository.findByChargebackId(chargebackId);
    }

    @Override
    public Flux<ChargebackRecord> findByOrganizerId(String organizerId) {
        return chargebackRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<ChargebackRecord> findByEventId(String eventId) {
        return chargebackRepository.findByEventId(eventId);
    }

    @Override
    public Flux<ChargebackRecord> findByStatus(ChargebackStatus status) {
        return chargebackRepository.findByStatus(status);
    }

    @Override
    public Flux<ChargebackRecord> findByRecoveryStatus(RecoveryStatus recoveryStatus) {
        return chargebackRepository.findByRecoveryStatus(recoveryStatus);
    }

    @Override
    public Flux<ChargebackRecord> findWithApproachingDeadline(int withinHours) {
        LocalDate deadline = LocalDate.now().plusDays(withinHours / 24);
        return chargebackRepository.findApproachingDeadline(deadline);
    }

    @Override
    public Flux<ChargebackRecord> findPendingRecovery() {
        return chargebackRepository.findPendingRecovery();
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<ChargebackStats> getOrganizerStats(String organizerId) {
        return chargebackRepository.findByOrganizerId(organizerId)
                .collectList()
                .map(this::calculateStats);
    }

    @Override
    public Mono<ChargebackStats> getPlatformStats() {
        return chargebackRepository.findAll()
                .collectList()
                .map(this::calculateStats);
    }

    private ChargebackStats calculateStats(List<ChargebackRecord> records) {
        long totalCount = records.size();
        long pendingCount = records.stream()
                .filter(r -> r.getStatus() == ChargebackStatus.RECEIVED ||
                        r.getStatus() == ChargebackStatus.UNDER_REVIEW)
                .count();
        long disputedCount = records.stream()
                .filter(r -> r.getStatus() == ChargebackStatus.DISPUTED)
                .count();
        long wonCount = records.stream()
                .filter(r -> r.getStatus() == ChargebackStatus.WON)
                .count();
        long lostCount = records.stream()
                .filter(r -> r.getStatus() == ChargebackStatus.LOST ||
                        r.getStatus() == ChargebackStatus.ACCEPTED)
                .count();

        BigDecimal totalAmount = records.stream()
                .map(ChargebackRecord::getChargebackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recoveredAmount = records.stream()
                .map(r -> r.getRecoveredAmount() != null ? r.getRecoveredAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal writtenOffAmount = records.stream()
                .filter(r -> r.getRecoveryStatus() == RecoveryStatus.WRITTEN_OFF)
                .map(ChargebackRecord::getChargebackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(recoveredAmount);

        double chargebackRate = 0.0; // Would need total transactions to calculate
        double winRate = (disputedCount + wonCount + lostCount) > 0
                ? (double) wonCount / (disputedCount + wonCount + lostCount)
                : 0.0;

        return new ChargebackStats(
                totalCount,
                pendingCount,
                disputedCount,
                wonCount,
                lostCount,
                totalAmount,
                recoveredAmount,
                writtenOffAmount.max(BigDecimal.ZERO),
                chargebackRate,
                winRate
        );
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<ChargebackRecord> findByIdInternal(String chargebackId) {
        return chargebackRepository.findById(chargebackId)
                .switchIfEmpty(chargebackRepository.findByChargebackId(chargebackId))
                .switchIfEmpty(Mono.error(new ChargebackProcessingException(
                        "Chargeback not found: " + chargebackId)));
    }
}
