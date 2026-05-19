package com.pml.booking.service.impl;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.LiveDashboardService;
import com.pml.booking.web.graphql.dto.CheckInEvent;
import com.pml.booking.web.graphql.dto.LiveDashboard;
import com.pml.booking.web.graphql.dto.TierCheckInStats;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Live Dashboard Service Implementation
 *
 * Business Intent: Provides real-time check-in statistics for organizers
 * during active events. Optimized for performance with MongoDB queries.
 *
 * Performance Considerations:
 * - Uses parallel execution with Mono.zip for independent queries
 * - Limits recent check-ins to prevent memory issues
 * - Simple queries instead of complex aggregations for reliability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveDashboardServiceImpl implements LiveDashboardService {

    private final TicketRepository ticketRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    // Statuses that count as "sold" tickets
    private static final Set<TicketStatus> SOLD_STATUSES = Set.of(
            TicketStatus.PURCHASED,
            TicketStatus.CONFIRMED,
            TicketStatus.VALIDATED,
            TicketStatus.USED
    );

    // Status for checked-in tickets
    private static final Set<TicketStatus> CHECKED_IN_STATUSES = Set.of(
            TicketStatus.VALIDATED,
            TicketStatus.USED
    );

    @Override
    public Mono<LiveDashboard> getEventLiveDashboard(String eventId) {
        log.debug("Getting live dashboard for event: {}", eventId);

        // Execute queries in parallel for performance
        return Mono.zip(
                getBasicStats(eventId),
                getTierStats(eventId),
                getRecentCheckIns(eventId, 10),
                getCheckInsLastHour(eventId)
        ).map(tuple -> {
            BasicStats basic = tuple.getT1();
            List<TierCheckInStats> tierStats = tuple.getT2();
            List<CheckInEvent> recentCheckIns = tuple.getT3();
            int checkInsLastHour = tuple.getT4();

            float checkInRate = basic.totalSold > 0
                    ? ((float) basic.checkedIn / basic.totalSold) * 100
                    : 0f;

            // Calculate current check-in rate (per minute over last hour)
            Float currentRate = checkInsLastHour / 60f;

            return LiveDashboard.builder()
                    .eventId(eventId)
                    .eventTitle(basic.eventTitle)
                    .totalSold(basic.totalSold)
                    .totalCapacity(basic.totalSold) // Use sold as approximation
                    .checkedIn(basic.checkedIn)
                    .checkInRate(checkInRate)
                    .checkInsLastHour(checkInsLastHour)
                    .peakCheckInTime(null) // Would require more complex aggregation
                    .currentCheckInRate(currentRate)
                    .checkInsByTier(tierStats)
                    .recentCheckIns(recentCheckIns)
                    .build();
        }).onErrorResume(e -> {
            log.error("Error getting live dashboard for event {}: {}", eventId, e.getMessage());
            return Mono.just(LiveDashboard.empty(eventId, "Unknown Event", 0));
        });
    }

    /**
     * Get basic statistics using repository methods.
     */
    private Mono<BasicStats> getBasicStats(String eventId) {
        return ticketRepository.findByEventId(eventId)
                .next()
                .flatMap(sampleTicket -> {
                    String eventTitle = sampleTicket.getEventTitle() != null
                            ? sampleTicket.getEventTitle()
                            : "Unknown Event";

                    Mono<Long> soldCount = ticketRepository.countByEventIdAndStatusIn(
                            eventId, SOLD_STATUSES);

                    Mono<Long> checkedInCount = ticketRepository.countByEventIdAndStatusIn(
                            eventId, CHECKED_IN_STATUSES);

                    return Mono.zip(soldCount, checkedInCount)
                            .map(counts -> new BasicStats(
                                    eventTitle,
                                    counts.getT1().intValue(),
                                    counts.getT2().intValue()
                            ));
                })
                .switchIfEmpty(Mono.just(new BasicStats("Unknown Event", 0, 0)));
    }

    /**
     * Get tier-level check-in statistics.
     */
    private Mono<List<TierCheckInStats>> getTierStats(String eventId) {
        return ticketRepository.findByEventIdAndStatusIn(eventId, SOLD_STATUSES)
                .collectList()
                .map(tickets -> {
                    // Group by tier
                    Map<String, List<Ticket>> byTier = tickets.stream()
                            .collect(Collectors.groupingBy(t ->
                                    t.getTicketCategoryCode() != null ? t.getTicketCategoryCode() : "default"));

                    return byTier.entrySet().stream()
                            .map(entry -> {
                                String tierId = entry.getKey();
                                List<Ticket> tierTickets = entry.getValue();
                                String tierName = tierTickets.isEmpty() ? tierId :
                                        (tierTickets.get(0).getTicketCategoryName() != null ?
                                                tierTickets.get(0).getTicketCategoryName() : tierId);
                                int sold = tierTickets.size();
                                int checkedIn = (int) tierTickets.stream()
                                        .filter(t -> CHECKED_IN_STATUSES.contains(t.getStatus()))
                                        .count();
                                return TierCheckInStats.of(tierId, tierName, sold, checkedIn);
                            })
                            .collect(Collectors.toList());
                });
    }

    /**
     * Get recent check-ins for live feed.
     */
    private Mono<List<CheckInEvent>> getRecentCheckIns(String eventId, int limit) {
        Query query = new Query(
                Criteria.where("eventId").is(eventId)
                        .and("status").in(CHECKED_IN_STATUSES.stream().map(Enum::name).toList())
                        .and("validatedAt").ne(null)
        )
                .with(Sort.by(Sort.Direction.DESC, "validatedAt"))
                .limit(limit);

        return mongoTemplate.find(query, Ticket.class)
                .map(ticket -> CheckInEvent.builder()
                        .ticketId(ticket.getId())
                        .ticketNumber(ticket.getTicketNumber())
                        .tierName(ticket.getTicketCategoryName())
                        .buyerName(ticket.getBuyerName())
                        .checkedInAt(ticket.getValidatedAt())
                        .scannerId(null) // Ticket model doesn't have validatedBy
                        .scannerName(null)
                        .totalCheckedIn(0) // Would need separate query
                        .build())
                .collectList();
    }

    /**
     * Count check-ins in the last hour.
     */
    private Mono<Integer> getCheckInsLastHour(String eventId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        Query query = new Query(
                Criteria.where("eventId").is(eventId)
                        .and("status").in(CHECKED_IN_STATUSES.stream().map(Enum::name).toList())
                        .and("validatedAt").gte(oneHourAgo)
        );

        return mongoTemplate.count(query, Ticket.class)
                .map(Long::intValue);
    }

    /**
     * Internal record for basic stats.
     */
    private record BasicStats(String eventTitle, int totalSold, int checkedIn) {}
}
