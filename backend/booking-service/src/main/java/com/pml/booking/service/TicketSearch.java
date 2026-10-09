package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import com.pml.booking.web.graphql.dto.TicketFilterInput;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A page of tickets matching every field of a ticket filter, queried and paged by the database.
 *
 * <p>Tickets are never loaded whole and filtered in memory: the count and the page are two bounded
 * queries. The search text is matched literally — quoted into the pattern — against the ticket
 * number, the buyer's name and e-mail, and the event title.
 */
@Component
@RequiredArgsConstructor
public class TicketSearch {

    private final ReactiveMongoTemplate mongo;

    /**
     * @param scope what the caller may see at all — an organizer's tickets, or everything for an
     *              administrator; the filter narrows within it and cannot widen it
     */
    public Mono<Page> page(Criteria scope, TicketFilterInput filter, OffsetPaginationInput pagination) {
        List<FieldViolation> violations = check(filter);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        OffsetPaginationInput page = pagination != null ? pagination : OffsetPaginationInput.defaults();
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(scope);
        conditions.addAll(conditions(filter));
        Criteria criteria = new Criteria().andOperator(conditions);
        int limit = page.getLimit();
        int offset = page.getOffset();
        Query pageQuery = new Query(criteria)
                .with(Sort.by(Sort.Order.desc("purchaseDate"), Sort.Order.asc("_id")))
                .skip(offset)
                .limit(limit);
        return Mono.zip(mongo.count(new Query(criteria), Ticket.class), mongo.find(pageQuery, Ticket.class).collectList())
                .map(result -> {
                    long total = result.getT1();
                    int totalPages = (int) Math.ceil((double) total / limit);
                    return new Page(result.getT2(), new PaginationInfo((int) total, limit, page.page(), totalPages,
                            offset + limit < total, page.page() > 1));
                });
    }

    public record Page(List<Ticket> tickets, PaginationInfo pagination) {
    }

    static List<FieldViolation> check(TicketFilterInput filter) {
        List<FieldViolation> violations = new ArrayList<>();
        if (filter == null) {
            return violations;
        }
        String search = filter.searchQuery() == null ? "" : filter.searchQuery().trim();
        if (!search.isEmpty() && search.length() < 3) {
            violations.add(new FieldViolation("filter.searchQuery", "must be at least 3 characters"));
        }
        if (filter.purchaseDateAfter() != null && filter.purchaseDateBefore() != null
                && filter.purchaseDateAfter().isAfter(filter.purchaseDateBefore())) {
            violations.add(new FieldViolation("filter.purchaseDateBefore", "must not be before purchaseDateAfter"));
        }
        return violations;
    }

    static List<Criteria> conditions(TicketFilterInput filter) {
        List<Criteria> conditions = new ArrayList<>();
        if (filter == null) {
            return conditions;
        }
        if (filter.eventId() != null) conditions.add(Criteria.where("eventId").is(filter.eventId()));
        if (filter.buyerId() != null) conditions.add(Criteria.where("buyerId").is(filter.buyerId()));
        if (filter.organizerId() != null) conditions.add(Criteria.where("organizerId").is(filter.organizerId()));
        if (filter.status() != null) conditions.add(Criteria.where("status").is(filter.status()));
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            conditions.add(Criteria.where("status").in(filter.statuses()));
        }
        if (filter.category() != null) conditions.add(Criteria.where("ticketCategoryCode").is(filter.category()));
        if (filter.purchaseDateAfter() != null || filter.purchaseDateBefore() != null) {
            Criteria purchased = Criteria.where("purchaseDate");
            if (filter.purchaseDateAfter() != null) purchased.gte(filter.purchaseDateAfter());
            if (filter.purchaseDateBefore() != null) purchased.lte(filter.purchaseDateBefore());
            conditions.add(purchased);
        }
        if (filter.searchQuery() != null && !filter.searchQuery().isBlank()) {
            Pattern literal = Pattern.compile(Pattern.quote(filter.searchQuery().trim()), Pattern.CASE_INSENSITIVE);
            conditions.add(new Criteria().orOperator(
                    Criteria.where("ticketNumber").regex(literal),
                    Criteria.where("buyerName").regex(literal),
                    Criteria.where("buyerEmail").regex(literal),
                    Criteria.where("eventTitle").regex(literal)));
        }
        return conditions;
    }
}
