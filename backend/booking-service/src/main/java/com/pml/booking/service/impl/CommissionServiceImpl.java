package com.pml.booking.service.impl;

import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.CommissionRecord.CommissionStatus;
import com.pml.booking.repository.CommissionRecordRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Commission Service Implementation
 *
 * Implements the Two-Stage Commission Model:
 *
 * Stage 1 - PENDING: Recorded at purchase, not yet revenue
 * Stage 2 - EARNED: After event + 7-day hold, becomes actual revenue
 *
 * This approach simplifies refunds:
 * - Refund before event → Cancel pending commission (no money movement)
 * - Refund after event → Clawback earned commission (rare, actual debit)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRecordRepository commissionRepository;
    private final AccountingService accountingService;

    /** The injected platform clock. */
    private final java.time.Clock clock;
    @Value("${platform.commission.rate:0.05}")
    private BigDecimal commissionRate;

    @Override
    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    @Override
    public BigDecimal calculateCommission(BigDecimal ticketPrice) {
        if (ticketPrice == null || ticketPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return ticketPrice.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateNetAmount(BigDecimal ticketPrice) {
        if (ticketPrice == null || ticketPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal commission = calculateCommission(ticketPrice);
        return ticketPrice.subtract(commission);
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> createPendingCommission(
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            BigDecimal ticketPrice
    ) {
        log.info("Creating pending commission for ticket: {} (org: {})", ticketId, organizationId);

        return commissionRepository.existsByTicketId(ticketId)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Commission record already exists for ticket: {}", ticketId);
                        return commissionRepository.findByTicketId(ticketId);
                    }

                    CommissionRecord commission = CommissionRecord.createPending(
                            ticketId,
                            eventId,
                            organizerId,
                            organizationId,
                            ticketPrice,
                            commissionRate
                    , clock.instant());

                    return commissionRepository.save(commission)
                            .doOnSuccess(c -> log.info("Pending commission created: {} ({})",
                                    c.getAmount(), ticketId));
                });
    }

    @Override
    @Transactional
    public Mono<Long> markEventCommissionsEarned(String eventId) {
        log.info("Marking all commissions as earned for event: {}", eventId);

        return commissionRepository.findByEventIdAndStatus(eventId, CommissionStatus.PENDING)
                .flatMap(commission -> {
                    String journalEntryId = generateJournalEntryId();
                    commission.markEarned(journalEntryId, clock.instant());
                    return commissionRepository.save(commission);
                })
                .count()
                .doOnSuccess(count -> log.info("Marked {} commissions as earned for event: {}", count, eventId));
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> cancelPendingCommission(
            String ticketId,
            String refundRequestId,
            String reason
    ) {
        log.info("Cancelling pending commission for ticket: {}", ticketId);

        return commissionRepository.findByTicketId(ticketId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Commission record not found for ticket: " + ticketId)))
                .flatMap(commission -> {
                    String journalEntryId = generateJournalEntryId();
                    commission.cancel(refundRequestId, reason, journalEntryId, clock.instant());
                    return commissionRepository.save(commission)
                            .doOnSuccess(c -> log.info("Pending commission cancelled for ticket: {}", ticketId));
                });
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> clawbackEarnedCommission(
            String ticketId,
            String refundRequestId,
            String reason
    ) {
        log.info("Clawing back earned commission for ticket: {}", ticketId);

        return commissionRepository.findByTicketId(ticketId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Commission record not found for ticket: " + ticketId)))
                .flatMap(commission -> {
                    // Record accounting entry for commission clawback
                    return accountingService.recordCommissionClawback(
                            commission.getId(),
                            refundRequestId,
                            commission.getAmount(),
                            commission.isEarned(), // wasEarned
                            "ZMW" // Default currency
                    ).flatMap(journalEntry -> {
                        commission.clawback(refundRequestId, reason, journalEntry.getId(), clock.instant());
                        return commissionRepository.save(commission);
                    });
                })
                .doOnSuccess(c -> log.warn("Commission clawed back for ticket: {} ({})",
                        ticketId, c.getAmount()));
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> reduceForPartialRefund(String ticketId, String refundRequestId,
                                                         BigDecimal refundAmount, BigDecimal commissionShare) {
        return commissionRepository.findByTicketId(ticketId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Commission record not found for ticket: " + ticketId)))
                .flatMap(commission -> {
                    commission.reduce(refundRequestId, refundAmount, commissionShare, clock.instant());
                    return commissionRepository.save(commission);
                });
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> reinstatePendingCommission(String ticketId, String refundRequestId) {
        return commissionRepository.findByTicketId(ticketId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Commission record not found for ticket: " + ticketId)))
                .flatMap(commission -> {
                    // A partial refund reduced the record rather than cancelling it; undoing it is
                    // giving the reduction back, and finding none means this was not that kind of refund.
                    if (commission.restoreReduction(refundRequestId)) {
                        return commissionRepository.save(commission);
                    }
                    if (commission.isPending()) {
                        // Already reinstated by an earlier attempt at this activity.
                        return Mono.just(commission);
                    }
                    if (!commission.isCancelled() || !refundRequestId.equals(commission.getRefundRequestId())) {
                        // Not this refund's cancellation to reverse — an earned commission's
                        // clawback is a real ledger entry and is reconciled by an operator, not
                        // reversed automatically here.
                        return Mono.just(commission);
                    }
                    commission.reinstate(clock.instant());
                    return commissionRepository.save(commission)
                            .doOnSuccess(c -> log.info(
                                    "Pending commission for ticket {} reinstated: refund {} did not reach the provider",
                                    ticketId, refundRequestId));
                });
    }

    @Override
    public Mono<CommissionRecord> findByTicketId(String ticketId) {
        return commissionRepository.findByTicketId(ticketId);
    }

    @Override
    public Flux<CommissionRecord> findByEventId(String eventId) {
        return commissionRepository.findByEventId(eventId);
    }

    @Override
    public Flux<CommissionRecord> findByEventIdAndStatus(String eventId, CommissionStatus status) {
        return commissionRepository.findByEventIdAndStatus(eventId, status);
    }

    @Override
    public Flux<CommissionRecord> findByOrganizerId(String organizerId) {
        return commissionRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Mono<BigDecimal> getTotalPlatformEarnedCommission() {
        return commissionRepository.findByStatus(CommissionStatus.EARNED)
                .map(CommissionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateJournalEntryId() {
        return "JE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

}
