package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.EscrowTransactionCategory;
import com.pml.booking.domain.enums.EscrowTransactionType;
import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import com.pml.booking.domain.model.StandaloneEscrowTransaction.TransactionType;
import com.pml.booking.repository.StandaloneEscrowTransactionRepository;
import com.pml.booking.repository.dto.AggregationResult;
import com.pml.booking.service.EscrowTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
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
    public Mono<BigDecimal> calculateBalanceAsOf(String escrowAccountId, Instant asOfDate) {
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

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    @Override
    public Flux<StandaloneEscrowTransaction> findUnlinkedTransactions() {
        return transactionRepository.findByJournalEntryIdIsNull();
    }
}
