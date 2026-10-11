package com.pml.booking.service.impl;

import com.pml.shared.constants.EscrowStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.security.TenantAccessGuard;
import com.pml.booking.service.EscrowService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

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

    /** Permission a caller needs to read an organization's escrow. */
    private static final String ESCROW_READ = "escrow:read";

    private final EventEscrowAccountRepository escrowRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final TenantAccessGuard tenantAccessGuard;

    @Override
    @Transactional
    public Mono<EventEscrowAccount> createEscrowAccount(
            String eventId,
            String eventTitle,
            String organizerId,
            Instant eventDate
    ) {
        log.info("Creating escrow account for event: {}", eventId);

        return escrowRepository.existsByEventId(eventId)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Escrow account already exists for event: {}", eventId);
                        return escrowRepository.findByEventId(eventId);
                    }

                    EventEscrowAccount escrow = EventEscrowAccount.create(
                            eventId, organizerId, eventDate, clock.instant());

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow account created: {}", e.getEventId()));
                });
    }

    @Override
    public Mono<EventEscrowAccount> openEscrowForPublishedEvent(String eventId,
                                                                String organizationId,
                                                                Instant startsAt) {
        return escrowRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.defer(() -> {
                    EventEscrowAccount escrow = EventEscrowAccount.create(eventId, null, startsAt, clock.instant());
                    escrow.setOrganizationId(organizationId);
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow account opened for event {} (organization {})",
                                    eventId, organizationId))
                            // Two deliveries can reach the save together. The unique index on eventId
                            // refuses the second, and losing that race means the escrow exists.
                            .onErrorResume(org.springframework.dao.DuplicateKeyException.class,
                                    e -> escrowRepository.findByEventId(eventId));
                }));
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
                    escrow.credit(amount, ticketId, paymentIntentId, description, clock.instant());
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow credited. New balance: {}", e.getCurrentBalance()));
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
                    // A repeated call for the same refund returns the account unchanged: the debit is
                    // taken once per refund request, however many times the refund is processed.
                    if (escrow.hasRefundDebit(refundRequestId)) {
                        return Mono.just(escrow);
                    }
                    escrow.debitForRefund(amount, ticketId, refundRequestId, description, clock.instant());
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
                    escrow.debitForPayout(amount, payoutRequestId, description, clock.instant());
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
                    escrow.hold();
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow locked until: {}", e.getHoldUntil()));
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
                            .doOnSuccess(e -> log.info("Escrow now payout-eligible: {}", e.getEventId()));
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
                    escrow.cancel(clock.instant());
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow cancelled: {}", e.getEventId()));
                });
    }

    // ========================================================================
    // ADMIN ESCROW MANAGEMENT OPERATIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<EventEscrowAccount> updateEscrowAccountStatus(String accountId, EscrowStatus status, String reason) {
        log.info("Admin updating escrow account {} status to {} (reason: {})", accountId, status, reason);

        return escrowAccountForCaller(accountId)
                .flatMap(escrow -> {
                    EscrowStatus oldStatus = escrow.getStatus();
                    escrow.setStatus(status);

                    // Set timestamps based on new status
                    if (status == EscrowStatus.PAYOUT_ELIGIBLE) {
                        escrow.setPayoutEligibleAt(clock.instant());
                    }

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} status changed from {} to {} (reason: {})",
                                    accountId, oldStatus, status, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> lockEscrowAccount(String accountId, Instant lockUntil, String reason) {
        log.info("Admin locking escrow account {} until {} (reason: {})", accountId, lockUntil, reason);

        return escrowAccountForCaller(accountId)
                .flatMap(escrow -> {
                    if (escrow.isClosed()) {
                        return Mono.error(new IllegalStateException("Cannot lock a closed escrow account"));
                    }

                    escrow.setStatus(EscrowStatus.HOLD);
                    escrow.setHoldUntil(lockUntil);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} locked until {} (reason: {})",
                                    accountId, lockUntil, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> unlockEscrowAccount(String accountId, String reason) {
        log.info("Admin unlocking escrow account {} (reason: {})", accountId, reason);

        return escrowAccountForCaller(accountId)
                .flatMap(escrow -> {
                    if (escrow.getStatus() != EscrowStatus.HOLD) {
                        return Mono.error(new IllegalStateException("Escrow account is not locked"));
                    }

                    // Unlocking makes it payout eligible
                    escrow.setStatus(EscrowStatus.PAYOUT_ELIGIBLE);
                    escrow.setPayoutEligibleAt(clock.instant());
                    escrow.setHoldUntil(null);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow {} unlocked (reason: {})", accountId, reason));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> closeEscrowAccount(String accountId, String reason) {
        log.info("Admin closing escrow account {} (reason: {})", accountId, reason);

        return escrowAccountForCaller(accountId)
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
        // Reached only from ADMIN/FINANCE-only queries and mutations: read the real request
        // scope rather than assuming platform authority.
        return escrowAccountForCaller(id);
    }

    /**
     * The escrow account the caller is entitled to act on, or {@code ESCROW_ACCOUNT_UNKNOWN}.
     */
    private Mono<EventEscrowAccount> escrowAccountForCaller(String id) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                escrowRepository.findById(id),
                organizationIds -> escrowRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                ErrorCode.ESCROW_ACCOUNT_UNKNOWN,
                "escrow account " + id));
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
    public Flux<EventEscrowAccount> findByOrganizationId(String actorUserId, String organizationId) {
        // The guard runs BEFORE the query, not as a filter on its results.
        // Checking afterwards would already have read another tenant's rows into
        // memory, and any logging or error message built from them leaks.
        return tenantAccessGuard.requireAccess(actorUserId, organizationId, ESCROW_READ)
                .flatMapMany(escrowRepository::findByOrganizationId);
    }

    @Override
    @Deprecated
    public Flux<EventEscrowAccount> findByOrganizerId(String organizerId) {
        return escrowRepository.findByOrganizerId(organizerId);
    }

    @Override
    @Deprecated
    public Flux<EventEscrowAccount> findPayoutEligibleByOrganizerId(String organizerId) {
        return escrowRepository.findPayoutEligibleByOrganizerId(organizerId);
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

                    // No title or organizer name is set: those denormalised fields are
                    // resolved from the event when a screen needs them, never invented here.
                    EventEscrowAccount escrow = EventEscrowAccount.create(
                            eventId, organizerId, clock.instant().plus(Duration.ofDays(30)), clock.instant());
                    escrow.setCurrency(currency);

                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Escrow account created: {}", e.getEventId()));
                });
    }

    @Override
    @Transactional
    public Mono<EventEscrowAccount> updateExpectedLockDate(String accountId, Instant newLockDate) {
        log.info("Updating expected lock date for escrow account: {} to {}", accountId, newLockDate);

        // Reached only from EventFinanceActivitiesImpl: a Temporal activity, no HTTP request,
        // the event's own lifecycle transition already authorized reaching this escrow.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.FINANCE_WORKFLOW, clock),
                        escrowRepository.findById(accountId),
                        organizationIds -> escrowRepository.findByIdAndOrganizationIdIn(accountId, organizationIds),
                        ErrorCode.ESCROW_ACCOUNT_UNKNOWN,
                        "escrow account " + accountId)
                .flatMap(escrow -> {
                    escrow.setHoldUntil(newLockDate.plus(Duration.ofDays(7)));
                    return escrowRepository.save(escrow)
                            .doOnSuccess(e -> log.info("Updated escrow lock date to: {}", e.getHoldUntil()));
                });
    }

    // ========================================================================
    // DASHBOARD & PAGINATION METHODS
    // ========================================================================

    @Override
    public Flux<EventEscrowAccount> findAll() {
        return escrowRepository.findAll();
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
    public Mono<BigDecimal> getTotalCredited() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalCredited)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getTotalDebited() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalDebited)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Mono<BigDecimal> getTotalRefunded() {
        return escrowRepository.findAll()
                .map(EventEscrowAccount::getTotalRefunded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Flux<EventEscrowAccount> findByOrganizationIdIn(Collection<String> organizationIds) {
        if (organizationIds.isEmpty()) {
            return Flux.empty();
        }
        return escrowRepository.findByOrganizationIdIn(organizationIds);
    }
}