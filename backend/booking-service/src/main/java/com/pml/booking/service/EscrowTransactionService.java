package com.pml.booking.service;

import com.pml.booking.domain.enums.EscrowTransactionCategory;
import com.pml.booking.domain.enums.EscrowTransactionType;
import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Escrow Transaction Service Interface
 *
 * <p>Manages standalone escrow transactions that provide an independent audit trail
 * for all movements in and out of event escrow accounts. Unlike the embedded
 * transactions in EventEscrowAccount, these standalone records enable:</p>
 *
 * <ul>
 *   <li>Independent querying and reporting</li>
 *   <li>Cross-reference with journal entries</li>
 *   <li>Reconciliation between escrow and general ledger</li>
 *   <li>Historical analysis without loading full escrow accounts</li>
 * </ul>
 *
 * <h2>Transaction Categories</h2>
 * <ul>
 *   <li><b>TICKET_SALE</b>: Credit from ticket purchase</li>
 *   <li><b>REFUND</b>: Debit for customer refund</li>
 *   <li><b>PAYOUT</b>: Debit for organizer payout</li>
 *   <li><b>CHARGEBACK</b>: Debit for chargeback recovery</li>
 *   <li><b>ADJUSTMENT</b>: Manual adjustment (admin)</li>
 * </ul>
 *
 * <h2>Integration with Double-Entry</h2>
 * <p>Each escrow transaction should have a corresponding journal entry.
 * The journalEntryId field links the escrow movement to its general ledger
 * representation for reconciliation.</p>
 *
 * @see com.pml.booking.domain.model.StandaloneEscrowTransaction
 * @see com.pml.booking.service.AccountingService
 * @since 1.0.0
 */
public interface EscrowTransactionService {

    // ========================================================================
    // TRANSACTION QUERIES
    // ========================================================================

    /**
     * Finds a transaction by ID.
     *
     * @param id Transaction ID
     * @return The transaction, or empty if not found
     */
    Mono<StandaloneEscrowTransaction> findById(String id);

    /**
     * Finds all transactions for an escrow account.
     *
     * @param escrowAccountId The escrow account ID
     * @return All transactions for the account
     */
    Flux<StandaloneEscrowTransaction> findByEscrowAccountId(String escrowAccountId);

    /**
     * Finds transactions by category.
     *
     * @param category Transaction category
     * @return All transactions of the specified category
     */
    Flux<StandaloneEscrowTransaction> findByCategory(EscrowTransactionCategory category);

    /**
     * Finds transactions by type (CREDIT or DEBIT).
     *
     * @param type Transaction type
     * @return All transactions of the specified type
     */
    Flux<StandaloneEscrowTransaction> findByType(EscrowTransactionType type);

    /**
     * Finds transactions linked to a specific ticket.
     *
     * @param ticketId The ticket ID
     * @return All transactions for the ticket
     */
    Flux<StandaloneEscrowTransaction> findByTicketId(String ticketId);

    /**
     * Finds transactions linked to a specific payment intent.
     *
     * @param paymentIntentId The payment intent ID
     * @return All transactions for the payment
     */
    Flux<StandaloneEscrowTransaction> findByPaymentIntentId(String paymentIntentId);

    /**
     * Finds transactions linked to a specific journal entry.
     *
     * @param journalEntryId The journal entry ID
     * @return All transactions linked to the journal entry
     */
    Flux<StandaloneEscrowTransaction> findByJournalEntryId(String journalEntryId);

    // ========================================================================
    // BALANCE CALCULATIONS
    // ========================================================================

    /**
     * Calculates the current balance for an escrow account.
     *
     * <p>Balance = SUM(credits) - SUM(debits)</p>
     *
     * @param escrowAccountId The escrow account ID
     * @return Current balance
     */
    Mono<BigDecimal> calculateBalance(String escrowAccountId);

    /**
     * Calculates the balance for an escrow account as of a specific date.
     *
     * @param escrowAccountId The escrow account ID
     * @param asOfDate        Date to calculate balance up to
     * @return Balance as of the specified date
     */
    Mono<BigDecimal> calculateBalanceAsOf(String escrowAccountId, Instant asOfDate);

    /**
     * Sums all credits for an escrow account.
     *
     * @param escrowAccountId The escrow account ID
     * @return Total credits
     */
    Mono<BigDecimal> sumCredits(String escrowAccountId);

    /**
     * Sums all debits for an escrow account.
     *
     * @param escrowAccountId The escrow account ID
     * @return Total debits
     */
    Mono<BigDecimal> sumDebits(String escrowAccountId);

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    /**
     * Finds transactions without linked journal entries.
     *
     * <p>Used for reconciliation - all escrow transactions should have
     * corresponding journal entries.</p>
     *
     * @return Transactions missing journal entry links
     */
    Flux<StandaloneEscrowTransaction> findUnlinkedTransactions();
}
