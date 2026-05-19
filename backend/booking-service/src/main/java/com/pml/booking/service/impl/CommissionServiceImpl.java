package com.pml.booking.service.impl;

import com.pml.booking.event.domain.CommissionEarnedEvent;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.CommissionRecord.CommissionStatus;
import com.pml.booking.repository.CommissionRecordRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
                    );

                    return commissionRepository.save(commission)
                            .doOnSuccess(c -> log.info("Pending commission created: {} ({})",
                                    c.getAmount(), ticketId));
                });
    }

    @Override
    @Transactional
    public Mono<CommissionRecord> markCommissionEarned(String ticketId) {
        log.info("Marking commission as earned for ticket: {}", ticketId);

        return commissionRepository.findByTicketId(ticketId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Commission record not found for ticket: " + ticketId)))
                .flatMap(commission -> {
                    // Record accounting entry for commission recognition
                    return accountingService.recordCommissionEarned(
                            commission.getId(),
                            commission.getEventId(),
                            commission.getAmount(),
                            "ZMW" // Default currency
                    ).flatMap(journalEntry -> {
                        commission.markEarned(journalEntry.getId());
                        return commissionRepository.save(commission);
                    });
                })
                .doOnSuccess(c -> log.info("Commission earned: {} for ticket: {}",
                        c.getAmount(), ticketId));
    }

    @Override
    @Transactional
    public Mono<Long> markEventCommissionsEarned(String eventId) {
        log.info("Marking all commissions as earned for event: {}", eventId);

        return commissionRepository.findByEventIdAndStatus(eventId, CommissionStatus.PENDING)
                .flatMap(commission -> {
                    String journalEntryId = generateJournalEntryId();
                    commission.markEarned(journalEntryId);
                    return commissionRepository.save(commission);
                })
                .count()
                .flatMap(count -> {
                    if (count > 0) {
                        return publishCommissionEarnedEvent(eventId)
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
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
                    commission.cancel(refundRequestId, reason, journalEntryId);
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
                        commission.clawback(refundRequestId, reason, journalEntry.getId());
                        return commissionRepository.save(commission);
                    });
                })
                .doOnSuccess(c -> log.warn("Commission clawed back for ticket: {} ({})",
                        ticketId, c.getAmount()));
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
    public Mono<BigDecimal> getTotalPendingCommission(String eventId) {
        return commissionRepository.sumAmountByEventIdAndStatus(eventId, CommissionStatus.PENDING);
    }

    @Override
    public Mono<BigDecimal> getTotalEarnedCommission(String eventId) {
        return commissionRepository.sumAmountByEventIdAndStatus(eventId, CommissionStatus.EARNED);
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

    private Mono<Void> publishCommissionEarnedEvent(String eventId) {
        return commissionRepository.findByEventIdAndStatus(eventId, CommissionStatus.EARNED)
                .collectList()
                .flatMap(commissions -> {
                    if (commissions.isEmpty()) {
                        return Mono.empty();
                    }

                    CommissionRecord first = commissions.get(0);
                    BigDecimal totalCommission = commissions.stream()
                            .map(CommissionRecord::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal grossRevenue = commissions.stream()
                            .map(CommissionRecord::getTicketPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    CommissionEarnedEvent event = new CommissionEarnedEvent(
                            eventId,
                            first.getOrganizerId(),
                            null, // Event title would need to be fetched
                            totalCommission,
                            commissions.size(),
                            grossRevenue
                    );

                    eventPublisher.publishEvent(event);
                    log.info("Published CommissionEarnedEvent for event: {}, total: {}",
                            eventId, totalCommission);
                    return Mono.empty();
                });
    }
}
