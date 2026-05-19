package com.pml.booking.repository.impl;

import com.pml.booking.repository.PlatformSummaryRepository;
import com.pml.booking.repository.dto.EscrowSummaryResult;
import com.pml.booking.repository.dto.PayoutSummaryResult;
import com.pml.booking.repository.dto.TicketSummaryResult;
import com.pml.booking.repository.dto.TransactionSummaryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.FacetOperation;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation of PlatformSummaryRepository using MongoDB aggregation pipelines.
 *
 * This implementation is designed for SCALE:
 * - All computation happens server-side in MongoDB
 * - Uses $facet for multiple aggregations in single query
 * - No documents are transferred to application memory for counting/summing
 * - Supports billions of records efficiently
 *
 * INDEXING REQUIREMENTS:
 * - event_escrow_accounts: index on "status"
 * - financial_transactions: compound index on "status", index on "transactionType"
 * - payout_requests: index on "status"
 * - tickets: index on "status"
 *
 * Example MongoDB command to create indexes:
 * db.event_escrow_accounts.createIndex({ "status": 1 })
 * db.financial_transactions.createIndex({ "status": 1 })
 * db.payout_requests.createIndex({ "status": 1 })
 * db.tickets.createIndex({ "status": 1 })
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlatformSummaryRepositoryImpl implements PlatformSummaryRepository {

    private final ReactiveMongoTemplate mongoTemplate;

    private static final String ESCROW_COLLECTION = "event_escrow_accounts";
    private static final String TRANSACTION_COLLECTION = "financial_transactions";
    private static final String PAYOUT_COLLECTION = "payout_requests";
    private static final String TICKET_COLLECTION = "tickets";

    @Override
    public Mono<EscrowSummaryResult> aggregateEscrowSummary() {
        log.debug("Executing escrow summary aggregation pipeline");

        /*
         * MongoDB Aggregation Pipeline using $facet:
         *
         * {
         *   $facet: {
         *     "statusCounts": [
         *       { $group: { _id: "$status", count: { $sum: 1 } } }
         *     ],
         *     "balanceTotals": [
         *       { $group: {
         *           _id: null,
         *           totalBalance: { $sum: "$currentBalance" },
         *           totalDeposits: { $sum: "$totalDeposits" },
         *           totalWithdrawals: { $sum: "$totalWithdrawals" },
         *           totalRefunds: { $sum: "$totalRefunds" }
         *         }
         *       }
         *     ],
         *     "payoutEligibleBalance": [
         *       { $match: { status: "PAYOUT_ELIGIBLE" } },
         *       { $group: { _id: null, available: { $sum: "$currentBalance" } } }
         *     ]
         *   }
         * }
         */

        // Status counts sub-pipeline
        AggregationOperation statusGroup = context -> new Document("$group",
                new Document("_id", "$status")
                        .append("count", new Document("$sum", 1)));

        // Balance totals sub-pipeline
        AggregationOperation balanceGroup = context -> new Document("$group",
                new Document("_id", null)
                        .append("totalBalance", new Document("$sum", new Document("$toDecimal", "$currentBalance")))
                        .append("totalDeposits", new Document("$sum", new Document("$toDecimal", "$totalDeposits")))
                        .append("totalWithdrawals", new Document("$sum", new Document("$toDecimal", "$totalWithdrawals")))
                        .append("totalRefunds", new Document("$sum", new Document("$toDecimal", "$totalRefunds"))));

        // Payout eligible balance sub-pipeline
        AggregationOperation payoutMatch = context -> new Document("$match",
                new Document("status", "PAYOUT_ELIGIBLE"));
        AggregationOperation payoutGroup = context -> new Document("$group",
                new Document("_id", null)
                        .append("available", new Document("$sum", new Document("$toDecimal", "$currentBalance"))));

        FacetOperation facet = facet()
                .and(statusGroup).as("statusCounts")
                .and(balanceGroup).as("balanceTotals")
                .and(payoutMatch, payoutGroup).as("payoutEligibleBalance");

        Aggregation aggregation = newAggregation(facet);

        return mongoTemplate.aggregate(aggregation, ESCROW_COLLECTION, Document.class)
                .next()
                .map(this::parseEscrowSummary)
                .defaultIfEmpty(EscrowSummaryResult.empty())
                .doOnError(e -> log.error("Escrow summary aggregation failed", e));
    }

    @Override
    public Mono<TransactionSummaryResult> aggregateTransactionSummary() {
        log.debug("Executing transaction summary aggregation pipeline");

        // Status counts sub-pipeline
        AggregationOperation statusGroup = context -> new Document("$group",
                new Document("_id", "$status")
                        .append("count", new Document("$sum", 1)));

        // Financial totals sub-pipeline
        AggregationOperation financialGroup = context -> new Document("$group",
                new Document("_id", null)
                        .append("totalVolume", new Document("$sum", new Document("$toDecimal", "$amount")))
                        .append("totalCommissions", new Document("$sum",
                                new Document("$ifNull", List.of(
                                        new Document("$toDecimal", "$commissionAmount"),
                                        0
                                )))));

        FacetOperation facet = facet()
                .and(statusGroup).as("statusCounts")
                .and(financialGroup).as("financialTotals");

        Aggregation aggregation = newAggregation(facet);

        return mongoTemplate.aggregate(aggregation, TRANSACTION_COLLECTION, Document.class)
                .next()
                .map(this::parseTransactionSummary)
                .defaultIfEmpty(TransactionSummaryResult.empty())
                .doOnError(e -> log.error("Transaction summary aggregation failed", e));
    }

    @Override
    public Mono<PayoutSummaryResult> aggregatePayoutSummary() {
        log.debug("Executing payout summary aggregation pipeline");

        // Status counts sub-pipeline
        AggregationOperation statusGroup = context -> new Document("$group",
                new Document("_id", "$status")
                        .append("count", new Document("$sum", 1)));

        // Completed payouts total sub-pipeline
        AggregationOperation completedMatch = context -> new Document("$match",
                new Document("status", "COMPLETED"));
        AggregationOperation completedSum = context -> new Document("$group",
                new Document("_id", null)
                        .append("totalAmount", new Document("$sum", new Document("$toDecimal", "$netPayoutAmount"))));

        FacetOperation facet = facet()
                .and(statusGroup).as("statusCounts")
                .and(completedMatch, completedSum).as("completedTotal");

        Aggregation aggregation = newAggregation(facet);

        return mongoTemplate.aggregate(aggregation, PAYOUT_COLLECTION, Document.class)
                .next()
                .map(this::parsePayoutSummary)
                .defaultIfEmpty(PayoutSummaryResult.empty())
                .doOnError(e -> log.error("Payout summary aggregation failed", e));
    }

    @Override
    public Mono<TicketSummaryResult> aggregateTicketSummary() {
        log.debug("Executing ticket summary aggregation pipeline");

        // Status counts sub-pipeline
        AggregationOperation statusGroup = context -> new Document("$group",
                new Document("_id", "$status")
                        .append("count", new Document("$sum", 1)));

        // Revenue total sub-pipeline (only sold statuses)
        AggregationOperation revenueMatch = context -> new Document("$match",
                new Document("status", new Document("$in",
                        List.of("PURCHASED", "CONFIRMED", "VALIDATED", "USED"))));
        AggregationOperation revenueSum = context -> new Document("$group",
                new Document("_id", null)
                        .append("totalRevenue", new Document("$sum", new Document("$toDecimal", "$price"))));

        FacetOperation facet = facet()
                .and(statusGroup).as("statusCounts")
                .and(revenueMatch, revenueSum).as("revenueTotals");

        Aggregation aggregation = newAggregation(facet);

        return mongoTemplate.aggregate(aggregation, TICKET_COLLECTION, Document.class)
                .next()
                .map(this::parseTicketSummary)
                .defaultIfEmpty(TicketSummaryResult.empty())
                .doOnError(e -> log.error("Ticket summary aggregation failed", e));
    }

    // ==================== PARSING METHODS ====================

    @SuppressWarnings("unchecked")
    private EscrowSummaryResult parseEscrowSummary(Document doc) {
        // Parse status counts
        List<Document> statusCounts = (List<Document>) doc.get("statusCounts");
        Map<String, Long> countsByStatus = new java.util.HashMap<>();
        long total = 0;
        if (statusCounts != null) {
            for (Document sc : statusCounts) {
                String status = sc.getString("_id");
                long count = sc.getInteger("count", 0);
                countsByStatus.put(status, count);
                total += count;
            }
        }

        // Parse balance totals
        List<Document> balanceTotals = (List<Document>) doc.get("balanceTotals");
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;

        if (balanceTotals != null && !balanceTotals.isEmpty()) {
            Document bt = balanceTotals.get(0);
            totalBalance = toBigDecimal(bt.get("totalBalance"));
            totalDeposits = toBigDecimal(bt.get("totalDeposits"));
            totalWithdrawals = toBigDecimal(bt.get("totalWithdrawals"));
            totalRefunds = toBigDecimal(bt.get("totalRefunds"));
        }

        // Parse payout eligible balance
        List<Document> payoutEligible = (List<Document>) doc.get("payoutEligibleBalance");
        BigDecimal availableForPayout = BigDecimal.ZERO;
        if (payoutEligible != null && !payoutEligible.isEmpty()) {
            availableForPayout = toBigDecimal(payoutEligible.get(0).get("available"));
        }

        return new EscrowSummaryResult(
                total,
                countsByStatus.getOrDefault("CREATED", 0L),
                countsByStatus.getOrDefault("ACTIVE", 0L),
                countsByStatus.getOrDefault("LOCKED", 0L),
                countsByStatus.getOrDefault("PAYOUT_ELIGIBLE", 0L),
                countsByStatus.getOrDefault("PROCESSING_PAYOUT", 0L),
                countsByStatus.getOrDefault("CLOSED", 0L),
                countsByStatus.getOrDefault("CANCELLED", 0L),
                totalBalance,
                totalDeposits,
                totalWithdrawals,
                totalRefunds,
                availableForPayout
        );
    }

    @SuppressWarnings("unchecked")
    private TransactionSummaryResult parseTransactionSummary(Document doc) {
        List<Document> statusCounts = (List<Document>) doc.get("statusCounts");
        Map<String, Long> countsByStatus = new java.util.HashMap<>();
        long total = 0;
        if (statusCounts != null) {
            for (Document sc : statusCounts) {
                String status = sc.getString("_id");
                long count = sc.getInteger("count", 0);
                countsByStatus.put(status, count);
                total += count;
            }
        }

        List<Document> financialTotals = (List<Document>) doc.get("financialTotals");
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;

        if (financialTotals != null && !financialTotals.isEmpty()) {
            Document ft = financialTotals.get(0);
            totalVolume = toBigDecimal(ft.get("totalVolume"));
            totalCommissions = toBigDecimal(ft.get("totalCommissions"));
        }

        return new TransactionSummaryResult(
                total,
                countsByStatus.getOrDefault("PENDING", 0L),
                countsByStatus.getOrDefault("PROCESSING", 0L),
                countsByStatus.getOrDefault("COMPLETED", 0L),
                countsByStatus.getOrDefault("FAILED", 0L),
                countsByStatus.getOrDefault("CANCELLED", 0L),
                totalVolume,
                totalCommissions
        );
    }

    @SuppressWarnings("unchecked")
    private PayoutSummaryResult parsePayoutSummary(Document doc) {
        List<Document> statusCounts = (List<Document>) doc.get("statusCounts");
        Map<String, Long> countsByStatus = new java.util.HashMap<>();
        long total = 0;
        if (statusCounts != null) {
            for (Document sc : statusCounts) {
                String status = sc.getString("_id");
                long count = sc.getInteger("count", 0);
                countsByStatus.put(status, count);
                total += count;
            }
        }

        List<Document> completedTotal = (List<Document>) doc.get("completedTotal");
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (completedTotal != null && !completedTotal.isEmpty()) {
            totalAmount = toBigDecimal(completedTotal.get(0).get("totalAmount"));
        }

        return new PayoutSummaryResult(
                total,
                countsByStatus.getOrDefault("PENDING", 0L),
                countsByStatus.getOrDefault("APPROVED", 0L),
                countsByStatus.getOrDefault("PROCESSING", 0L),
                countsByStatus.getOrDefault("COMPLETED", 0L),
                countsByStatus.getOrDefault("FAILED", 0L),
                countsByStatus.getOrDefault("REJECTED", 0L),
                totalAmount
        );
    }

    @SuppressWarnings("unchecked")
    private TicketSummaryResult parseTicketSummary(Document doc) {
        List<Document> statusCounts = (List<Document>) doc.get("statusCounts");
        Map<String, Long> countsByStatus = new java.util.HashMap<>();
        long total = 0;
        if (statusCounts != null) {
            for (Document sc : statusCounts) {
                String status = sc.getString("_id");
                long count = sc.getInteger("count", 0);
                countsByStatus.put(status, count);
                total += count;
            }
        }

        List<Document> revenueTotals = (List<Document>) doc.get("revenueTotals");
        BigDecimal totalRevenue = BigDecimal.ZERO;
        if (revenueTotals != null && !revenueTotals.isEmpty()) {
            totalRevenue = toBigDecimal(revenueTotals.get(0).get("totalRevenue"));
        }

        return new TicketSummaryResult(
                total,
                countsByStatus.getOrDefault("PENDING_PAYMENT", 0L),
                countsByStatus.getOrDefault("PURCHASED", 0L),
                countsByStatus.getOrDefault("CONFIRMED", 0L),
                countsByStatus.getOrDefault("VALIDATED", 0L),
                countsByStatus.getOrDefault("USED", 0L),
                countsByStatus.getOrDefault("EXPIRED", 0L),
                countsByStatus.getOrDefault("CANCELLED", 0L),
                countsByStatus.getOrDefault("REFUNDED", 0L),
                totalRevenue
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof org.bson.types.Decimal128) {
            return ((org.bson.types.Decimal128) value).bigDecimalValue();
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
