package com.pml.booking.service;

import com.pml.booking.domain.model.EventEscrowAccount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Escrow Service Interface
 *
 * Manages per-event escrow accounts that hold organizer funds until payout.
 * Implements the escrow lifecycle: CREATED → ACTIVE → LOCKED → PAYOUT_ELIGIBLE → CLOSED
 */
public interface EscrowService {

    /**
     * Create a new escrow account for an event.
     * Called when an event is published.
     *
     * @param eventId       The event ID
     * @param eventTitle    The event title
     * @param organizerId   The organizer's user ID
     * @param organizerName The organizer's name
     * @param eventDate     The event date (used to calculate lock period)
     * @return Created escrow account
     */
    Mono<EventEscrowAccount> createEscrowAccount(
            String eventId,
            String eventTitle,
            String organizerId,
            String organizerName,
            LocalDateTime eventDate
    );

    /**
     * Credit funds to escrow (from ticket sale).
     * Called after successful payment.
     *
     * @param eventId         The event ID
     * @param amount          Net amount after commission
     * @param ticketId        The ticket ID for audit
     * @param paymentIntentId The payment intent ID
     * @param description     Description of the credit
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> creditEscrow(
            String eventId,
            BigDecimal amount,
            String ticketId,
            String paymentIntentId,
            String description
    );

    /**
     * Debit funds from escrow for refund.
     *
     * @param eventId         The event ID
     * @param amount          Amount to refund
     * @param ticketId        The ticket ID
     * @param refundRequestId The refund request ID
     * @param description     Description of the debit
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> debitForRefund(
            String eventId,
            BigDecimal amount,
            String ticketId,
            String refundRequestId,
            String description
    );

    /**
     * Debit funds from escrow for organizer payout.
     *
     * @param eventId         The event ID
     * @param amount          Amount to pay out
     * @param payoutRequestId The payout request ID
     * @param description     Description of the payout
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> debitForPayout(
            String eventId,
            BigDecimal amount,
            String payoutRequestId,
            String description
    );

    /**
     * Lock escrow after event completion.
     * Starts the 7-day hold period.
     *
     * @param eventId The event ID
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> lockEscrow(String eventId);

    /**
     * Mark escrow as payout eligible.
     * Called after hold period passes.
     *
     * @param eventId The event ID
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> markPayoutEligible(String eventId);

    /**
     * Cancel escrow account.
     * Called when event is cancelled and all refunds processed.
     *
     * @param eventId The event ID
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> cancelEscrow(String eventId);

    // ========================================================================
    // ADMIN ESCROW MANAGEMENT OPERATIONS
    // ========================================================================

    /**
     * Update escrow account status (admin operation).
     *
     * @param accountId Account ID
     * @param status    New status
     * @param reason    Reason for the change
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> updateEscrowAccountStatus(String accountId, EventEscrowAccount.EscrowStatus status, String reason);

    /**
     * Lock escrow account until a specific date (admin operation).
     *
     * @param accountId Account ID
     * @param lockUntil Date until which the account is locked
     * @param reason    Reason for the lock
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> lockEscrowAccount(String accountId, LocalDateTime lockUntil, String reason);

    /**
     * Unlock escrow account (admin operation).
     *
     * @param accountId Account ID
     * @param reason    Reason for the unlock
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> unlockEscrowAccount(String accountId, String reason);

    /**
     * Close escrow account (admin operation).
     *
     * @param accountId Account ID
     * @param reason    Reason for closing
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> closeEscrowAccount(String accountId, String reason);

    /**
     * Find escrow account by ID.
     */
    Mono<EventEscrowAccount> findById(String id);

    /**
     * Find escrow account by event ID.
     */
    Mono<EventEscrowAccount> findByEventId(String eventId);

    /**
     * Find escrow account by account number.
     */
    Mono<EventEscrowAccount> findByAccountNumber(String accountNumber);

    /**
     * Find all escrow accounts for an organizer.
     */
    Flux<EventEscrowAccount> findByOrganizerId(String organizerId);

    /**
     * Find payout-eligible accounts for an organizer.
     */
    Flux<EventEscrowAccount> findPayoutEligibleByOrganizerId(String organizerId);

    /**
     * Find locked escrows where hold period has passed.
     * Used by scheduled job to transition to PAYOUT_ELIGIBLE.
     */
    Flux<EventEscrowAccount> findLockedEscrowsWithPassedHoldPeriod();

    /**
     * Process escrows that have passed their hold period.
     * Transitions from LOCKED to PAYOUT_ELIGIBLE.
     */
    Mono<Long> processHoldPeriodExpirations();

    /**
     * Get total payout-eligible balance for an organizer.
     */
    Mono<BigDecimal> getTotalPayoutEligibleBalance(String organizerId);

    /**
     * Create a new escrow account with minimal parameters.
     * Used when event is published via Service Bus message.
     *
     * @param eventId     The event ID
     * @param organizerId The organizer's user ID
     * @param currency    The currency code
     * @return Created escrow account
     */
    Mono<EventEscrowAccount> createEscrowAccount(String eventId, String organizerId, String currency);

    /**
     * Lock escrow for a specific event.
     * Alias for lockEscrow for clarity in message consumers.
     *
     * @param eventId The event ID
     * @return Updated escrow account
     */
    default Mono<EventEscrowAccount> lockEscrowForEvent(String eventId) {
        return lockEscrow(eventId);
    }

    /**
     * Update the expected lock date for an escrow account.
     * Used when an event is rescheduled.
     *
     * @param accountId   The escrow account ID
     * @param newLockDate The new expected lock date
     * @return Updated escrow account
     */
    Mono<EventEscrowAccount> updateExpectedLockDate(String accountId, LocalDateTime newLockDate);

    // ========================================================================
    // DASHBOARD & PAGINATION METHODS
    // ========================================================================

    /**
     * Find all escrow accounts.
     */
    Flux<EventEscrowAccount> findAll();

    /**
     * Count all escrow accounts.
     */
    Mono<Long> countAll();

    /**
     * Count escrow accounts by status.
     */
    Mono<Long> countByStatus(EventEscrowAccount.EscrowStatus status);

    /**
     * Get total balance across all escrow accounts.
     */
    Mono<BigDecimal> getTotalEscrowBalance();

    /**
     * Get total deposits across all escrow accounts.
     */
    Mono<BigDecimal> getTotalDeposits();

    /**
     * Get total withdrawals across all escrow accounts.
     */
    Mono<BigDecimal> getTotalWithdrawals();

    /**
     * Get total refunds across all escrow accounts.
     */
    Mono<BigDecimal> getTotalRefunds();
}
