package com.pml.booking.repository;

import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import com.pml.booking.domain.model.StandaloneEscrowTransaction.TransactionType;
import com.pml.booking.repository.dto.AggregationResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive Repository for Standalone Escrow Transactions
 *
 * Provides reactive access to the escrow_transactions collection in MongoDB.
 * This repository enables independent querying of escrow transactions without
 * loading entire escrow account documents.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Account Queries</b>: All transactions for an escrow account</li>
 *   <li><b>Type/Category Filtering</b>: Credits, debits, sales, refunds, payouts</li>
 *   <li><b>Reference Lookups</b>: Find by ticket, payment, refund, payout ID</li>
 *   <li><b>Aggregations</b>: Sum by type for reconciliation</li>
 *   <li><b>Time Range Queries</b>: For reporting periods</li>
 * </ul>
 *
 * <h2>Reconciliation Usage</h2>
 * <pre>
 * // Verify escrow balance matches transaction history
 * Mono<BigDecimal> credits = repository.sumAmountByEscrowAccountIdAndType(
 *     accountId, TransactionType.CREDIT);
 * Mono<BigDecimal> debits = repository.sumAmountByEscrowAccountIdAndType(
 *     accountId, TransactionType.DEBIT);
 *
 * // Expected balance = credits - debits
 * Mono.zip(credits, debits)
 *     .map(t -> t.getT1().subtract(t.getT2()))
 *     .flatMap(expected -> escrowRepo.findById(accountId)
 *         .map(escrow -> escrow.getCurrentBalance().equals(expected)));
 * </pre>
 *
 * @see StandaloneEscrowTransaction
 * @since 1.0.0
 */
@Repository
public interface StandaloneEscrowTransactionRepository
        extends ReactiveMongoRepository<StandaloneEscrowTransaction, String> {

    // ========================================================================
    // ESCROW ACCOUNT QUERIES
    // ========================================================================

    /**
     * Find all transactions for an escrow account.
     *
     * <p>Returns transactions ordered by timestamp (most recent first).</p>
     *
     * @param escrowAccountId The escrow account ID
     * @return Flux of transactions for this account
     */
    Flux<StandaloneEscrowTransaction> findByEscrowAccountIdOrderByTimestampDesc(
            String escrowAccountId
    );

    /**
     * Find transactions for an escrow account with pagination.
     *
     * @param escrowAccountId The escrow account ID
     * @param pageable Pagination parameters
     * @return Flux of transactions
     */
    Flux<StandaloneEscrowTransaction> findByEscrowAccountId(
            String escrowAccountId,
            Pageable pageable
    );

    /**
     * Find transactions by escrow account and type.
     *
     * @param escrowAccountId The escrow account ID
     * @param type Transaction type (CREDIT or DEBIT)
     * @return Flux of matching transactions
     */
    Flux<StandaloneEscrowTransaction> findByEscrowAccountIdAndType(
            String escrowAccountId,
            TransactionType type
    );

    // ========================================================================
    // REFERENCE LOOKUPS (Business Entity Links)
    // ========================================================================

    /**
     * Find transaction by ticket ID.
     *
     * <p>A ticket may have multiple transactions (sale + refund).</p>
     *
     * @param ticketId The ticket ID
     * @return Flux of transactions for this ticket
     */
    Flux<StandaloneEscrowTransaction> findByTicketId(String ticketId);

    /**
     * Find transaction by payment intent ID.
     *
     * <p>Links back to the original payment flow.</p>
     *
     * @param paymentIntentId The payment intent ID
     * @return Mono containing the transaction if found
     */
    Mono<StandaloneEscrowTransaction> findByPaymentIntentId(String paymentIntentId);

    /**
     * Find transaction by chargeback ID.
     *
     * @param chargebackId The chargeback record ID
     * @return Mono containing the transaction if found
     */
    Mono<StandaloneEscrowTransaction> findByChargebackId(String chargebackId);

    /**
     * Find transaction by journal entry ID.
     *
     * <p>Links back to the double-entry bookkeeping record.</p>
     *
     * @param journalEntryId The journal entry ID
     * @return Flux of transactions for this journal entry (usually one)
     */
    Flux<StandaloneEscrowTransaction> findByJournalEntryId(String journalEntryId);

    /**
     * Find transactions for an account within a time range.
     *
     * @param escrowAccountId The escrow account ID
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return Flux of matching transactions
     */
    Flux<StandaloneEscrowTransaction> findByEscrowAccountIdAndTimestampBetween(
            String escrowAccountId,
            Instant startTime,
            Instant endTime
    );

    // ========================================================================
    // AGGREGATION QUERIES (for Reconciliation)
    // ========================================================================

    /**
     * Sum transaction amounts by escrow account and type.
     *
     * <p>Used for reconciliation: Total credits - Total debits = Expected balance</p>
     *
     * <p>Example:</p>
     * <pre>
     * sumAmountByEscrowAccountIdAndType("account-1", CREDIT) → K1000
     * sumAmountByEscrowAccountIdAndType("account-1", DEBIT)  → K200
     * Expected balance: K800
     * </pre>
     *
     * <p>Returns AggregationResult wrapper to avoid Java 21+ module reflection issues
     * with BigDecimal. Use .map(AggregationResult::getTotal) to extract the value.</p>
     *
     * @param escrowAccountId The escrow account ID
     * @param type Transaction type (CREDIT or DEBIT)
     * @return Mono<AggregationResult> containing total amount
     */
    @Aggregation(pipeline = {
        "{ $match: { escrowAccountId: ?0, type: ?1 } }",
        "{ $group: { _id: null, total: { $sum: '$amount' } } }",
        "{ $project: { _id: 0, total: 1 } }"
    })
    Mono<AggregationResult> sumAmountByEscrowAccountIdAndType(
            String escrowAccountId,
            TransactionType type
    );

    // ========================================================================
    // CATEGORY QUERIES
    // ========================================================================

    /**
     * Find all ticket sale transactions.
     *
     * @return Flux of ticket sale transactions
     */
    Flux<StandaloneEscrowTransaction> findByCategory(String category);

    // ========================================================================
    // RECONCILIATION QUERIES
    // ========================================================================

    /**
     * Find transactions that are not linked to a journal entry.
     *
     * <p>Used for reconciliation - all escrow transactions should have
     * corresponding journal entries.</p>
     *
     * @return Flux of transactions without journal entry links
     */
    Flux<StandaloneEscrowTransaction> findByJournalEntryIdIsNull();
}
