package com.pml.booking.service.impl;

import com.pml.booking.event.domain.EscrowCreditedEvent;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.EventEscrowAccount.EscrowStatus;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Escrow Service Implementation
 *
 * Manages per-event escrow accounts that hold organizer funds until payout.
 * Key principle: This is LIABILITY money - we OWE it to organizers.
 *
 * Escrow Lifecycle:
 * 1. CREATED - Account created when event is published
 * 2. ACTIVE - Receiving funds from ticket sales
 * 3. LOCKED - Event happened, in 7-day hold period
 * 4. PAYOUT_ELIGIBLE - Hold period passed, organizer can request payout
 * 5. CLOSED - All funds paid out
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EventEscrowAccountRepository escrowRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Mono<EventEscrowAccount> createEscrowAccount(
            String eventId,
            String eventTitle,
            String organizerId,
            String organizerName,
            LocalDateTime eventDate
    ) {
        log.info("Creating escrow account for event: {}", eventId);

        return escrowRepository.existsByEventId(eventId)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Escrow account already exists for event: {}", eventId);
                        return escrowRepository.findByEventId(eventId);
                    }

                    EventEscrowAccount escrow = EventEscrowAccount.create(
                            eventId,
                            eventTitle,
                            organizerId,
                            organizerName,
                            eventDate
                    );

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow account created: {}", e.getAccountNumber()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> creditEscrow(
            String eventId,
            BigDecimal amount,
            String ticketId,
            String paymentIntentId,
            String description
    ) {
        log.info("Crediting {} to escrow for event: {}", amount, eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.credit(amount, ticketId, paymentIntentId, description);
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> {
                                log.info("Escrow credited. New balance: {}", e.getCurrentBalance());
                                publishEscrowCredited(e, ticketId, paymentIntentId, amount, "TICKET_SALE");
                            });
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> debitForRefund(
            String eventId,
            BigDecimal amount,
            String ticketId,
            String refundRequestId,
            String description
    ) {
        log.info("Debiting {} from escrow for refund, event: {}", amount, eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.debitForRefund(amount, ticketId, refundRequestId, description);
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow debited for refund. New balance: {}", e.getCurrentBalance()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> debitForPayout(
            String eventId,
            BigDecimal amount,
            String payoutRequestId,
            String description
    ) {
        log.info("Debiting {} from escrow for payout, event: {}", amount, eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.debitForPayout(amount, payoutRequestId, description);
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow debited for payout. New balance: {}", e.getCurrentBalance()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> lockEscrow(String eventId) {
        log.info("Locking escrow for event: {}", eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.lock();
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow locked until: {}", e.getLockUntil()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> markPayoutEligible(String eventId) {
        log.info("Marking escrow payout-eligible for event: {}", eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.markPayoutEligible();
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow now payout-eligible: {}", e.getAccountNumber()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> cancelEscrow(String eventId) {
        log.info("Cancelling escrow for event: {}", eventId);

        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found for event: " + eventId)))
                .flatMap(escrow -> {
                    escrow.cancel();
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow cancelled: {}", e.getAccountNumber()));
                });
    }

    // ========================================================================
    // ADMIN ESCROW MANAGEMENT OPERATIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<EventEscrowAccount> updateEscrowAccountStatus(String accountId, EscrowStatus status, String reason) {
        log.info("Admin updating escrow account {} status to {} (reason: {})", accountId, status, reason);

        return escrowRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found: " + accountId)))
                .flatMap(escrow -> {
                    EscrowStatus oldStatus = escrow.getStatus();
                    escrow.setStatus(status);

                    // Set timestamps based on new status
                    if (status == EscrowStatus.PAYOUT_ELIGIBLE) {
                        escrow.setPayoutEligibleAt(java.time.Instant.now());
                    }

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} status changed from {} to {} (reason: {})",
                                    accountId, oldStatus, status, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> lockEscrowAccount(String accountId, LocalDateTime lockUntil, String reason) {
        log.info("Admin locking escrow account {} until {} (reason: {})", accountId, lockUntil, reason);

        return escrowRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found: " + accountId)))
                .flatMap(escrow -> {
                    if (escrow.isClosed()) {
                        return Mono.error(new IllegalStateException("Cannot lock a closed escrow account"));
                    }

                    escrow.setStatus(EscrowStatus.LOCKED);
                    escrow.setLockUntil(lockUntil);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} locked until {} (reason: {})",
                                    accountId, lockUntil, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> unlockEscrowAccount(String accountId, String reason) {
        log.info("Admin unlocking escrow account {} (reason: {})", accountId, reason);

        return escrowRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found: " + accountId)))
                .flatMap(escrow -> {
                    if (escrow.getStatus() != EscrowStatus.LOCKED) {
                        return Mono.error(new IllegalStateException("Escrow account is not locked"));
                    }

                    // Unlocking makes it payout eligible
                    escrow.setStatus(EscrowStatus.PAYOUT_ELIGIBLE);
                    escrow.setPayoutEligibleAt(java.time.Instant.now());
                    escrow.setLockUntil(null);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} unlocked (reason: {})", accountId, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> closeEscrowAccount(String accountId, String reason) {
        log.info("Admin closing escrow account {} (reason: {})", accountId, reason);

        return escrowRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found: " + accountId)))
                .flatMap(escrow -> {
                    if (escrow.getCurrentBalance().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        return Mono.error(new IllegalStateException(
                                "Cannot close escrow with remaining balance: " + escrow.getCurrentBalance()));
                    }

                    escrow.setStatus(EscrowStatus.CLOSED);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} closed (reason: {})", accountId, reason));
                });
    }

    @Override
    public Mono<EventEscrowAccount> findById(String id) {
        return escrowRepository.findById(id);
    }

    @Override
    public Mono<EventEscrowAccount> findByEventId(String eventId) {
        return escrowRepository.findByEventId(eventId);
    }

    @Override
    public Mono<EventEscrowAccount> findByAccountNumber(String accountNumber) {
        return escrowRepository.findByAccountNumber(accountNumber);
    }

    @Override
    public Flux<EventEscrowAccount> findByOrganizerId(String organizerId) {
        return escrowRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<EventEscrowAccount> findPayoutEligibleByOrganizerId(String organizerId) {
        return escrowRepository.findPayoutEligibleByOrganizerId(organizerId);
    }

    @Override
    public Flux<EventEscrowAccount> findLockedEscrowsWithPassedHoldPeriod() {
        return escrowRepository.findByStatusAndLockUntilBefore(
                EscrowStatus.LOCKED,
                LocalDateTime.now()
        );
    }

    @Override
    @Transactional
    public Mono<Long> processHoldPeriodExpirations() {
        log.info("Processing hold period expirations");

        return findLockedEscrowsWithPassedHoldPeriod()
                .flatMap(escrow -> {
                    escrow.markPayoutEligible();
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} now payout-eligible", e.getAccountNumber()));
                })
                .count()
                .doOnSuccess(count -> log.info("Processed {} hold period expirations", count));
    }

    @Override
    public Mono<BigDecimal> getTotalPayoutEligibleBalance(String organizerId) {
        return findPayoutEligibleByOrganizerId(organizerId)
                .map(EventEscrowAccount::getAvailableForPayout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> createEscrowAccount(String eventId, String organizerId, String currency) {
        log.info("Creating escrow account for event: {} (simplified)", eventId);

        return escrowRepository.existsByEventId(eventId)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Escrow account already exists for event: {}", eventId);
                        return escrowRepository.findByEventId(eventId);
                    }

                    EventEscrowAccount escrow = EventEscrowAccount.create(
                            eventId,
                            "Event " + eventId,
                            organizerId,
                            "Organizer",
                            LocalDateTime.now().plusDays(30)
                    );
                    escrow.setCurrency(currency);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow account created: {}", e.getAccountNumber()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> updateExpectedLockDate(String accountId, LocalDateTime newLockDate) {
        log.info("Updating expected lock date for escrow account: {} to {}", accountId, newLockDate);

        return escrowRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Escrow account not found: " + accountId)))
                .flatMap(escrow -> {
                    escrow.setLockUntil(newLockDate.plusDays(7));
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Updated escrow lock date to: {}", e.getLockUntil()));
                });
    }

    private void publishEscrowCredited(
            EventEscrowAccount escrow,
            String ticketId,
            String transactionRef,
            BigDecimal amount,
            String category
    ) {
        EscrowCreditedEvent event = new EscrowCreditedEvent(
                escrow.getId(),
                escrow.getEventId(),
                escrow.getOrganizerId(),
                ticketId,
                transactionRef,
                amount,
                escrow.getCurrentBalance(),
                escrow.getCurrency(),
                category
        );
        eventPublisher.publishEvent(event);
        log.debug("Published EscrowCreditedEvent for ticket: {}", ticketId);
    }

    // ========================================================================
    // DASHBOARD & PAGINATION METHODS
    // ========================================================================

    @Override
    public Flux<EventEscrowAccount> findAll() {
        return escrowRepository.findAll();
    }

    @Override
    public Mono<Long> countAll() {
        return escrowRepository.count();
    }

    @Override
    public Mono<Long> countByStatus(EscrowStatus status) {
        return escrowRepository.findByStatus(status).count();
    }

    @Override
    public Mono<BigDecimal> getTotalEscrowBalance() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getTotalDeposits() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalDeposits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getTotalWithdrawals() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalWithdrawals)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getTotalRefunds() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalRefunds)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
