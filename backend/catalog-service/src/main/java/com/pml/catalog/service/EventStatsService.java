package com.pml.catalog.service;

import com.pml.catalog.web.graphql.dto.stats.EventCategoryStats;
import com.pml.catalog.web.graphql.dto.stats.EventOrganizerStats;
import com.pml.catalog.web.graphql.dto.stats.EventStats;
import com.pml.catalog.web.graphql.dto.stats.EventStatusStats;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.repository.EventRepository;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Service for computing event statistics using MongoDB aggregation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStatsService {

    private final EventRepository eventRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Compute comprehensive event statistics.
     */
    public Mono<EventStats> getEventStats() {
        return Mono.zip(
                getStatusCounts(),
                getTotalCapacityAndSales(),
                getStatsByCategory(),
                getStatsByStatus(),
                getStatsByOrganizer(),
                getRecentEvents()
        ).map(tuple -> {
            Map<EventStatus, Long> statusCounts = tuple.getT1();
            CapacitySales capacitySales = tuple.getT2();
            List<EventCategoryStats> categoryStats = tuple.getT3();
            List<EventStatusStats> statusStats = tuple.getT4();
            List<EventOrganizerStats> organizerStats = tuple.getT5();
            List<Event> recentEvents = tuple.getT6();

            int totalEvents = statusCounts.values().stream()
                    .mapToInt(Long::intValue)
                    .sum();

            return EventStats.builder()
                    .totalEvents(totalEvents)
                    .publishedEvents(statusCounts.getOrDefault(EventStatus.PUBLISHED, 0L).intValue())
                    .approvedNotPublishedEvents(statusCounts.getOrDefault(EventStatus.APPROVED, 0L).intValue())
                    .draftEvents(statusCounts.getOrDefault(EventStatus.DRAFT, 0L).intValue())
                    .pendingApprovalEvents(statusCounts.getOrDefault(EventStatus.PENDING_APPROVAL, 0L).intValue())
                    .cancelledEvents(statusCounts.getOrDefault(EventStatus.CANCELLED, 0L).intValue())
                    .completedEvents(statusCounts.getOrDefault(EventStatus.COMPLETED, 0L).intValue())
                    .rejectedEvents(statusCounts.getOrDefault(EventStatus.REJECTED, 0L).intValue())
                    .totalCapacity(capacitySales.totalCapacity)
                    .totalSoldTickets(capacitySales.totalSoldTickets)
                    .totalRevenue(capacitySales.totalRevenue)
                    .eventsByCategory(categoryStats)
                    .eventsByStatus(statusStats)
                    .eventsByOrganizer(organizerStats)
                    .recentEvents(recentEvents)
                    .build();
        });
    }

    /**
     * Get event counts grouped by status.
     */
    private Mono<Map<EventStatus, Long>> getStatusCounts() {
        return Flux.fromArray(EventStatus.values())
                .flatMap(status -> eventRepository.countByStatus(status)
                        .map(count -> Map.entry(status, count)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * Get total capacity, sold tickets, and revenue using aggregation.
     */
    private Mono<CapacitySales> getTotalCapacityAndSales() {
        Aggregation aggregation = newAggregation(
                match(org.springframework.data.mongodb.core.query.Criteria.where("isActive").is(true)),
                group()
                        .sum("totalCapacity").as("totalCapacity")
                        .sum("soldTickets").as("totalSoldTickets")
        );

        return mongoTemplate.aggregate(aggregation, "events", CapacitySalesResult.class)
                .singleOrEmpty()
                .map(result -> new CapacitySales(
                        result.getTotalCapacity(),
                        result.getTotalSoldTickets(),
                        BigDecimal.ZERO // Revenue calculated per-event in a separate pass if needed
                ))
                .defaultIfEmpty(new CapacitySales(0, 0, BigDecimal.ZERO));
    }

    /**
     * Get statistics grouped by category with category name lookup.
     * Uses MongoDB $lookup to join with event_categories collection.
     */
    private Mono<List<EventCategoryStats>> getStatsByCategory() {
        // Aggregation with $lookup to get category names
        LookupOperation lookupOperation = LookupOperation.newLookup()
                .from("event_categories")
                .localField("categoryId")
                .foreignField("_id")
                .as("categoryInfo");

        UnwindOperation unwindOperation = unwind("categoryInfo", true); // preserve nulls

        Aggregation aggregation = newAggregation(
                match(org.springframework.data.mongodb.core.query.Criteria.where("isActive").is(true)),
                lookupOperation,
                unwindOperation,
                group("categoryId")
                        .count().as("count")
                        .sum("totalCapacity").as("totalCapacity")
                        .sum("soldTickets").as("totalSoldTickets")
                        .first("categoryId").as("categoryId")
                        .first("categoryInfo.name").as("categoryName"),
                sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, "events", CategoryAggResult.class)
                .collectList()
                .map(results -> {
                    int totalEvents = results.stream().mapToInt(CategoryAggResult::getCount).sum();

                    List<EventCategoryStats> stats = new ArrayList<>();
                    for (CategoryAggResult result : results) {
                        double percentage = totalEvents > 0
                                ? (result.getCount() * 100.0) / totalEvents
                                : 0.0;

                        // Use category name if available, otherwise fall back to ID or "Unknown"
                        String categoryDisplay = result.getCategoryName() != null
                                ? result.getCategoryName()
                                : (result.getCategoryId() != null ? result.getCategoryId() : "Unknown");

                        stats.add(EventCategoryStats.builder()
                                .category(categoryDisplay)
                                .count(result.getCount())
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .totalCapacity(result.getTotalCapacity())
                                .totalSoldTickets(result.getTotalSoldTickets())
                                .totalRevenue(BigDecimal.ZERO)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get statistics grouped by status.
     */
    private Mono<List<EventStatusStats>> getStatsByStatus() {
        return getStatusCounts()
                .map(statusCounts -> {
                    int total = statusCounts.values().stream()
                            .mapToInt(Long::intValue)
                            .sum();

                    List<EventStatusStats> stats = new ArrayList<>();
                    for (EventStatus status : EventStatus.values()) {
                        long count = statusCounts.getOrDefault(status, 0L);
                        double percentage = total > 0 ? (count * 100.0) / total : 0.0;

                        stats.add(EventStatusStats.builder()
                                .status(status)
                                .count((int) count)
                                .percentage(Math.round(percentage * 100.0) / 100.0)
                                .build());
                    }
                    return stats;
                });
    }

    /**
     * Get statistics grouped by organizer.
     */
    private Mono<List<EventOrganizerStats>> getStatsByOrganizer() {
        Aggregation aggregation = newAggregation(
                match(org.springframework.data.mongodb.core.query.Criteria.where("isActive").is(true)),
                group("organizerId")
                        .count().as("eventCount")
                        .sum("totalCapacity").as("totalCapacity")
                        .sum("soldTickets").as("totalSoldTickets")
                        .first("organizerId").as("organizerId")
                        .first("organizerName").as("organizerName"),
                sort(Sort.Direction.DESC, "eventCount"),
                limit(10) // Top 10 organizers
        );

        return mongoTemplate.aggregate(aggregation, "events", OrganizerAggResult.class)
                .map(result -> EventOrganizerStats.builder()
                        .organizerId(result.getOrganizerId())
                        .organizerName(result.getOrganizerName() != null ? result.getOrganizerName() : "Unknown")
                        .eventCount(result.getEventCount())
                        .totalCapacity(result.getTotalCapacity())
                        .totalSoldTickets(result.getTotalSoldTickets())
                        .totalRevenue(BigDecimal.ZERO)
                        .build())
                .collectList();
    }

    /**
     * Get recent events (last 10).
     */
    private Mono<List<Event>> getRecentEvents() {
        return eventRepository.findAllBy(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).collectList();
    }

    // ==================== Helper Classes ====================

    private record CapacitySales(int totalCapacity, int totalSoldTickets, BigDecimal totalRevenue) {}

    @lombok.Data
    private static class CapacitySalesResult {
        private int totalCapacity;
        private int totalSoldTickets;
    }

    @lombok.Data
    private static class CategoryAggResult {
        private String categoryId;
        private String categoryName;
        private int count;
        private int totalCapacity;
        private int totalSoldTickets;
    }

    @lombok.Data
    private static class OrganizerAggResult {
        private String organizerId;
        private String organizerName;
        private int eventCount;
        private int totalCapacity;
        private int totalSoldTickets;
    }
}
