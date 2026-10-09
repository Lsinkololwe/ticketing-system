package com.pml.catalog.service;

import com.pml.catalog.domain.enums.EventDiscoverySort;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.web.graphql.dto.EventDiscoveryFilterInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The public event feed: published events that have not ended, narrowed by the five discovery
 * filters and nothing else.
 *
 * <p>The filters are category, city, a start-date range, a price range over each event's cheapest
 * on-sale tier, and free text — each combination served by an index of {@code catalog_events}. Any
 * other filter the input type offers is refused rather than run as an unindexed scan. A page holds
 * at most 100 events and the feed stops at {@code catalog.discovery.max-depth}: past that the
 * caller narrows the filter, so the catalogue cannot be walked end to end.
 *
 * <p>The cursor carries a position. Forging one reaches nothing a caller could not page to anyway:
 * the position is checked against the depth limit here.
 */
@Component
public class EventDiscovery {

    static final int MIN_SEARCH_LENGTH = 3;
    static final int MAX_SEARCH_LENGTH = 100;
    private static final String CURSOR_PREFIX = "discover:";

    private final ReactiveMongoTemplate mongo;
    private final Clock clock;
    private final int maxDepth;

    public EventDiscovery(ReactiveMongoTemplate mongo, Clock clock,
                          @Value("${catalog.discovery.max-depth:1000}") int maxDepth) {
        this.mongo = mongo;
        this.clock = clock;
        this.maxDepth = maxDepth;
    }

    /** One page: the events, where they start in the feed, and whether more follow. */
    public record Page(List<Event> events, int start, boolean hasNext) {
    }

    public Mono<Page> find(EventDiscoveryFilterInput filter, int first, String after) {
        return find(filter, EventDiscoverySort.SOONEST, first, after);
    }

    /**
     * One page of the feed in the order asked for. Every order is served by an index of
     * {@code catalog_events} when no other filter narrows the feed; combined with a filter the
     * database picks the narrower index and sorts the matches, which is bounded by the depth cap.
     */
    public Mono<Page> find(EventDiscoveryFilterInput filter, EventDiscoverySort sort, int first, String after) {
        EventDiscoverySort order = sort == null ? EventDiscoverySort.SOONEST : sort;
        List<FieldViolation> violations = check(filter);
        int start = 0;
        if (after != null && !after.isBlank()) {
            Integer position = position(after);
            if (position == null) {
                violations.add(new FieldViolation("pagination.after", "is not a cursor this feed issued"));
            } else {
                start = position;
            }
        }
        if (start + first > maxDepth) {
            violations.add(new FieldViolation("pagination.after",
                    "the feed stops at " + maxDepth + " events; narrow the filter to see further"));
        }
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        Query query = query(filter, order).with(orderOf(order))
                .skip(start)
                .limit(first + 1);
        int from = start;
        return mongo.find(query, Event.class).collectList()
                .map(found -> new Page(found.size() > first ? found.subList(0, first) : found, from, found.size() > first));
    }

