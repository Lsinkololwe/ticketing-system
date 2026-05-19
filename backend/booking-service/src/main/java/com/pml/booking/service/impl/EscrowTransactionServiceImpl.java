package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.EscrowTransactionCategory;
import com.pml.booking.domain.enums.EscrowTransactionType;
import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import com.pml.booking.domain.model.StandaloneEscrowTransaction.TransactionType;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.StandaloneEscrowTransactionRepository;
import com.pml.booking.repository.dto.AggregationResult;
import com.pml.booking.service.EscrowTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Escrow Transaction Service Implementation
 *
 * <p>Manages standalone escrow transactions that provide an independent
 * audit trail for all escrow account movements. Each transaction links
 * to its corresponding journal entry for reconciliation.</p>
 *
 * <h2>Transaction Types</h2>
 * <ul>
 *   <li><b>CREDIT</b>: Funds flowing INTO escrow (ticket sales)</li>
 *   <li><b>DEBIT</b>: Funds flowing OUT of escrow (refunds, payouts, chargebacks)</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>Links to JournalEntry via journalEntryId</li>
 *   <li>References tickets, payments, refunds, payouts</li>
 *   <li>Provides balance verification for reconciliation</li>
 * </ul>
 *
 * @see EscrowTransactionService
 * @see StandaloneEscrowTransaction
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowTransactionServiceImpl implements EscrowTransactionService {

    private final StandaloneEscrowTransactionRepository transactionRepository;
    private final EventEscrowAccountRepository escrowAccountRepository;

    // ========================================================================
    // TRANSACTION RECORDING
    // ========================================================================

    @Override
    @Transactional
    public Mono<StandaloneEscrowTransaction> recordCredit(
            String escrowAccountId,
            BigDecimal amount,
            EscrowTransactionCategory category,
            String ticketId,
            String paymentIntentId,
            String description,
            String journalEntryId
    ) {
        log.info("Recording credit of {} to escrow {}: {}", amount, escrowAccountId, description);

        return calculateBalance(escrowAccountId)
                .defaultIfEmpty(BigDecimal.ZERO)
                .flatMap(currentBalance -> {
                    BigDecimal newBalance = currentBalance.add(amount);

                    StandaloneEscrowTransaction transaction = StandaloneEscrowTransaction.credit(
                            escrowAccountId,
                            amount,
                            newBalance,
                            category,
                            ticketId,
                            paymentIntentId,
                            description
                    );
                    transaction.setJournalEntryId(journalEntryId);

                    return transactionRepository.save(transaction)
                            .doOnSuccess(saved -> log.info(
                                    "Escrow credit recorded: {} balance now {}",
                                    saved.getId(), saved.getBalanceAfter()));
                });
    }

    @Override
    @Transactional
    public Mono<StandaloneEscrowTransaction> recordDebit(
            String escrowAccountId,
            BigDecimal amount,
            EscrowTransactionCategory category,
            String referenceId,
            String description,
            String journalEntryId
    ) {
        log.info("Recording debit of {} from escrow {}: {}", amount, escrowAccountId, description);

        return calculateBalance(escrowAccountId)
                .defaultIfEmpty(BigDecimal.ZERO)
                .flatMap(currentBalance -> {
                    BigDecimal newBalance = currentBalance.subtract(amount);

                    StandaloneEscrowTransaction transaction = StandaloneEscrowTransaction.debit(
                            escrowAccountId,
                            amount,
                            newBalance,
                            category,
                            referenceId,
                            description
                    );
                    transaction.setJournalEntryId(journalEntryId);

                    return transactionRepository.save(transaction)
                            .doOnSuccess(saved -> log.info(
                                    "Escrow debit recorded: {} balance now {}",
                                    saved.getId(), saved.getBalanceAfter()));
                });
    }

    @Override
    @Transactional
    public Mono<StandaloneEscrowTransaction> recordTransaction(StandaloneEscrowTransaction transaction) {
        log.info("Recording escrow transaction: {} {} {}",
                transaction.getType(), transaction.getAmount(), transaction.getEscrowAccountId());

        return transactionRepository.save(transaction)
                .doOnSuccess(saved -> log.info("Escrow transaction recorded: {}", saved.getId()));
    }

    // ========================================================================
    // TRANSACTION QUERIES
    // ========================================================================

    @Override
    public Mono<StandaloneEscrowTransaction> findById(String id) {
        return transactionRepository.findById(id);
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByEscrowAccountId(String escrowAccountId) {
        return transactionRepository.findByEscrowAccountIdOrderByTimestampDesc(escrowAccountId);
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByEscrowAccountIdAndDateRange(
            String escrowAccountId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        Instant startInstant = startDate.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.atZone(ZoneId.systemDefault()).toInstant();
        return transactionRepository.findByEscrowAccountIdAndTimestampBetween(
                escrowAccountId, startInstant, endInstant);
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByCategory(EscrowTransactionCategory category) {
        return transactionRepository.findByCategory(category.name());
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByType(EscrowTransactionType type) {
        TransactionType internalType = type == EscrowTransactionType.CREDIT
                ? TransactionType.CREDIT
                : TransactionType.DEBIT;
        return transactionRepository.findByEscrowAccountIdAndType(null, internalType);
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByTicketId(String ticketId) {
        return transactionRepository.findByTicketId(ticketId);
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByPaymentIntentId(String paymentIntentId) {
        return transactionRepository.findByPaymentIntentId(paymentIntentId)
                .flux();
    }

    @Override
    public Flux<StandaloneEscrowTransaction> findByJournalEntryId(String journalEntryId) {
        return transactionRepository.findByJournalEntryId(journalEntryId);
    }

    // ========================================================================
    // BALANCE CALCULATIONS
    // ========================================================================

    @Override
    public Mono<BigDecimal> calculateBalance(String escrowAccountId) {
        return Mono.zip(
                sumCredits(escrowAccountId),
                sumDebits(escrowAccountId)
        ).map(tuple -> tuple.getT1().subtract(tuple.getT2()));
    }

    @Override
    public Mono<BigDecimal> calculateBalanceAsOf(String escrowAccountId, LocalDateTime asOfDate) {
        Instant startInstant = Instant.EPOCH;
        Instant endInstant = asOfDate.atZone(ZoneId.systemDefault()).toInstant();

        return transactionRepository.findByEscrowAccountIdAndTimestampBetween(
                        escrowAccountId,
                        startInstant,
                        endInstant
                )
                .reduce(BigDecimal.ZERO, (balance, transaction) -> {
                    if (transaction.isCredit()) {
                        return balance.add(transaction.getAmount());
                    } else {
                        return balance.subtract(transaction.getAmount());
                    }
                });
    }

    @Override
    public Mono<BigDecimal> sumCredits(String escrowAccountId) {
        return transactionRepository.sumAmountByEscrowAccountIdAndType(
                        escrowAccountId,
                        TransactionType.CREDIT
                )
                .map(AggregationResult::getTotal)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> sumDebits(String escrowAccountId) {
        return transactionRepository.sumAmountByEscrowAccountIdAndType(
                        escrowAccountId,
                        TransactionType.DEBIT
                )
                .map(AggregationResult::getTotal)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> sumByCategory(String escrowAccountId, EscrowTransactionCategory category) {
        return transactionRepository.sumAmountByEscrowAccountIdAndCategory(escrowAccountId, category.name())
                .map(AggregationResult::getTotal)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    @Override
    public Flux<StandaloneEscrowTransaction> findUnlinkedTransactions() {
        return transactionRepository.findByJournalEntryIdIsNull();
    }

    @Override
    @Transactional
    public Mono<StandaloneEscrowTransaction> linkToJournalEntry(String transactionId, String journalEntryId) {
        log.info("Linking transaction {} to journal entry {}", transactionId, journalEntryId);

        return transactionRepository.findById(transactionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Transaction not found: " + transactionId)))
                .flatMap(transaction -> {
                    transaction.setJournalEntryId(journalEntryId);
                    return transactionRepository.save(transaction);
                })
                .doOnSuccess(updated -> log.info("Transaction linked to journal entry"));
    }

    @Override
    public Mono<Boolean> verifyBalanceConsistency(String escrowAccountId) {
        log.info("Verifying balance consistency for escrow: {}", escrowAccountId);

        return Mono.zip(
                // Get balance from EventEscrowAccount
                escrowAccountRepository.findById(escrowAccountId)
                        .map(account -> account.getCurrentBalance())
                        .defaultIfEmpty(BigDecimal.ZERO),
                // Calculate balance from transactions
                calculateBalance(escrowAccountId)
        ).map(tuple -> {
            BigDecimal recordedBalance = tuple.getT1();
            BigDecimal calculatedBalance = tuple.getT2();
            boolean isConsistent = recordedBalance.compareTo(calculatedBalance) == 0;

            if (!isConsistent) {
                log.warn("Balance inconsistency for escrow {}: recorded={}, calculated={}",
                        escrowAccountId, recordedBalance, calculatedBalance);
            }

            return isConsistent;
        });
    }
}
