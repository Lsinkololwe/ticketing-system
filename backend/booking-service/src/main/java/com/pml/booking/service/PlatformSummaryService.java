package com.pml.booking.service;

import com.pml.booking.repository.dto.EscrowSummaryResult;
import com.pml.booking.repository.dto.PayoutSummaryResult;
import com.pml.booking.repository.dto.TicketSummaryResult;
import com.pml.booking.repository.dto.TransactionSummaryResult;
import com.pml.booking.web.graphql.dto.PlatformSummary;
import reactor.core.publisher.Mono;

/**
 * Service for platform-wide financial summaries and escrow management.
 *
 * <h2>Business Context</h2>
 * Platform summaries provide aggregated financial metrics across all organizers
 * and events. The escrow account holds funds between ticket purchase and payout
 * to organizers, ensuring sufficient liquidity for refunds.
 *
 * <h2>Primary Users</h2>
 * <ul>
 *   <li><b>Platform Admins</b> - Monitor overall platform financial health</li>
 *   <li><b>Finance Team</b> - Reconciliation and treasury management</li>
 *   <li><b>Executive Dashboard</b> - High-level KPIs and reporting</li>
 * </ul>
 *
 * <h2>Key Metrics</h2>
 * <ul>
 *   <li>Total escrow balance - Funds held for all organizers</li>
 *   <li>Pending payouts - Total amount awaiting payout processing</li>
 *   <li>Platform fees collected - Revenue from service fees</li>
 *   <li>Refund reserve - Funds set aside for potential refunds</li>
 * </ul>
 *
 * @author Booking Service Team
 * @since 1.0
 */
public interface PlatformSummaryService {

    /**
     * Retrieves the current platform-wide financial summary.
     * Used in admin dashboard for financial overview.
     *
     * <p>This method aggregates data from multiple collections:</p>
     * <ul>
     *   <li>Escrow accounts - balance, deposits, withdrawals</li>
     *   <li>Financial transactions - volume, fees, status counts</li>
     *   <li>Payout requests - pending, completed amounts</li>
     *   <li>Tickets - total count, revenue</li>
     * </ul>
     *
     * @return Mono containing the platform summary
     */
    Mono<PlatformSummary> getCurrentSummary();

    /**
     * Aggregates escrow account summary data.
     * Returns totals for balances, deposits, withdrawals, and account statuses.
     *
     * @return Mono containing the escrow summary result
     */
    Mono<EscrowSummaryResult> aggregateEscrowSummary();

    /**
     * Aggregates financial transaction summary data.
     * Returns totals for transaction counts, volumes, and commissions.
     *
     * @return Mono containing the transaction summary result
     */
    Mono<TransactionSummaryResult> aggregateTransactionSummary();

    /**
     * Aggregates payout request summary data.
     * Returns totals for payout counts and amounts by status.
     *
     * @return Mono containing the payout summary result
     */
    Mono<PayoutSummaryResult> aggregatePayoutSummary();

    /**
     * Aggregates ticket summary data.
     * Returns totals for ticket counts and revenue.
     *
     * @return Mono containing the ticket summary result
     */
    Mono<TicketSummaryResult> aggregateTicketSummary();
}