    /** The sort of an order; the id breaks ties so a page boundary never repeats or skips an event. */
    public static Sort orderOf(EventDiscoverySort order) {
        return switch (order == null ? EventDiscoverySort.SOONEST : order) {
            case SOONEST -> Sort.by(Sort.Order.asc("eventDateTime"), Sort.Order.asc("_id"));
            case NEWEST -> Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("_id"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("lowestTicketPrice"), Sort.Order.asc("eventDateTime"), Sort.Order.asc("_id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("lowestTicketPrice"), Sort.Order.asc("eventDateTime"), Sort.Order.asc("_id"));
            case POPULAR -> Sort.by(Sort.Order.desc("soldTickets"), Sort.Order.asc("eventDateTime"), Sort.Order.asc("_id"));
        };
    }

    /** The query for {@code filter} in the default order, without paging. */
    public Query query(EventDiscoveryFilterInput filter) {
        return query(filter, EventDiscoverySort.SOONEST);
    }

    /**
     * The query for {@code filter}, without paging: public events and the chosen narrowing. Ordering
     * by price leaves out an event with no tier on public sale, which has no price to order by and
     * cannot be bought.
     */
    public Query query(EventDiscoveryFilterInput filter, EventDiscoverySort order) {
        Criteria criteria = Criteria.where("status").is(EventStatus.PUBLISHED)
                .and("published").is(true)
                .and("isActive").is(true)
                .and("isDeleted").ne(true)
                .and("endDateTime").gt(clock.instant());
        if (filter.categoryId() != null) {
            criteria.and("categoryId").is(filter.categoryId());
        }
        if (filter.cityId() != null) {
            criteria.and("cityId").is(filter.cityId());
        }
        if (filter.startDate() != null || filter.endDate() != null) {
            Criteria starts = criteria.and("eventDateTime");
            if (filter.startDate() != null) {
                starts.gte(filter.startDate());
            }
            if (filter.endDate() != null) {
                starts.lte(filter.endDate());
            }
        }
        if (order == EventDiscoverySort.PRICE_ASC || order == EventDiscoverySort.PRICE_DESC) {
            // The same field can carry a range below; $ne null and the range are one condition.
            if (filter.minPrice() == null && filter.maxPrice() == null) {
                criteria.and("lowestTicketPrice").ne(null);
            }
        }
        if (filter.minPrice() != null || filter.maxPrice() != null) {
            Criteria price = criteria.and("lowestTicketPrice");
            if (filter.minPrice() != null) {
                price.gte(filter.minPrice());
            }
            if (filter.maxPrice() != null) {
                price.lte(filter.maxPrice());
            }
        }
        Query query = new Query(criteria);
        if (filter.searchQuery() != null && !filter.searchQuery().isBlank()) {
            // Any of the words, ranked by the text index's weights.
            String[] words = EventAdminFilter.words(filter.searchQuery());
            if (words.length > 0) {
                query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(words));
            }
        }
        return query;
    }

    /** Every reason the filter cannot be run. */
    static List<FieldViolation> check(EventDiscoveryFilterInput filter) {
        List<FieldViolation> violations = new ArrayList<>();
        Map<String, Object> unsupported = new LinkedHashMap<>();
        unsupported.put("categoryIds", filter.categoryIds());
        unsupported.put("cityName", filter.cityName());
        unsupported.put("country", filter.country());
        unsupported.put("provinceId", filter.provinceId());
        unsupported.put("isFreeEvent", filter.isFreeEvent());
        unsupported.put("hasAvailableTickets", filter.hasAvailableTickets());
        unsupported.put("isAccessible", filter.isAccessible());
        unsupported.put("isVirtual", filter.isVirtual());
        unsupported.put("isFeatured", filter.isFeatured());
        unsupported.put("organizerId", filter.organizerId());
        unsupported.forEach((field, value) -> {
            if (value != null) {
                violations.add(new FieldViolation("filter." + field,
                        "is not a discovery filter; use categoryId, cityId, startDate/endDate, minPrice/maxPrice or searchQuery"));
            }
        });
        String search = filter.searchQuery() == null ? null : filter.searchQuery().trim();
        if (search != null && !search.isEmpty() && (search.length() < MIN_SEARCH_LENGTH || search.length() > MAX_SEARCH_LENGTH)) {
            violations.add(new FieldViolation("filter.searchQuery",
                    "must be " + MIN_SEARCH_LENGTH + " to " + MAX_SEARCH_LENGTH + " characters"));
        }
        if (filter.minPrice() != null && filter.minPrice().signum() < 0) {
            violations.add(new FieldViolation("filter.minPrice", "cannot be negative"));
        }
        if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            violations.add(new FieldViolation("filter.maxPrice", "must not be below minPrice"));
        }
        if (filter.startDate() != null && filter.endDate() != null && filter.startDate().isAfter(filter.endDate())) {
            violations.add(new FieldViolation("filter.endDate", "must not be before startDate"));
        }
        return violations;
    }

    /** The cursor that resumes the feed after {@code position} events. */
    public static String cursor(int position) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((CURSOR_PREFIX + position).getBytes(StandardCharsets.UTF_8));
    }

    private static Integer position(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(CURSOR_PREFIX)) {
                return null;
            }
            int position = Integer.parseInt(decoded.substring(CURSOR_PREFIX.length()));
            return position < 0 ? null : position;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
