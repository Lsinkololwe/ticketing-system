package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.stats.TicketCategoryStats;
import com.pml.booking.web.graphql.dto.stats.TicketStats;
import com.pml.booking.web.graphql.dto.stats.TicketStatusStats;
import com.pml.booking.domain.model.Ticket;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Service for computing ticket statistics using MongoDB aggregation pipelines.
 *
 * This service uses efficient aggregation queries instead of multiple database
 * round-trips, resulting in significantly faster dashboard performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketStatsService {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Compute comprehensive ticket statistics using a single aggregation pipeline.
     *
     * Performance: This method executes 3 parallel aggregation queries instead of
     * 10+ individual count queries, reducing database round-trips by ~70%.
     */
    public Mono<TicketStats> getTicketStats() {
        return Mono.zip(
                getStatusCountsAggregation(null),
                getCategoryStatsAggregation(null),
                getRecentTickets(null)
        ).map(tuple -> buildTicketStats(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    /**
     * Compute ticket statistics for a specific event.
     *
     * @param eventId The event ID to filter by
     * @return Ticket statistics for the specified event
     */
    public Mono<TicketStats> getTicketStatsByEvent(String eventId) {
        return Mono.zip(
                getStatusCountsAggregation(eventId),
                getCategoryStatsAggregation(eventId),
                getRecentTickets(eventId)
        ).map(tuple -> buildTicketStats(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private TicketStats buildTicketStats(List<TicketStatusStats> statusStats,
                                          List<TicketCategoryStats> categoryStats,
                                          List<Ticket> recentTickets) {
        // Calculate totals from status stats
        int totalTickets = statusStats.stream().mapToInt(TicketStatusStats::getCount).sum();
        int purchasedTickets = getCountForStatus(statusStats, TicketStatus.PURCHASED);
        int validatedTickets = getCountForStatus(statusStats, TicketStatus.VALIDATED);
        int usedTickets = getCountForStatus(statusStats, TicketStatus.USED);
        int refundedTickets = getCountForStatus(statusStats, TicketStatus.REFUNDED);
        int expiredTickets = getCountForStatus(statusStats, TicketStatus.EXPIRED);
        int cancelledTickets = getCountForStatus(statusStats, TicketStatus.CANCELLED);
        int pendingPaymentTickets = getCountForStatus(statusStats, TicketStatus.PENDING_PAYMENT);

        return TicketStats.builder()
                .totalTickets(totalTickets)
                .purchasedTickets(purchasedTickets)
                .validatedTickets(validatedTickets)
                .usedTickets(usedTickets)
                .refundedTickets(refundedTickets)
                .expiredTickets(expiredTickets)
                .cancelledTickets(cancelledTickets)
                .pendingPaymentTickets(pendingPaymentTickets)
                .ticketsByStatus(statusStats)
                .ticketsByCategory(categoryStats)
                .recentTickets(recentTickets)
                .build();
    }

    /**
     * Get ticket counts grouped by status using a single aggregation.
     *
     * MongoDB Aggregation Pipeline:
     * 1. $match: Optional filter by eventId
     * 2. $group: Group all tickets by status and count
     * 3. Results include all statuses with their counts
     *
     * @param eventId Optional event ID to filter by (null for all events)
     */
    private Mono<List<TicketStatusStats>> getStatusCountsAggregation(String eventId) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> operations = new ArrayList<>();
        if (eventId != null) {
            operations.add(match(Criteria.where("eventId").is(eventId)));
        }
        operations.add(group("status").count().as("count"));
        operations.add(sort(Sort.Direction.DESC, "count"));

        Aggregation aggregation = newAggregation(operations);

        return mongoTemplate.aggregate(aggregation, "tickets", StatusAggResult.class)
                .collectList()
                .map(results -> {
                    int total = results.stream().mapToInt(StatusAggResult::getCount).sum();

                    // Create stats for all statuses, including those with 0 count
                    List<TicketStatusStats> stats = new ArrayList<>();
                    for (TicketStatus status : TicketStatus.values()) {
                        int count = results.stream()
                                .filter(r -> status.name().equals(r.getId()))
                                .findFirst()
                                .map(StatusAggResult::getCount)
                                .orElse(0);

                        double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                        stats.add(TicketStatusStats.builder()
                                .status(status)
                                .count(count)
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get ticket statistics grouped by category using aggregation.
     *
     * MongoDB Aggregation Pipeline:
     * 1. $match: Filter by eventId if provided
     * 2. $group: Group by ticketCategoryName, count and sum revenue
     * 3. $sort: Order by count descending
     *
     * @param eventId Optional event ID to filter by (null for all events)
     */
    private Mono<List<TicketCategoryStats>> getCategoryStatsAggregation(String eventId) {
        Criteria criteria = Criteria.where("isActive").is(true);
        if (eventId != null) {
            criteria = criteria.and("eventId").is(eventId);
        }

        Aggregation aggregation = newAggregation(
                match(criteria),
                group("ticketCategoryName")
                        .count().as("count")
                        .sum("price").as("totalRevenue")
                        .first("ticketCategoryName").as("categoryName"),
                sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, "tickets", CategoryAggResult.class)
                .collectList()
                .map(results -> {
                    int total = results.stream().mapToInt(CategoryAggResult::getCount).sum();

                    List<TicketCategoryStats> stats = new ArrayList<>();
                    for (CategoryAggResult result : results) {
                        double percentage = total > 0 ? (result.getCount() * 100.0) / total : 0.0;

                        stats.add(TicketCategoryStats.builder()
                                .category(result.getCategoryName() != null ? result.getCategoryName() : "Unknown")
                                .count(result.getCount())
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .totalRevenue(result.getTotalRevenue() != null ? result.getTotalRevenue() : BigDecimal.ZERO)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get recent tickets (last 10) sorted by creation date.
     *
     * @param eventId Optional event ID to filter by (null for all events)
     */
    private Mono<List<Ticket>> getRecentTickets(String eventId) {
        List<org.springframework.data.mongodb.core.aggregation.AggregationOperation> operations = new ArrayList<>();
        if (eventId != null) {
            operations.add(match(Criteria.where("eventId").is(eventId)));
        }
        operations.add(sort(Sort.Direction.DESC, "createdAt"));
        operations.add(limit(10));

        Aggregation aggregation = newAggregation(operations);

        return mongoTemplate.aggregate(aggregation, "tickets", Ticket.class)
                .collectList();
    }

    private int getCountForStatus(List<TicketStatusStats> stats, TicketStatus status) {
        return stats.stream()
                .filter(s -> s.getStatus() == status)
                .findFirst()
                .map(TicketStatusStats::getCount)
                .orElse(0);
    }

    // ==================== Helper Classes ====================

    @lombok.Data
    private static class StatusAggResult {
        private String id; // The _id field from $group (status name)
        private int count;
    }

    @lombok.Data
    private static class CategoryAggResult {
        private String categoryName;
        private int count;
        private BigDecimal totalRevenue;
    }
}
