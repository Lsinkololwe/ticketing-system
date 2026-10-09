package com.pml.catalog.service;

import com.pml.catalog.domain.enums.RecommendationReason;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.web.graphql.dto.EventRecommendation;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is selling, and what a buyer might like given what they already hold.
 *
 * <p>Both rank by {@code Event.soldTickets}, which booking's inventory commits and refunds keep
 * current, over published events that have not ended — the same events the public feed shows.
 * Nothing here reads a buyer's history: the caller names the events they hold, and only those events'
 * categories are read, so a recommendation can never reveal what another buyer booked.
 */
@Service
@RequiredArgsConstructor
public class EventRanking {

    /** The most events one request may be based on; a buyer with more is asked for the latest. */
    static final int MAX_BASIS = 20;

    /** The most one request may return. */
    static final int MAX_RESULTS = 50;

    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    /** Upcoming published events, most tickets sold first. */
    public Flux<Event> trending(int first) {
        return upcoming(Set.of(), null, bounded(first));
    }

    public Mono<List<EventRecommendation>> recommended(List<String> basedOn, int first) {
        int limit = bounded(first);
        List<String> basis = basedOn == null ? List.of() : basedOn.stream().filter(id -> id != null && !id.isBlank())
                .distinct().toList();
        if (basis.size() > MAX_BASIS) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("basedOnEventIds",
                    "names at most " + MAX_BASIS + " events"))));
        }
        return categoriesOf(basis).flatMap(categoryToEvent -> {
            Mono<List<EventRecommendation>> byCategory = categoryToEvent.isEmpty()
                    ? Mono.just(List.<EventRecommendation>of())
                    : upcoming(new LinkedHashSet<>(basis), categoryToEvent.keySet(), limit)
                    .map(event -> new EventRecommendation(event, RecommendationReason.BECAUSE_YOU_BOOKED,
                            categoryToEvent.get(event.getCategoryId())))
                    .collectList();
            return byCategory.flatMap(first1 -> {
                if (first1.size() >= limit) {
                    return Mono.just(first1);
                }
                Set<String> taken = new LinkedHashSet<>(basis);
                first1.forEach(recommendation -> taken.add(recommendation.event().getId()));
                return upcoming(taken, null, limit - first1.size())
                        .map(event -> new EventRecommendation(event, RecommendationReason.TRENDING, null))
                        .collectList()
                        .map(rest -> {
                            List<EventRecommendation> all = new ArrayList<>(first1);
                            all.addAll(rest);
                            return all;
                        });
            });
        });
    }

    /** Each basis event's category, mapped to the first basis event that has it. Only published events count. */
    private Mono<Map<String, String>> categoriesOf(List<String> basis) {
        if (basis.isEmpty()) {
            return Mono.just(Map.of());
        }
        return mongo.find(Query.query(Criteria.where("id").in(basis).and("published").is(true)), Event.class)
                .collectList()
                .map(events -> {
                    Map<String, String> first = new LinkedHashMap<>();
                    // Keep the caller's order: the first event they named is the reason shown.
                    for (String id : basis) {
                        events.stream().filter(event -> id.equals(event.getId()) && event.getCategoryId() != null)
                                .findFirst()
                                .ifPresent(event -> first.putIfAbsent(event.getCategoryId(), event.getId()));
                    }
                    return first;
                });
    }

    private Flux<Event> upcoming(Set<String> exclude, Set<String> categories, int limit) {
        Criteria criteria = Criteria.where("status").is(EventStatus.PUBLISHED)
                .and("published").is(true)
                .and("isActive").is(true)
                .and("isDeleted").ne(true)
                .and("endDateTime").gt(clock.instant());
        if (!exclude.isEmpty()) {
            criteria.and("id").nin(exclude);
        }
        Query query = new Query(criteria);
        if (categories != null) {
            query.addCriteria(Criteria.where("categoryId").in(categories));
        }
        return mongo.find(query.with(Sort.by(Sort.Order.desc("soldTickets"), Sort.Order.asc("eventDateTime"),
                Sort.Order.asc("_id"))).limit(limit), Event.class);
    }

    private static int bounded(int first) {
        if (first < 1 || first > MAX_RESULTS) {
            throw new ValidationRefusal(List.of(new FieldViolation("first", "must be between 1 and " + MAX_RESULTS)));
        }
        return first;
    }
}
