package com.pml.booking.migration;

import com.pml.booking.domain.enums.EscrowTransactionCategory;
import com.pml.booking.domain.model.EscrowTransaction;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.StandaloneEscrowTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Migration Service for Escrow Transactions
 *
 * <p>One-time migration to extract embedded EscrowTransaction objects from
 * EventEscrowAccount documents into the standalone escrow_transactions collection.</p>
 *
 * <h2>Why This Migration?</h2>
 * <p>The original design embedded transactions within escrow accounts. This works
 * for small numbers of transactions but has issues:</p>
 * <ul>
 *   <li>Document size limits (16MB MongoDB limit)</li>
 *   <li>Can't query transactions independently</li>
 *   <li>Full document must be loaded to access any transaction</li>
 *   <li>No efficient aggregation across all transactions</li>
 * </ul>
 *
 * <h2>Standalone Benefits</h2>
 * <ul>
 *   <li>Unlimited transactions per account</li>
 *   <li>Independent querying and aggregation</li>
 *   <li>Efficient balance calculation via aggregation</li>
 *   <li>Better audit trail with journal entry links</li>
 * </ul>
 *
 * <h2>Migration Process</h2>
 * <ol>
 *   <li>Load all EventEscrowAccount documents</li>
 *   <li>For each account, extract embedded transactions</li>
 *   <li>Convert to StandaloneEscrowTransaction</li>
 *   <li>Save to escrow_transactions collection</li>
 *   <li>Verify balance consistency</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * <p>This migration is idempotent - running it multiple times will not
 * create duplicate transactions. Existing transactions are skipped.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowTransactionMigrationService {

    private final EventEscrowAccountRepository escrowAccountRepository;
    private final StandaloneEscrowTransactionRepository transactionRepository;

    /**
     * Migrate all embedded escrow transactions to standalone collection.
     *
     * @return Migration result summary
     */
    @Transactional
    public Mono<MigrationResult> migrateAllTransactions() {
        log.info("Starting escrow transaction migration...");

        AtomicInteger totalAccounts = new AtomicInteger(0);
        AtomicInteger totalTransactions = new AtomicInteger(0);
        AtomicInteger skippedTransactions = new AtomicInteger(0);

        return escrowAccountRepository.findAll()
                .doOnNext(account -> totalAccounts.incrementAndGet())
                .flatMap(account -> migrateAccountTransactions(account, totalTransactions, skippedTransactions))
                .then(Mono.defer(() -> {
                    MigrationResult result = new MigrationResult(
                            totalAccounts.get(),
                            totalTransactions.get(),
                            skippedTransactions.get()
                    );
                    log.info("Migration completed: {}", result);
                    return Mono.just(result);
                }));
    }

    /**
     * Migrate transactions for a single escrow account.
     */
    private Mono<Void> migrateAccountTransactions(
            EventEscrowAccount account,
            AtomicInteger totalTransactions,
            AtomicInteger skippedTransactions
    ) {
        if (account.getTransactions() == null || account.getTransactions().isEmpty()) {
            log.debug("No transactions to migrate for account: {}", account.getId());
            return Mono.empty();
        }

        log.info("Migrating {} transactions for account: {}",
                account.getTransactions().size(), account.getAccountNumber());

        BigDecimal runningBalance = BigDecimal.ZERO;

        return Flux.fromIterable(account.getTransactions())
                .concatMap(embeddedTx -> {
                    // Check if already migrated
                    return transactionRepository.findByPaymentIntentId(embeddedTx.getPaymentIntentId())
                            .hasElement()
                            .flatMap(exists -> {
                                if (exists) {
                                    skippedTransactions.incrementAndGet();
                                    log.debug("Transaction already exists, skipping: {}",
                                            embeddedTx.getPaymentIntentId());
                                    return Mono.empty();
                                }

                                // Convert embedded transaction to standalone
                                StandaloneEscrowTransaction standalone = convertToStandalone(
                                        account, embeddedTx);

                                totalTransactions.incrementAndGet();
                                return transactionRepository.save(standalone)
                                        .doOnSuccess(saved -> log.debug("Migrated transaction: {}",
                                                saved.getId()));
                            });
                })
                .then();
    }

    /**
     * Convert an embedded EscrowTransaction to a StandaloneEscrowTransaction.
     */
    private StandaloneEscrowTransaction convertToStandalone(
            EventEscrowAccount account,
            EscrowTransaction embedded
    ) {
        // Determine transaction type and category
        StandaloneEscrowTransaction.TransactionType type;
        EscrowTransactionCategory category;

        // Parse from category string or infer from amount/description
        String categoryStr = embedded.getCategory();
        if (categoryStr != null) {
            if (categoryStr.contains("SALE") || categoryStr.contains("CREDIT")) {
                type = StandaloneEscrowTransaction.TransactionType.CREDIT;
                category = EscrowTransactionCategory.TICKET_SALE;
            } else if (categoryStr.contains("REFUND")) {
                type = StandaloneEscrowTransaction.TransactionType.DEBIT;
                category = EscrowTransactionCategory.REFUND;
            } else if (categoryStr.contains("PAYOUT")) {
                type = StandaloneEscrowTransaction.TransactionType.DEBIT;
                category = EscrowTransactionCategory.PAYOUT;
            } else if (categoryStr.contains("CHARGEBACK")) {
                type = StandaloneEscrowTransaction.TransactionType.DEBIT;
                category = EscrowTransactionCategory.CHARGEBACK;
            } else {
                // Default based on description
                type = inferType(embedded.getDescription());
                category = EscrowTransactionCategory.ADJUSTMENT;
            }
        } else {
            type = inferType(embedded.getDescription());
            category = EscrowTransactionCategory.ADJUSTMENT;
        }

        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(account.getId())
                .type(type)
                .category(category.name())
                .amount(embedded.getAmount().abs())
                .balanceAfter(embedded.getBalanceAfter())
                .ticketId(embedded.getTicketId())
                .paymentIntentId(embedded.getPaymentIntentId())
                .description(embedded.getDescription())
                .timestamp(embedded.getTimestamp() != null
                        ? embedded.getTimestamp()
                        : Instant.now())
                .createdAt(embedded.getTimestamp() != null
                        ? embedded.getTimestamp()
                        : Instant.now())
                .version(0L)
                .build();
    }

    /**
     * Infer transaction type from description.
     */
    private StandaloneEscrowTransaction.TransactionType inferType(String description) {
        if (description == null) {
            return StandaloneEscrowTransaction.TransactionType.CREDIT;
        }

        String lower = description.toLowerCase();
        if (lower.contains("refund") || lower.contains("payout") ||
            lower.contains("debit") || lower.contains("withdrawal")) {
            return StandaloneEscrowTransaction.TransactionType.DEBIT;
        }
        return StandaloneEscrowTransaction.TransactionType.CREDIT;
    }

    /**
     * Verify that migrated transactions match account balance.
     *
     * @param accountId Account to verify
     * @return true if balance matches, false otherwise
     */
    public Mono<Boolean> verifyMigration(String accountId) {
        log.info("Verifying migration for account: {}", accountId);

        return Mono.zip(
                escrowAccountRepository.findById(accountId)
                        .map(EventEscrowAccount::getCurrentBalance)
                        .defaultIfEmpty(BigDecimal.ZERO),
                transactionRepository.calculateBalanceByEscrowAccountId(accountId)
                        .defaultIfEmpty(BigDecimal.ZERO)
        ).map(tuple -> {
            BigDecimal accountBalance = tuple.getT1();
            BigDecimal calculatedBalance = tuple.getT2();
            boolean matches = accountBalance.compareTo(calculatedBalance) == 0;

            if (!matches) {
                log.warn("Balance mismatch for account {}: recorded={}, calculated={}",
                        accountId, accountBalance, calculatedBalance);
            } else {
                log.info("Balance verified for account {}: {}", accountId, accountBalance);
            }

            return matches;
        });
    }

    /**
     * Verify migration for all accounts.
     *
     * @return Number of accounts with mismatched balances
     */
    public Mono<Long> verifyAllMigrations() {
        log.info("Verifying migration for all accounts...");

        return escrowAccountRepository.findAll()
                .flatMap(account -> verifyMigration(account.getId())
                        .map(matches -> matches ? 0L : 1L))
                .reduce(0L, Long::sum)
                .doOnSuccess(mismatches -> {
                    if (mismatches == 0) {
                        log.info("All accounts verified successfully");
                    } else {
                        log.warn("{} accounts have balance mismatches", mismatches);
                    }
                });
    }

    /**
     * Migration result summary.
     */
    public record MigrationResult(
            int accountsProcessed,
            int transactionsMigrated,
            int transactionsSkipped
    ) {
        @Override
        public String toString() {
            return String.format(
                    "MigrationResult[accounts=%d, migrated=%d, skipped=%d]",
                    accountsProcessed, transactionsMigrated, transactionsSkipped
            );
        }
    }
}
