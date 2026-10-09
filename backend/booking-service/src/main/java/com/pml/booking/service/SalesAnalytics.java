package com.pml.booking.service;

import com.pml.booking.domain.SalesBuckets;
import com.pml.booking.domain.SalesBuckets.Bucket;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.security.OrganizerAccess;
import com.pml.shared.constants.PlatformTime;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.Permission;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sales read from the tickets themselves: a time series per event, and when in the week people buy.
 *
 * <p>A sale counts on the day it was made even if it was refunded since; the refund shows in its own
 * column so the series can be read both ways. Days are Lusaka days.
 */
@Service
public class SalesAnalytics {

    /** A seat that was sold at some point: everything but the states a seat never reached by being bought. */
    private static final Set<TicketStatus> SOLD_AT_SOME_POINT = Set.of(
            TicketStatus.ISSUED, TicketStatus.VALIDATED, TicketStatus.TRANSFERRED, TicketStatus.REFUND_PENDING,
            TicketStatus.REFUNDED, TicketStatus.EXPIRED, TicketStatus.CANCELLED);

    private final ReactiveMongoTemplate template;
    private final OrganizerAccess access;
    private final Clock clock;

    public SalesAnalytics(ReactiveMongoTemplate template, OrganizerAccess access, Clock clock) {
        this.template = template;
        this.access = access;
        this.clock = clock;
    }

    public record SalesPoint(Instant bucketStart, Instant bucketEnd, int tickets, int orders, BigDecimal grossRevenue,
                             BigDecimal netRevenue, BigDecimal refundedAmount) {
    }

    public record HeatCell(int dayOfWeek, int hour, int purchases, int tickets, BigDecimal revenue) {
    }

    // ---- sales over time -----------------------------------------------------------------------

    public Mono<List<SalesPoint>> salesOverTime(String eventId, Instant from, Instant to, Bucket bucket) {
        Instant end = to == null ? clock.instant() : to;
        Bucket granularity = bucket == null ? Bucket.DAY : bucket;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        var violations = checkRange(start, end, granularity);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        return access.requireEvent(eventId, Permission.ANALYTICS_VIEW).flatMap(event -> {
            Instant first = SalesBuckets.floor(start, granularity).toInstant();
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("eventId").is(eventId)
                            .and("status").in(SOLD_AT_SOME_POINT)
                            .and("purchaseDate").gte(first).lte(end)),
                    context -> new Document("$group", new Document("_id", truncated(granularity))
                            .append("tickets", new Document("$sum", 1))
                            .append("orders", new Document("$addToSet", "$reservationId"))
                            .append("gross", new Document("$sum", "$price"))
                            .append("net", new Document("$sum", "$netAmount"))
                            .append("refunded", new Document("$sum", new Document("$ifNull", List.of("$refundedAmount", 0))))));
            return template.aggregate(aggregation, Ticket.class, Document.class).collectList()
                    .map(rows -> series(rows, start, end, granularity));
        });
    }

    static List<FieldViolation> checkRange(Instant from, Instant to, Bucket bucket) {
        List<FieldViolation> violations = new ArrayList<>();
        if (from.isAfter(to)) {
            violations.add(new FieldViolation("to", "must not be before from"));
        } else if (Duration.between(from, to).compareTo(bucket.maxRange()) > 0) {
            violations.add(new FieldViolation("to", "a " + bucket + " series covers at most " + bucket.maxRange().toDays() + " days"));
        }
        return violations;
    }

    private static Document truncated(Bucket bucket) {
        Document spec = new Document("date", "$purchaseDate").append("unit", bucket.unit()).append("timezone", PlatformTime.ZONE.getId());
        if (bucket == Bucket.WEEK) {
            spec.append("startOfWeek", "monday");
        }
        return new Document("$dateTrunc", spec);
    }

    static List<SalesPoint> series(List<Document> rows, Instant from, Instant to, Bucket bucket) {
        Map<Instant, Document> byStart = new HashMap<>();
        for (Document row : rows) {
            byStart.put(((java.util.Date) row.get("_id")).toInstant(), row);
        }
        List<SalesPoint> points = new ArrayList<>();
        for (ZonedDateTime start : SalesBuckets.between(from, to, bucket)) {
            Document row = byStart.get(start.toInstant());
            Instant end = SalesBuckets.next(start, bucket).toInstant();
            points.add(row == null
                    ? new SalesPoint(start.toInstant(), end, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new SalesPoint(start.toInstant(), end, ((Number) row.get("tickets")).intValue(),
                            ((java.util.Collection<?>) row.get("orders")).size(), money(row.get("gross")), money(row.get("net")),
                            money(row.get("refunded"))));
        }
        return points;
    }

    // ---- when people buy -----------------------------------------------------------------------

    /**
     * Purchases across the week, as a full 7 x 24 grid (ISO day of week, Monday = 1) so a heatmap draws
     * the quiet cells too. For one event, one organization, or the whole platform (staff only).
     */
    public Mono<List<HeatCell>> purchasesByDayAndHour(Instant from, Instant to, String eventId, String organizationId) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(90)) : from;
        if (start.isAfter(end) || Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("to", "the range must be at most 366 days and not negative"))));
        }
        Mono<Criteria> scope;
        if (eventId != null && !eventId.isBlank()) {
            scope = access.requireEvent(eventId, Permission.ANALYTICS_VIEW).map(event -> Criteria.where("eventId").is(eventId));
        } else if (organizationId != null && !organizationId.isBlank()) {
            scope = access.requireOrganizations(organizationId, Permission.ANALYTICS_VIEW)
                    .map(organizations -> Criteria.where("organizationId").in(organizations));
        } else {
            scope = OrganizerAccess.isPlatformStaff().flatMap(staff -> staff
                    ? Mono.just(new Criteria())
                    : Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                            "name an event or an organization; the whole platform is for platform staff")));
        }
        return scope.flatMap(base -> {
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.match(new Criteria().andOperator(base, Criteria.where("status").in(SOLD_AT_SOME_POINT)
                            .and("purchaseDate").gte(start).lte(end))),
                    context -> new Document("$group", new Document("_id", new Document()
                                    .append("day", new Document("$isoDayOfWeek", new Document("date", "$purchaseDate")
                                            .append("timezone", PlatformTime.ZONE.getId())))
                                    .append("hour", new Document("$hour", new Document("date", "$purchaseDate")
                                            .append("timezone", PlatformTime.ZONE.getId()))))
                            .append("orders", new Document("$addToSet", "$reservationId"))
                            .append("tickets", new Document("$sum", 1))
                            .append("revenue", new Document("$sum", "$price"))));
            return template.aggregate(aggregation, Ticket.class, Document.class).collectList().map(SalesAnalytics::grid);
        });
    }

    static List<HeatCell> grid(List<Document> rows) {
        Map<Integer, Document> byCell = new HashMap<>();
        for (Document row : rows) {
            Document id = (Document) row.get("_id");
            byCell.put(id.getInteger("day") * 100 + id.getInteger("hour"), row);
        }
        List<HeatCell> cells = new ArrayList<>(168);
        for (int day = 1; day <= 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                Document row = byCell.get(day * 100 + hour);
                cells.add(row == null ? new HeatCell(day, hour, 0, 0, BigDecimal.ZERO)
                        : new HeatCell(day, hour, ((java.util.Collection<?>) row.get("orders")).size(),
                                ((Number) row.get("tickets")).intValue(), money(row.get("revenue"))));
            }
        }
        return cells;
    }

    private static BigDecimal money(Object value) {
        if (value instanceof Decimal128 decimal) {
            return decimal.bigDecimalValue();
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
