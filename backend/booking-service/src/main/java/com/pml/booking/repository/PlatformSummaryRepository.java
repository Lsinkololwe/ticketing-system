package com.pml.booking.repository;

import com.pml.booking.repository.dto.EscrowSummaryResult;
import com.pml.booking.repository.dto.PayoutSummaryResult;
import com.pml.booking.repository.dto.TicketSummaryResult;
import com.pml.booking.repository.dto.TransactionSummaryResult;
import reactor.core.publisher.Mono;

/**
 * Repository for platform-wide aggregation queries.
 *
 * Uses MongoDB aggregation pipelines for efficient computation at scale.
 * These queries are designed to handle billions of records without
 * loading data into application memory.
 *
 * Best Practices Applied:
 * 1. Server-side aggregation with $group, $sum, $count
 * 2. $facet for multiple aggregations in single query
 * 3. Proper indexes on status and date fields
 * 4. Returns pre-computed DTOs, not raw documents
 */
public interface PlatformSummaryRepository {

    /**
     * Aggregate escrow account statistics.
     *
     * Uses MongoDB aggregation pipeline:
     * - $facet to compute counts by status + balance sums in one query
     * - $group with $sum for financial totals
     * - All computation done server-side
     */
    Mono<EscrowSummaryResult> aggregateEscrowSummary();

    /**
     * Aggregate transaction statistics.
     *
     * Uses $facet for:
     * - Status distribution counts
     * - Volume and commission sums
     */
    Mono<TransactionSummaryResult> aggregateTransactionSummary();

    /**
     * Aggregate payout request statistics.
     */
    Mono<PayoutSummaryResult> aggregatePayoutSummary();

    /**
     * Aggregate ticket statistics.
     */
    Mono<TicketSummaryResult> aggregateTicketSummary();
}
