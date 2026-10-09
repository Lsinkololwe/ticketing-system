package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * What catalog knows about an organizer for their public profile: how many events they have live
 * and how many they have finished. Both are counts of public facts, so they are the same for every
 * caller.
 */
@Service
@RequiredArgsConstructor
public class OrganizerProfileService {

    private final ReactiveMongoTemplate mongo;
    private final Clock clock;

    /** Published, active, not deleted, and not yet over. */
    public Mono<Integer> publishedEventCount(String organizationId) {
        if (organizationId == null) {
            return Mono.just(0);
        }
        return mongo.count(Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("status").is(EventStatus.PUBLISHED)
                        .and("published").is(true)
                        .and("isActive").is(true)
                        .and("isDeleted").ne(true)
                        .and("endDateTime").gt(clock.instant())), Event.class)
                .map(Long::intValue);
    }

    /** Events that ran their course; a cancelled event is not one of them. */
    public Mono<Integer> completedEventCount(String organizationId) {
        if (organizationId == null) {
            return Mono.just(0);
        }
        return mongo.count(Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("status").is(EventStatus.COMPLETED)
                        .and("isDeleted").ne(true)), Event.class)
                .map(Long::intValue);
    }
}
