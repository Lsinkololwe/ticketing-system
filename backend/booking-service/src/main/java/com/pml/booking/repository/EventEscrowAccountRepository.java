package com.pml.booking.repository;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.EventEscrowAccount.EscrowStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository for EventEscrowAccount entities.
 *
 * Provides reactive access to per-event escrow accounts stored in MongoDB.
 * Each event has one escrow account that holds organizer funds until payout.
 */
@Repository
public interface EventEscrowAccountRepository extends ReactiveMongoRepository<EventEscrowAccount, String> {

    /**
     * Find escrow account by account number.
     */
    Mono<EventEscrowAccount> findByAccountNumber(String accountNumber);

    /**
     * Find escrow account for a specific event.
     * Each event has exactly one escrow account.
     */
    Mono<EventEscrowAccount> findByEventId(String eventId);

    /**
     * Find all escrow accounts for an organizer.
     */
    Flux<EventEscrowAccount> findByOrganizerId(String organizerId);

    /**
     * Find escrow accounts by organizer and status.
     */
    Flux<EventEscrowAccount> findByOrganizerIdAndStatus(String organizerId, EscrowStatus status);

    /**
     * Find all escrow accounts with a specific status.
     */
    Flux<EventEscrowAccount> findByStatus(EscrowStatus status);

    /**
     * Find locked escrow accounts where hold period has passed.
     * Used by scheduled job to transition to PAYOUT_ELIGIBLE.
     */
    Flux<EventEscrowAccount> findByStatusAndLockUntilBefore(EscrowStatus status, LocalDateTime lockUntil);

    /**
     * Find payout-eligible accounts for an organizer.
     * These are accounts where organizer can request payout.
     */
    default Flux<EventEscrowAccount> findPayoutEligibleByOrganizerId(String organizerId) {
        return findByOrganizerIdAndStatus(organizerId, EscrowStatus.PAYOUT_ELIGIBLE);
    }

    /**
     * Count active escrow accounts for an organizer.
     */
    Mono<Long> countByOrganizerIdAndStatus(String organizerId, EscrowStatus status);

    /**
     * Check if escrow account exists for an event.
     */
    Mono<Boolean> existsByEventId(String eventId);

    /**
     * Check if account number exists.
     */
    Mono<Boolean> existsByAccountNumber(String accountNumber);

    /**
     * Find all escrow accounts with statuses in the given list.
     * Used for reconciliation to filter active accounts.
     *
     * @param statuses List of statuses to include
     * @return Flux of matching escrow accounts
     */
    Flux<EventEscrowAccount> findByStatusIn(java.util.Collection<EscrowStatus> statuses);

    /**
     * Find all escrow accounts excluding certain statuses.
     * Used for reconciliation to exclude CLOSED/CANCELLED accounts.
     *
     * @param statuses List of statuses to exclude
     * @return Flux of matching escrow accounts
     */
    Flux<EventEscrowAccount> findByStatusNotIn(java.util.Collection<EscrowStatus> statuses);
}
