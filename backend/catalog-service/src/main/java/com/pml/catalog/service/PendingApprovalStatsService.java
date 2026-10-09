package com.pml.catalog.service;

import com.pml.catalog.persistence.CatalogCollections;

import com.pml.catalog.web.graphql.dto.stats.CatalogPendingCounts;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.count;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

/**
 * Computes the Catalog-owned pending approval-queue count (events awaiting
 * review) for the admin action-center sidebar badge.
 *
 * <p>Per the project rule, the count uses a MongoDB aggregation pipeline via
 * {@link ReactiveMongoTemplate} rather than counting documents in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingApprovalStatsService {

    private static final String EVENTS_COLLECTION = CatalogCollections.EVENTS;

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Events awaiting admin review.
     */
    public Mono<CatalogPendingCounts> getPendingCounts() {
        return getPendingEventReviewsCount()
                .map(eventReviews -> CatalogPendingCounts.builder()
                        .eventReviews(eventReviews)
                        .build());
    }

    /**
     * Count events awaiting admin review (status = PENDING_APPROVAL), excluding
     * soft-deleted events.
     */
    private Mono<Integer> getPendingEventReviewsCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("status").is(EventStatus.PENDING_APPROVAL.name())
                        .and("isDeleted").is(false)),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, EVENTS_COLLECTION, CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    @lombok.Data
    private static class CountResult {
        private int count;
    }
}
