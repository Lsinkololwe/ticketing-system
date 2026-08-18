package com.pml.booking.service.impl;

import com.pml.shared.constants.EscrowStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.security.ActorOrganizationResolver;
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
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Arrays;

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
    private final ActorOrganizationResolver actorOrganizationResolver;

    private static final String TICKETS_COLLECTION = "tickets";
    private static final String ESCROW_ACCOUNTS_COLLECTION = "booking_escrow_accounts";
    private static final String PAYOUT_REQUESTS_COLLECTION = "booking_payout_requests";

    @Override
    public Mono<OrganizerDashboardStats> getDashboardStats(String organizerId) {
        // Resolved once, from the authenticated actor. Ticket and check-in
        // queries below still scope by organizer because they are about what
        // this PERSON did; only the money is scoped by organization.
        return actorOrganizationResolver.resolve(organizerId)
                .flatMap(organizationId -> dashboardStatsFor(organizerId, organizationId));
    }

    private Mono<OrganizerDashboardStats> dashboardStatsFor(String organizerId, String organizationId) {
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
                getPayoutStats(organizationId)
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
        return actorOrganizationResolver.resolve(organizerId)
                .flatMap(organizationId -> financeOverviewFor(organizerId, organizationId));
    }

    private Mono<OrganizerFinanceOverview> financeOverviewFor(String organizerId, String organizationId) {
        log.debug("Getting finance overview for organizer: {}", organizerId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startOfLastMonth = startOfMonth.minusMonths(1);
        LocalDateTime endOfLastMonth = startOfMonth.minusSeconds(1);

        return Mono.zip(
                // 1. Balance from escrow accounts
                getBalanceFromEscrow(organizationId),
                // 2. Payout information
                getPayoutInfo(organizationId),
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
        return actorOrganizationResolver.resolve(organizerId)
                .flatMapMany(organizationId -> recentActivityFor(organizerId, organizationId, limit));
    }

    private Flux<OrganizerActivityItem> recentActivityFor(
            String organizerId, String organizationId, Integer limit) {
        int activityLimit = limit != null && limit > 0 ? Math.min(limit, 50) : 10;
        log.debug("Getting recent activity for organizer: {}, limit: {}", organizerId, activityLimit);

        // Get recent tickets (sales), check-ins, and payouts
        return Flux.merge(
                getRecentTicketSales(organizerId, activityLimit),
                getRecentCheckIns(organizerId, activityLimit),
                getRecentPayoutActivity(organizationId, activityLimit)
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
                        .sum(asDecimal("price")).as("revenue"),
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
                        .and("status").in(SOLD_STATES)),
                Aggregation.group()
                        .sum(asDecimal("price")).as("totalRevenue")
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
                        .and("status").is(TicketStatus.VALIDATED.name())),
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

    private Mono<Map<String, Object>> getPayoutStats(String organizationId) {
        return Mono.zip(
                // Pending payouts
                payoutRequestRepository.findByOrganizationIdAndStatus(organizationId, PayoutRequestStatus.PENDING)
                        .map(PayoutRequest::getSettledAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                // Available balance from escrow accounts
                escrowAccountRepository.findByOrganizationId(organizationId)
                        .filter(acc -> "ACTIVE".equals(acc.getStatus().name()) || "PAYOUT_ELIGIBLE".equals(acc.getStatus().name()))
                        .map(EventEscrowAccount::getCurrentBalance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ).map(tuple -> Map.of(
                "pendingPayouts", tuple.getT1(),
                "availableBalance", tuple.getT2()
        ));
    }

    private Mono<Map<String, Object>> getBalanceFromEscrow(String organizationId) {
        return escrowAccountRepository.findByOrganizationId(organizationId)
                .collectList()
                .map(accounts -> {
                    BigDecimal available = BigDecimal.ZERO;
                    BigDecimal pending = BigDecimal.ZERO;
                    BigDecimal totalEarned = BigDecimal.ZERO;

                    for (EventEscrowAccount acc : accounts) {
                        totalEarned = totalEarned.add(acc.getTotalCredited() != null ? acc.getTotalCredited() : BigDecimal.ZERO);
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

    private Mono<Map<String, Object>> getPayoutInfo(String organizationId) {
        return Mono.zip(
                // Count pending payouts
                payoutRequestRepository.findByOrganizationIdAndStatus(organizationId, PayoutRequestStatus.PENDING)
                        .count()
                        .map(Long::intValue),
                // Get last completed payout
                payoutRequestRepository.findByOrganizationIdAndStatus(organizationId, PayoutRequestStatus.COMPLETED)
                        .sort(Comparator.comparing(PayoutRequest::getProcessedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .next()
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("pendingCount", tuple.getT1());
            PayoutRequest lastPayout = tuple.getT2();
            if (lastPayout != null) {
                result.put("lastPayoutDate", lastPayout.getProcessedAt());
                result.put("lastPayoutAmount", lastPayout.getSettledAmount());
            }
            return result;
        });
    }

    private Mono<Map<String, Object>> getRevenueBreakdown(String organizerId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)),
                Aggregation.group()
                        .sum(asDecimal("price")).as("grossRevenue")
                        .sum(asDecimal("commissionAmount")).as("fees")
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
                        .and("status").in(SOLD_STATES)),
                Aggregation.group().sum(asDecimal("price")).as("totalRevenue")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .next()
                .map(doc -> toBigDecimal(doc.get("totalRevenue")))
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Flux<OrganizerActivityItem> getRecentTicketSales(String organizerId, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("status").is(TicketStatus.ISSUED.name())),
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
                        .and("status").is(TicketStatus.VALIDATED.name())
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

    private Flux<OrganizerActivityItem> getRecentPayoutActivity(String organizationId, int limit) {
        return payoutRequestRepository.findByOrganizationId(organizationId)
                .sort(Comparator.comparing(PayoutRequest::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .take(limit)
                .map(payout -> {
                    OrganizerActivityItem.OrganizerActivityType type =
                            payout.getStatus() == PayoutRequestStatus.COMPLETED
                                    ? OrganizerActivityItem.OrganizerActivityType.PAYOUT_COMPLETED
                                    : OrganizerActivityItem.OrganizerActivityType.PAYOUT_REQUESTED;

                    String message = payout.getStatus() == PayoutRequestStatus.COMPLETED
                            ? "Payout of K " + payout.getSettledAmount() + " processed"
                            : "Payout request of K " + payout.getRequestedAmount() + " submitted";

                    return OrganizerActivityItem.builder()
                            .id(payout.getId())
                            .type(type)
                            .message(message)
                            .timestamp(payout.getStatus() == PayoutRequestStatus.COMPLETED
                                    ? payout.getProcessedAt() : payout.getRequestedAt())
                            .eventId(payout.getEventId())
                            .eventTitle(payout.getEventTitle())
                            .amount(payout.getSettledAmount())
                            .currency(payout.getCurrency())
                            .build();
                });
    }

    // ========================================================================
    // DASHBOARD ANALYTICS
    // ========================================================================

    /**
     * Sum a money field as a decimal.
     *
     * <p><b>Why this exists.</b> Spring Data MongoDB stores {@code BigDecimal}
     * as a BSON <em>String</em> by default, not Decimal128. MongoDB's
     * {@code $sum} silently ignores non-numeric values, so a plain
     * {@code .sum("price")} over this collection returns {@code 0} no matter how
     * many tickets were sold — no error, no warning, just a zero.
     *
     * <p>{@code $convert} coerces the string to a decimal inside the pipeline,
     * which works against the documents already in the database. {@code onError}
     * and {@code onNull} both fall back to zero so one malformed row cannot fail
     * an entire dashboard query.
     *
     * <p>The alternative — registering a Decimal128 converter so BigDecimal is
     * stored numerically — is the better long-term fix, but it is a data
     * migration: every existing document holds a string, and a numeric-only
     * pipeline would silently skip all of them. That decision is called out in
     * the handover notes rather than made here.
     */
    private static AggregationExpression asDecimal(String field) {
        return ConvertOperators.Convert.convertValueOf(field)
                .to("decimal")
                .onErrorReturn(0)
                .onNullReturn(0);
    }

    /**
     * Ticket states that represent a completed sale — the revenue basis.
     *
     * <p>Derived from {@link TicketStatus#isSold()} rather than listed, because
     * this file had three separate hand-written copies of the list and they had
     * already drifted from each other.
     */
    private static final List<String> SOLD_STATES = Arrays.stream(TicketStatus.values())
            .filter(TicketStatus::isSold)
            .map(TicketStatus::name)
            .toList();

    /** Tiers below this share of the total are folded into a single "Other" row. */
    private static final double MIN_TIER_SHARE_PCT = 1.0;

    @Override
    public Flux<OrganizerRevenuePoint> getRevenueSeries(String organizerId, Integer months) {
        int window = months != null && months > 0 ? Math.min(months, 24) : 6;

        // Bound the series at the START of the current month, so the partial
        // month in progress is excluded rather than drawn as a short column.
        LocalDateTime seriesEnd = LocalDateTime.now()
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS);
        LocalDateTime seriesStart = seriesEnd.minusMonths(window);

        log.debug("Getting {}-month revenue series for organizer {} ({} .. {})",
                window, organizerId, seriesStart, seriesEnd);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("purchaseDate").gte(seriesStart).lt(seriesEnd)
                        .and("status").in(SOLD_STATES)),
                Aggregation.project("price")
                        .and(DateOperators.Year.yearOf("purchaseDate")).as("year")
                        .and(DateOperators.Month.monthOf("purchaseDate")).as("month"),
                Aggregation.group("year", "month")
                        .sum(asDecimal("price")).as("revenue")
                        .count().as("ticketsSold")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                // Group key lands under _id as a nested {year, month} document.
                // Rows without a readable key are dropped rather than folded
                // into an arbitrary month, which would misattribute revenue.
                .filter(doc -> doc.get("_id") instanceof Map<?, ?> key
                        && key.get("year") instanceof Number
                        && key.get("month") instanceof Number)
                .collectMap(
                        doc -> {
                            Map<?, ?> key = (Map<?, ?>) doc.get("_id");
                            return YearMonth.of(
                                    ((Number) key.get("year")).intValue(),
                                    ((Number) key.get("month")).intValue());
                        },
                        doc -> doc
                )
                // Emit EVERY month in the window, including months with no
                // sales. A gap in the x-axis would misrepresent a flat month as
                // a shorter time span.
                .flatMapMany(byMonth -> Flux.range(0, window).map(offset -> {
                    YearMonth period = YearMonth.from(seriesStart.plusMonths(offset));
                    Map<?, ?> row = byMonth.get(period);
                    return OrganizerRevenuePoint.forMonth(period.atDay(1))
                            .revenue(row != null ? toBigDecimal(row.get("revenue")) : BigDecimal.ZERO)
                            .ticketsSold(row != null ? toInt(row.get("ticketsSold")) : 0)
                            .currency("ZMW")
                            .build();
                }))
                .onErrorResume(e -> {
                    log.error("Error building revenue series for organizer {}: {}", organizerId, e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Mono<OrganizerTicketMix> getTicketMix(String organizerId) {
        log.debug("Getting ticket mix for organizer: {}", organizerId);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("status").in(SOLD_STATES)),
                Aggregation.group("ticketCategoryName")
                        .count().as("count")
                        .sum(asDecimal("price")).as("revenue"),
                Aggregation.sort(Sort.Direction.DESC, "count")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .collectList()
                .map(docs -> {
                    int totalSold = docs.stream()
                            .mapToInt(d -> toInt(d.get("count")))
                            .sum();
                    BigDecimal totalRevenue = docs.stream()
                            .map(d -> toBigDecimal(d.get("revenue")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    List<OrganizerShareRow> rows = new ArrayList<>();
                    int otherCount = 0;
                    BigDecimal otherRevenue = BigDecimal.ZERO;

                    for (Map<?, ?> doc : docs) {
                        int count = toInt(doc.get("count"));
                        BigDecimal revenue = toBigDecimal(doc.get("revenue"));
                        double share = totalSold > 0 ? (count * 100.0) / totalSold : 0.0;

                        // Fold slivers together — a 0.3% bar cannot carry a label.
                        if (share < MIN_TIER_SHARE_PCT) {
                            otherCount += count;
                            otherRevenue = otherRevenue.add(revenue);
                            continue;
                        }

                        Object name = doc.get("_id");
                        rows.add(OrganizerShareRow.builder()
                                .name(name != null ? String.valueOf(name) : "Unspecified")
                                .count(count)
                                .revenue(revenue)
                                .build());
                    }

                    if (otherCount > 0) {
                        rows.add(OrganizerShareRow.builder()
                                .name("Other")
                                .count(otherCount)
                                .revenue(otherRevenue)
                                .build());
                    }

                    return OrganizerTicketMix.builder()
                            .totalSold(totalSold)
                            .totalRevenue(totalRevenue)
                            .currency("ZMW")
                            .rows(List.copyOf(rows))
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error building ticket mix for organizer {}: {}", organizerId, e.getMessage());
                    return Mono.just(OrganizerTicketMix.empty());
                });
    }

    @Override
    public Mono<OrganizerCheckInRate> getCheckInRate(String organizerId) {
        log.debug("Getting check-in rate for organizer: {}", organizerId);

        LocalDateTime now = LocalDateTime.now();

        // Most recent event that has already run. Grouping by eventId and
        // sorting descending on the event date gives us that in one pass.
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizerId").is(organizerId)
                        .and("status").in(SOLD_STATES)),
                // Flag scanned tickets in a projection first.
                //
                // `$ifNull` rather than a Criteria-based `$ne: null`: a ticket
                // that was never scanned may have validatedAt stored as null OR
                // absent entirely, depending on how it was written, and the two
                // do not compare alike inside `$cond`. `$ifNull` collapses both
                // to false. Getting this wrong counts every ticket as scanned
                // and reports a 100% check-in rate for every event.
                Aggregation.project("eventId", "eventTitle", "eventDate")
                        .and(ConditionalOperators
                                .when(ConditionalOperators.ifNull("validatedAt").then(false))
                                .then(1).otherwise(0)).as("scannedFlag"),
                Aggregation.group("eventId")
                        .first("eventId").as("eventId")
                        .first("eventTitle").as("eventTitle")
                        .first("eventDate").as("eventDate")
                        .count().as("issued")
                        // validatedAt is one timestamp per ticket, so summing
                        // the flag deduplicates a ticket re-presented at the
                        // gate — the rate can never exceed 100%.
                        .sum("scannedFlag").as("scanned"),
                Aggregation.sort(Sort.Direction.DESC, "eventDate")
        );

        return mongoTemplate.aggregate(aggregation, TICKETS_COLLECTION, Map.class)
                .filter(doc -> {
                    LocalDateTime eventDate = parseNullableDateTime(doc.get("eventDate"));
                    return eventDate != null && eventDate.isBefore(now);
                })
                .next()
                .map(doc -> OrganizerCheckInRate.builder()
                        .eventId((String) doc.get("eventId"))
                        .eventTitle((String) doc.getOrDefault("eventTitle", "Untitled event"))
                        .eventDateTime(parseNullableDateTime(doc.get("eventDate")))
                        .issued(((Number) doc.getOrDefault("issued", 0)).intValue())
                        .scanned(((Number) doc.getOrDefault("scanned", 0)).intValue())
                        .build())
                .onErrorResume(e -> {
                    log.error("Error building check-in rate for organizer {}: {}", organizerId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<OrganizerPayoutWindow> getPayoutWindow(String organizerId) {
        log.debug("Getting payout window for organizer: {}", organizerId);

        // The organization is DERIVED from the authenticated actor, never taken
        // from input. There is no id for a caller to substitute, so the tenant
        // cannot be chosen — which is a stronger guarantee than checking one.
        return actorOrganizationResolver.resolve(organizerId)
                .flatMapMany(escrowAccountRepository::findByOrganizationId)
                .collectList()
                .map(accounts -> {
                    BigDecimal availableNow = BigDecimal.ZERO;
                    BigDecimal pendingRelease = BigDecimal.ZERO;
                    EventEscrowAccount nextToRelease = null;

                    for (EventEscrowAccount account : accounts) {
                        BigDecimal balance = account.getCurrentBalance() != null
                                ? account.getCurrentBalance() : BigDecimal.ZERO;
                        String status = account.getStatus() != null ? account.getStatus().name() : "";

                        if ("PAYOUT_ELIGIBLE".equals(status)) {
                            availableNow = availableNow.add(balance);
                        } else if ("ACTIVE".equals(status)) {
                            pendingRelease = pendingRelease.add(balance);
                            // The window shown is the one unlocking soonest.
                            if (account.getHoldUntil() != null
                                    && (nextToRelease == null
                                        || account.getHoldUntil().isBefore(nextToRelease.getHoldUntil()))) {
                                nextToRelease = account;
                            }
                        }
                    }

                    if (nextToRelease == null) {
                        return OrganizerPayoutWindow.builder()
                                .availableNow(availableNow)
                                .pendingRelease(pendingRelease)
                                .currency("ZMW")
                                .build();
                    }

                    return OrganizerPayoutWindow.windowBetween(
                                    nextToRelease.getCreatedAt(),
                                    nextToRelease.getHoldUntil().toInstant(ZoneOffset.UTC),
                                    Instant.now())
                            .availableNow(availableNow)
                            .pendingRelease(pendingRelease)
                            .currency("ZMW")
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error building payout window for organizer {}: {}", organizerId, e.getMessage());
                    return Mono.just(OrganizerPayoutWindow.empty());
                });
    }

    @Override
    public Flux<OrganizerPayoutSource> getPayoutSources(String organizerId) {
        log.debug("Getting payout sources for organizer: {}", organizerId);

        return actorOrganizationResolver.resolve(organizerId)
                .flatMapMany(escrowAccountRepository::findByOrganizationId)
                .filter(account -> account.getStatus() != null
                        && EscrowStatus.PAYOUT_ELIGIBLE.equals(account.getStatus()))
                // A zero-balance account is not a payout source. Listing it
                // would let the organizer submit a request the backend then
                // rejects for insufficient funds.
                .filter(account -> account.getCurrentBalance() != null
                        && account.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0)
                .map(account -> OrganizerPayoutSource.builder()
                        .escrowAccountId(account.getId())
                        .eventId(account.getEventId())
                        .eventTitle(account.getEventTitle())
                        .availableAmount(account.getCurrentBalance())
                        .currency(account.getCurrency() != null ? account.getCurrency() : "ZMW")
                        .eligibleSince(account.getPayoutEligibleAt() != null
                                ? LocalDateTime.ofInstant(account.getPayoutEligibleAt(), ZoneOffset.UTC)
                                : null)
                        .build())
                // Oldest eligible first: money that has been sitting longest
                // should be the first thing the organizer is offered.
                .sort(Comparator.comparing(
                        OrganizerPayoutSource::getEligibleSince,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .onErrorResume(e -> {
                    log.error("Error listing payout sources for organizer {}: {}", organizerId, e.getMessage());
                    return Flux.empty();
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

    /**
     * Null-safe numeric coercion for aggregation results.
     *
     * <p>Aggregation rows come back as a raw {@code Map}, so {@code getOrDefault}
     * against a wildcard-typed map will not compile. Reading then coercing keeps
     * the call sites generic-clean and treats a missing key and a null value
     * identically — both mean "no rows in this bucket", which is zero.
     */
    private int toInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Coerce an aggregation result value to BigDecimal.
     *
     * <p><b>Decimal128 must be handled explicitly.</b> {@code Ticket.price} is a
     * BigDecimal, which Spring Data stores as BSON {@code Decimal128}, and
     * {@code $sum} over Decimal128 yields Decimal128. {@link org.bson.types.Decimal128}
     * does <em>not</em> extend {@link Number}, so without this branch every
     * money figure computed by an aggregation silently fell through to
     * {@code BigDecimal.ZERO} — including {@code myDashboardStats.totalRevenue}
     * and the finance overview, which reported K 0 regardless of sales.
     *
     * <p>Caught by {@code OrganizerDashboardAnalyticsIntegrationTest}, which runs
     * these pipelines against a real MongoDB rather than a mocked template.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof org.bson.types.Decimal128 decimal128) return decimal128.bigDecimalValue();
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof Date) return LocalDateTime.ofInstant(((Date) value).toInstant(), java.time.ZoneId.systemDefault());
        return LocalDateTime.now();
    }

    /**
     * Like {@link #parseDateTime(Object)} but returns null instead of "now" for
     * an unparseable value.
     *
     * <p>The distinction matters for the check-in tile: {@code Ticket.eventDate}
     * is stored as a String, and defaulting an unreadable date to now would make
     * a future event look like one that has already run, then report a 0%
     * check-in rate for it.
     */
    private LocalDateTime parseNullableDateTime(Object value) {
        if (value instanceof LocalDateTime dt) return dt;
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC);
                } catch (DateTimeParseException stillUnparseable) {
                    log.debug("Unparseable eventDate '{}' — excluded from date-sensitive aggregates", s);
                    return null;
                }
            }
        }
        return null;
    }
}
