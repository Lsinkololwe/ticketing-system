package com.pml.booking.service.impl;

import com.pml.booking.repository.PlatformSummaryRepository;
import com.pml.booking.repository.dto.EscrowSummaryResult;
import com.pml.booking.repository.dto.PayoutSummaryResult;
import com.pml.booking.repository.dto.TicketSummaryResult;
import com.pml.booking.repository.dto.TransactionSummaryResult;
import com.pml.booking.service.PlatformSummaryService;
import com.pml.booking.web.graphql.dto.PlatformSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of {@link PlatformSummaryService}.
 *
 * <p>Centralizes all platform summary logic previously in EscrowAccountQueryResolver.</p>
 *
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSummaryServiceImpl implements PlatformSummaryService {

    private final PlatformSummaryRepository platformSummaryRepository;

    @Override
    public Mono<PlatformSummary> getCurrentSummary() {
        log.debug("Computing platform summary using MongoDB aggregation pipelines");

        return Mono.zip(
                aggregateEscrowSummary(),
                aggregateTransactionSummary(),
                aggregatePayoutSummary(),
                aggregateTicketSummary()
        ).map(tuple -> {
            EscrowSummaryResult escrow = tuple.getT1();
            TransactionSummaryResult txn = tuple.getT2();
            PayoutSummaryResult payout = tuple.getT3();
            TicketSummaryResult ticket = tuple.getT4();

            return new PlatformSummary(
                    escrow.totalAccounts(),
                    escrow.activeAccounts(),
                    escrow.lockedAccounts(),
                    escrow.payoutEligibleAccounts(),
                    escrow.closedAccounts(),
                    escrow.totalBalance(),
                    escrow.totalDeposits(),
                    escrow.totalWithdrawals(),
                    escrow.totalRefunds(),
                    escrow.availableForPayout(),
                    txn.totalTransactions(),
                    txn.completedTransactions(),
                    txn.pendingTransactions(),
                    txn.failedTransactions(),
                    txn.totalVolume(),
                    txn.totalCommissions(),
                    payout.totalPayoutRequests(),
                    payout.pendingPayoutRequests(),
                    payout.completedPayoutRequests(),
                    payout.totalPayoutAmount(),
                    ticket.totalTickets(),
                    ticket.totalRevenue(),
                    "ZMW"
            );
        }).doOnSuccess(summary -> log.debug("Platform summary computed successfully"))
          .doOnError(e -> log.error("Platform summary computation failed", e));
    }

    @Override
    public Mono<EscrowSummaryResult> aggregateEscrowSummary() {
        log.debug("Aggregating escrow summary");
        return platformSummaryRepository.aggregateEscrowSummary();
    }

    @Override
    public Mono<TransactionSummaryResult> aggregateTransactionSummary() {
        log.debug("Aggregating transaction summary");
        return platformSummaryRepository.aggregateTransactionSummary();
    }

    @Override
    public Mono<PayoutSummaryResult> aggregatePayoutSummary() {
        log.debug("Aggregating payout summary");
        return platformSummaryRepository.aggregatePayoutSummary();
    }

    @Override
    public Mono<TicketSummaryResult> aggregateTicketSummary() {
        log.debug("Aggregating ticket summary");
        return platformSummaryRepository.aggregateTicketSummary();
    }
}
