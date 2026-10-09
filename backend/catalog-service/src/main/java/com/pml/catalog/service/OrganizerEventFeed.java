package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.catalog.util.KeysetCursor;
import com.pml.catalog.web.graphql.query.OrganizerEventQueryResolver.OrganizerEventFilterInput;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * An organization's own events, newest first, a page at a time with a cursor that stays put when
 * events are added at the front.
 *
 * <p>Scoped to the caller's organizations from the tenant scope, in the query itself, so a team member
 * sees the organization's events and nobody sees another's. Filters are applied by the database, not
 * to a list loaded into memory.
 */
@Service
@RequiredArgsConstructor
public class OrganizerEventFeed {

    private final ReactiveMongoTemplate mongo;

    /** A page of events, and whether another follows or precedes it. */
    public record Page(List<Event> events, boolean hasNext, boolean hasPrevious) {
    }

    public Mono<Page> mine(OrganizerEventFilterInput filter, String after, int limit) {
        return CurrentTenantScope.get().flatMap(scope -> {
            if (scope.organizationIds().isEmpty()) {
                return Mono.just(new Page(List.of(), false, false));
            }
            Criteria criteria = Criteria.where("organizationId").in(scope.organizationIds())
                    .and("isDeleted").ne(true);
            Query query = new Query(criteria);
            if (filter != null) {
                if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                    query.addCriteria(Criteria.where("status").in(filter.statuses()));
                } else if (filter.status() != null) {
                    query.addCriteria(Criteria.where("status").is(filter.status()));
                }
                if (filter.searchQuery() != null && !filter.searchQuery().isBlank()) {
                    String escaped = Pattern.quote(filter.searchQuery().trim());
                    query.addCriteria(new Criteria().orOperator(
                            Criteria.where("title").regex(escaped, "i"),
                            Criteria.where("locationName").regex(escaped, "i")));
                }
                if (filter.eventDateAfter() != null || filter.eventDateBefore() != null) {
                    Criteria when = Criteria.where("eventDateTime");
                    if (filter.eventDateAfter() != null) when.gte(filter.eventDateAfter());
                    if (filter.eventDateBefore() != null) when.lte(filter.eventDateBefore());
                    query.addCriteria(when);
                }
            }
            var position = KeysetCursor.decode(after);
            if (after != null && !after.isBlank() && position.isEmpty()) {
                return Mono.error(new ValidationRefusal(List.of(
                        new FieldViolation("pagination.after", "is not a cursor this list issued"))));
            }
            position.ifPresent(p -> query.addCriteria(new Criteria().orOperator(
                    Criteria.where("createdAt").lt(p.createdAt()),
                    Criteria.where("createdAt").is(p.createdAt()).and("id").lt(p.id()))));
            query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).limit(limit + 1);
            boolean hasPrevious = position.isPresent();
            return mongo.find(query, Event.class).collectList().map(found -> new Page(
                    found.size() > limit ? found.subList(0, limit) : found, found.size() > limit, hasPrevious));
        });
    }
}
