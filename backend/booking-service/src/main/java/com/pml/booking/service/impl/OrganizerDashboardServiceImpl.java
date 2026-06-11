package com.pml.booking.service.impl;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.OrganizerDashboardService;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.organizer.*;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Implementation of OrganizerDashboardService.
 *
 * Uses MongoDB aggregation pipelines for efficient statistics computation.
 * All queries filter by organizerId to ensure data isolation (OWASP A01:2021).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizerDashboardServiceImpl implements OrganizerDashboardService {

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketRepository ticketRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final EventEscrowAccountRepository escrowAccountRepository;

    private static final String TICKETS_COLLECTION = "tickets";
    private static final String ESCROW_ACCOUNTS_COLLECTION = "event_escrow_accounts";
    private static final String PAYOUT_REQUESTS_COLLECTION = "payout_requests";

    @Override
    public Mono<OrganizerDashboardStats> getDashboardStats(String organizerId) {
        log.debug("Getting dashboard stats for organizer: {}", organizerId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime sixtyDaysAgo = now.minusDays(60);
        LocalDateTime oneWeekFromNow = now.plusDays(7);

        // Run multiple aggregations in parallel using Mono.zip
        return Mono.zip(
                // 1. Revenue and tickets sold (current period)
                getRevenueAndTicketStats(organizerId, thirtyDaysAgo, now),
                // 2. Revenue and tickets sold (previous period for comparison)
                getRevenueAndTicketStats(organizerId, sixtyDaysAgo, thirtyDaysAgo),
                // 3. Active events count (placeholder - would need catalog-service call)
                Mono.just(Map.of("activeEvents", 0, "eventsEndingThisWeek", 0)),
                // 4. Total attendees (checked in tickets)
                getAttendeeStats(organizerId, thirtyDaysAgo, now),
                // 5. Previous period attendees
                getAttendeeStats(organizerId, sixtyDaysAgo, thirtyDaysAgo),
                // 6. Pending payouts and available balance
                getPayoutStats(organizerId)
        ).map(tuple -> {
            Map<String, Object> currentStats = new HashMap<>(tuple.getT1());
            Map<String, Object> previousStats = new HashMap<>(tuple.getT2());
            Map<String, Object> eventStats = new HashMap<>(tuple.getT3());
            Map<String, Object> currentAttendees = new HashMap<>(tuple.getT4());
            Map<String, Object> previousAttendees = new HashMap<>(tuple.getT5());
            Map<String, Object> payoutStats = new HashMap<>(tuple.getT6());

            BigDecimal currentRevenue = (BigDecimal) currentStats.getOrDefault("totalRevenue", BigDecimal.ZERO);
            BigDecimal previousRevenue = (BigDecimal) previousStats.getOrDefault("totalRevenue", BigDecimal.ZERO);
            Integer currentTickets = (Integer) currentStats.getOrDefault("ticketsSold", 0);
            Integer previousTickets = (Integer) previousStats.getOrDefault("ticketsSold", 0);
            Integer currentCheckedIn = (Integer) currentAttendees.getOrDefault("checkedIn", 0);
            Integer previousCheckedIn = (Integer) previousAttendees.getOrDefault("checkedIn", 0);

            return OrganizerDashboardStats.builder()
                    .totalRevenue(currentRevenue)
                    .revenueChange(calculatePercentageChange(previousRevenue, currentRevenue))
                    .revenueCurrency("ZMW")
                    .totalTicketsSold(currentTickets)
                    .ticketsSoldChange(calculatePercentageChange(previousTickets, currentTickets))
                    .activeEvents((Integer) eventStats.getOrDefault("activeEvents", 0))
                    .eventsEndingThisWeek((Integer) eventStats.getOrDefault("eventsEndingThisWeek", 0))
                    .totalAttendees(currentCheckedIn)
                    .attendeesChange(calculatePercentageChange(previousCheckedIn, currentCheckedIn))
                    .pendingPayouts((BigDecimal) payoutStats.getOrDefault("pendingPayouts", BigDecimal.ZERO))
                    .availableBalance((BigDecimal) payoutStats.getOrDefault("availableBalance", BigDecimal.ZERO))
                    .periodStart(thirtyDaysAgo)
                    .periodEnd(now)
                    .build();
        }).onErrorResume(e -> {
            log.error("Error getting dashboard stats for organizer {}: {}", organizerId, e.getMessage());
            return Mono.just(OrganizerDashboardStats.empty());
        });
    }

    @Override
    public Mono<OrganizerFinanceOverview> getFinanceOverview(String organizerId) {
        log.debug("Getting finance overview for organizer: {}", organizerId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);
        LocalDateTime endOfLastMonth = startOfMonth.minusSeconds(1);

        return Mono.zip(
                // 1. Balance from escrow accounts
                getBalanceFromEscrow(organizerId),
                // 2. Payout information
                getPayoutInfo(organizerId),
                // 3. Revenue breakdown
                getRevenueBreakdown(organizerId),
                // 4. This month's earnings
                getMonthlyEarnings(organizerId, startOfMonth, now),
                // 5. Last month's earnings
                getMonthlyEarnings(organizerId, startOfLastMonth, endOfLastMonth)
        ).map(tuple -> {
            Map<String, Object> balances = tuple.getT1();
            Map<String, Object> payoutInfo = tuple.getT2();
            Map<String, Object> revenue = tuple.getT3();
            BigDecimal thisMonthEarnings = tuple.getT4();
            BigDecimal lastMonthEarnings = tuple.getT5();

            return OrganizerFinanceOverview.builder()
                    .availableBalance((BigDecimal) balances.getOrDefault("available", BigDecimal.ZERO))
                    .pendingBalance((BigDecimal) balances.getOrDefault("pending", BigDecimal.ZERO))
                    .totalEarned((BigDecimal) balances.getOrDefault("totalEarned", BigDecimal.ZERO))
                    .currency("ZMW")
                    .pendingPayoutRequests((Integer) payoutInfo.getOrDefault("pendingCount", 0))
                    .lastPayoutDate((LocalDateTime) payoutInfo.get("lastPayoutDate"))
                    .lastPayoutAmount((BigDecimal) payoutInfo.get("lastPayoutAmount"))
                    .totalTicketRevenue((BigDecimal) revenue.getOrDefault("grossRevenue", BigDecimal.ZERO))
                    .totalRefunds((BigDecimal) revenue.getOrDefault("refunds", BigDecimal.ZERO))
                    .platformFees((BigDecimal) revenue.getOrDefault("fees", BigDecimal.ZERO))
                    .netEarnings((BigDecimal) revenue.getOrDefault("netEarnings", BigDecimal.ZERO))
                    .earningsThisMonth(thisMonthEarnings)
                    .earningsLastMonth(lastMonthEarnings)
                    .monthlyGrowth(calculatePercentageChange(lastMonthEarnings, thisMonthEarnings))
                    .build();
        }).onErrorResume(e -> {
            log.error("Error getting finance overview for organizer {}: {}", organizerId, e.getMessage());
            return Mono.just(OrganizerFinanceOverview.empty());
        });
    }

    @Override
    public Flux<OrganizerActivityItem> getRecentActivity(String organizerId, Integer limit) {
        int activityLimit = limit != null && limit > 0 ? Math.min(limit, 50) : 10;
        log.debug("Getting recent activity for organizer: {}, limit: {}", organizerId, activityLimit);

        // Get recent tickets (sales), check-ins, and payouts
        return Flux.merge(
                getRecentTicketSales(organizerId, activityLimit),
                getRecentCheckIns(organizerId, activityLimit),
                getRecentPayoutActivity(organizerId, activityLimit)
        )
        .sort(Comparator.comparing(OrganizerActivityItem::getTimestamp).reversed())
        .take(activityLimit);
    }

    @Override
    public Flux<OrganizerUpcomingEvent> getUpcomingEvents(String organizerId, Integer limit) {
        int eventLimit = limit != null && limit > 0 ? Math.min(limit, 20) : 5;
        log.debug("Getting upcoming events for organizer: {}, limit: {}", organizerId, eventLimit);

        // This would typically aggregate ticket data with event data from catalog-service
        // For now, return aggregated ticket data grouped by event
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)),
                Aggregation.group("eventId")
                        .first("eventId").as("eventId")
                        .first("eventTitle").as("title")
                        .first("eventDate").as("eventDateTime")
                        .count().as("ticketsSold")
                        .sum("price").as("revenue"),
                Aggregation.sort(Sort.Direction.ASC, "eventDateTime"),
                Aggregation.limit(eventLimit)
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .map(doc -> OrganizerUpcomingEvent.builder()
                        .id((String) doc.get("eventId"))
                        .title((String) doc.getOrDefault("title", "Untitled Event"))
                        .eventDateTime(parseDateTime(doc.get("eventDateTime")))
                        .ticketsSold(((Number) doc.getOrDefault("ticketsSold", 0)).intValue())
                        .totalCapacity(100) // Would come from event data
                        .status("published")
                        .revenue(toBigDecimal(doc.get("revenue")))
                        .currency("ZMW")
                        .build())
                .onErrorResume(e -> {
                    log.error("Error getting upcoming events for organizer {}: {}", organizerId, e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Mono<OrganizerTransactionOffsetPage> getTransactions(
            String organizerId,
            OrganizerTransactionFilterInput filter,
            OffsetPaginationInput pagination
    ) {
        log.debug("Getting transactions for organizer: {}", organizerId);

        int page = pagination != null ? pagination.page() : 0;
        int size = pagination != null ? pagination.size() : 20;
        int skip = page * size;

        // Build criteria
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(Criteria.where("organizerId").is(organizerId));

        if (filter != null) {
            if (filter.eventId() != null) {
                criteriaList.add(Criteria.where("eventId").is(filter.eventId()));
            }
            if (filter.startDate() != null) {
                criteriaList.add(Criteria.where("purchaseDate").gte(filter.startDate()));
            }
            if (filter.endDate() != null) {
                criteriaList.add(Criteria.where("purchaseDate").lte(filter.endDate()));
            }
        }

        Criteria criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        // Count total
        Mono<Long> countMono = mongoTemplate.count(
                org.springframework.data.mongodb.core.query.Query.query(criteria),
                Ticket.class
        );

        // Get page of tickets and convert to transactions
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.sort(Sort.Direction.DESC, "purchaseDate"),
                Aggregation.skip((long) skip),
                Aggregation.limit(size)
        );

        Flux<OrganizerTransaction> transactionsFlux = mongoTemplate
                .aggregate(aggregation, TICKETS_COLLECTION, Ticket.class)
                .map(this::ticketToTransaction);

        return Mono.zip(countMono, transactionsFlux.collectList())
                .map(tuple -> {
                    Long total = tuple.getT1();
                    List<OrganizerTransaction> transactions = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);

                    return OrganizerTransactionOffsetPage.builder()
                            .content(transactions)
                            .totalElements(total.intValue())
                            .totalPages(totalPages)
                            .page(page)
                            .size(size)
                            .hasNext(page < totalPages - 1)
                            .hasPrevious(page > 0)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error getting transactions for organizer {}: {}", organizerId, e.getMessage());
                    return Mono.just(OrganizerTransactionOffsetPage.empty());
                });
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - AGGREGATIONS
    // ========================================================================

    private Mono<Map<String, Object>> getRevenueAndTicketStats(String organizerId, LocalDateTime from, LocalDateTime to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("purchaseDate").gte(from).lte(to)
                        .and("status").in(TicketStatus.PURCHASED.name(), TicketStatus.VALIDATED.name(), TicketStatus.USED.name())),
                Aggregation.group()
                        .sum("price").as("totalRevenue")
                        .count().as("ticketsSold")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .next()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("totalRevenue", toBigDecimal(doc.get("totalRevenue")));
                    result.put("ticketsSold", ((Number) doc.getOrDefault("ticketsSold", 0)).intValue());
                    return result;
                })
                .defaultIfEmpty(new HashMap<>(Map.of("totalRevenue", BigDecimal.ZERO, "ticketsSold", 0)));
    }

    private Mono<Map<String, Object>> getAttendeeStats(String organizerId, LocalDateTime from, LocalDateTime to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("validatedAt").gte(from).lte(to)
                        .and("status").in(TicketStatus.VALIDATED.name(), TicketStatus.USED.name())),
                Aggregation.group().count().as("checkedIn")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .next()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("checkedIn", ((Number) doc.getOrDefault("checkedIn", 0)).intValue());
                    return result;
                })
                .defaultIfEmpty(new HashMap<>(Map.of("checkedIn", 0)));
    }

    private Mono<Map<String, Object>> getPayoutStats(String organizerId) {
        return Mono.zip(
                // Pending payouts
                payoutRequestRepository.findByOrganizerIdAndStatus(organizerId, PayoutRequestStatus.PENDING)
                        .map(PayoutRequest::getNetPayoutAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                // Available balance from escrow accounts
                escrowAccountRepository.findByOrganizerId(organizerId)
                        .filter(acc -> "ACTIVE".equals(acc.getStatus().name()) || "PAYOUT_ELIGIBLE".equals(acc.getStatus().name()))
                        .map(EventEscrowAccount::getCurrentBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ).map(tuple -> Map.of(
                "pendingPayouts", tuple.getT1(),
                "availableBalance", tuple.getT2()
        ));
    }

    private Mono<Map<String, Object>> getBalanceFromEscrow(String organizerId) {
        return escrowAccountRepository.findByOrganizerId(organizerId)
                .collectList()
                .map(accounts -> {
                    BigDecimal available = BigDecimal.ZERO;
                    BigDecimal pending = BigDecimal.ZERO;
                    BigDecimal totalEarned = BigDecimal.ZERO;

                    for (EventEscrowAccount acc : accounts) {
                        totalEarned = totalEarned.add(acc.getTotalDeposits() != null ? acc.getTotalDeposits() : BigDecimal.ZERO);
                        if ("PAYOUT_ELIGIBLE".equals(acc.getStatus().name())) {
                            available = available.add(acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO);
                        } else if ("ACTIVE".equals(acc.getStatus().name())) {
                            pending = pending.add(acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO);
                        }
                    }

                    return Map.of(
                            "available", available,
                            "pending", pending,
                            "totalEarned", totalEarned
                    );
                });
    }

    private Mono<Map<String, Object>> getPayoutInfo(String organizerId) {
        return Mono.zip(
                // Count pending payouts
                payoutRequestRepository.findByOrganizerIdAndStatus(organizerId, PayoutRequestStatus.PENDING)
                        .count()
                        .map(Long::intValue),
                // Get last completed payout
                payoutRequestRepository.findByOrganizerIdAndStatus(organizerId, PayoutRequestStatus.COMPLETED)
                        .sort(Comparator.comparing(PayoutRequest::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .next()
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("pendingCount", tuple.getT1());
            PayoutRequest lastPayout = tuple.getT2();
            if (lastPayout != null) {
                result.put("lastPayoutDate", lastPayout.getProcessedAt());
                result.put("lastPayoutAmount", lastPayout.getNetPayoutAmount());
            }
            return result;
        });
    }

    private Mono<Map<String, Object>> getRevenueBreakdown(String organizerId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)),
                Aggregation.group()
                        .sum("price").as("grossRevenue")
                        .sum("commissionAmount").as("fees")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .next()
                .map(doc -> {
                    BigDecimal gross = toBigDecimal(doc.get("grossRevenue"));
                    BigDecimal fees = toBigDecimal(doc.get("fees"));
                    Map<String, Object> result = new HashMap<>();
                    result.put("grossRevenue", gross);
                    result.put("fees", fees);
                    result.put("refunds", BigDecimal.ZERO); // Would need to aggregate from refund requests
                    result.put("netEarnings", gross.subtract(fees));
                    return result;
                })
                .defaultIfEmpty(new HashMap<>(Map.of(
                        "grossRevenue", BigDecimal.ZERO,
                        "fees", BigDecimal.ZERO,
                        "refunds", BigDecimal.ZERO,
                        "netEarnings", BigDecimal.ZERO
                )));
    }

    private Mono<BigDecimal> getMonthlyEarnings(String organizerId, LocalDateTime from, LocalDateTime to) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("purchaseDate").gte(from).lte(to)
                        .and("status").in(TicketStatus.PURCHASED.name(), TicketStatus.VALIDATED.name(), TicketStatus.USED.name())),
                Aggregation.group().sum("price").as("totalRevenue")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .next()
                .map(doc -> toBigDecimal(doc.get("totalRevenue")))
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Flux<OrganizerActivityItem> getRecentTicketSales(String organizerId, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("status").is(TicketStatus.PURCHASED.name())),
                Aggregation.sort(Sort.Direction.DESC, "purchaseDate"),
                Aggregation.limit(limit)
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Ticket.class)
                .map(ticket -> OrganizerActivityItem.builder()
                        .id(ticket.getId())
                        .type(OrganizerActivityItem.OrganizerActivityType.TICKET_SALE)
                        .message("Ticket sold for " + (ticket.getEventTitle() != null ? ticket.getEventTitle() : "event"))
                        .timestamp(ticket.getPurchaseDate())
                        .eventId(ticket.getEventId())
                        .eventTitle(ticket.getEventTitle())
                        .amount(ticket.getPrice())
                        .currency(ticket.getCurrency())
                        .build());
    }

    private Flux<OrganizerActivityItem> getRecentCheckIns(String organizerId, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("status").in(TicketStatus.VALIDATED.name(), TicketStatus.USED.name())
                        .and("validatedAt").exists(true)),
                Aggregation.sort(Sort.Direction.DESC, "validatedAt"),
                Aggregation.limit(limit)
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Ticket.class)
                .map(ticket -> OrganizerActivityItem.builder()
                        .id(ticket.getId() + "-checkin")
                        .type(OrganizerActivityItem.OrganizerActivityType.CHECK_IN)
                        .message("Guest checked in at " + (ticket.getEventTitle() != null ? ticket.getEventTitle() : "event"))
                        .timestamp(ticket.getValidatedAt())
                        .eventId(ticket.getEventId())
                        .eventTitle(ticket.getEventTitle())
                        .build());
    }

    private Flux<OrganizerActivityItem> getRecentPayoutActivity(String organizerId, int limit) {
        return payoutRequestRepository.findByOrganizerId(organizerId)
                .sort(Comparator.comparing(PayoutRequest::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .take(limit)
                .map(payout -> {
                    OrganizerActivityItem.OrganizerActivityType type =
                            payout.getStatus() == PayoutRequestStatus.COMPLETED
                                    ? OrganizerActivityItem.OrganizerActivityType.PAYOUT_COMPLETED
                                    : OrganizerActivityItem.OrganizerActivityType.PAYOUT_REQUESTED;

                    String message = payout.getStatus() == PayoutRequestStatus.COMPLETED
                            ? "Payout of K " + payout.getNetPayoutAmount() + " processed"
                            : "Payout request of K " + payout.getRequestedAmount() + " submitted";

                    return OrganizerActivityItem.builder()
                            .id(payout.getId())
                            .type(type)
                            .message(message)
                            .timestamp(payout.getStatus() == PayoutRequestStatus.COMPLETED
                                    ? payout.getProcessedAt() : payout.getRequestedAt())
                            .eventId(payout.getEventId())
                            .eventTitle(payout.getEventTitle())
                            .amount(payout.getNetPayoutAmount())
                            .currency(payout.getCurrency())
                            .build();
                });
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private OrganizerTransaction ticketToTransaction(Ticket ticket) {
        return OrganizerTransaction.builder()
                .id(ticket.getId())
                .type(OrganizerTransaction.OrganizerTransactionType.TICKET_SALE)
                .description("Ticket sale - " + (ticket.getEventTitle() != null ? ticket.getEventTitle() : "Event"))
                .amount(ticket.getPrice())
                .currency(ticket.getCurrency() != null ? ticket.getCurrency() : "ZMW")
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN")
                .timestamp(ticket.getPurchaseDate())
                .eventId(ticket.getEventId())
                .eventTitle(ticket.getEventTitle())
                .ticketId(ticket.getId())
                .reference(ticket.getTicketNumber())
                .build();
    }

    private Float calculatePercentageChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? 100.0f : 0.0f;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .floatValue();
    }

    private Float calculatePercentageChange(Integer previous, Integer current) {
        if (previous == null || previous == 0) {
            return current != null && current > 0 ? 100.0f : 0.0f;
        }
        return ((float) (current - previous) / previous) * 100;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return BigDecimal.ZERO;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof Date) return LocalDateTime.ofInstant(((Date) value).toInstant(), java.time.ZoneId.systemDefault());
        return LocalDateTime.now();
    }
}
