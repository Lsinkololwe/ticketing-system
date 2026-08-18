package com.pml.identity.service;

import com.pml.shared.constants.DocumentStatus;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.identity.web.graphql.dto.stats.IdentityPendingCounts;
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
 * Computes the Identity-owned pending approval-queue counts for the admin
 * action-center sidebar badges.
 *
 * <p>Per the project rule, all dashboard counts use MongoDB aggregation
 * pipelines via {@link ReactiveMongoTemplate} rather than fetching documents
 * and counting in memory. The two independent counts are executed in parallel
 * with {@link Mono#zip}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingApprovalStatsService {

    private static final String ORGANIZATIONS_COLLECTION = "organizations";
    private static final String VERIFICATION_DOCUMENTS_COLLECTION = "verification_documents";

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Pending organizer applications + pending document verifications.
     */
    public Mono<IdentityPendingCounts> getPendingCounts() {
        return Mono.zip(
                getPendingOrganizerApplicationsCount(),
                getPendingDocumentVerificationsCount()
        ).map(tuple -> IdentityPendingCounts.builder()
                .organizerApplications(tuple.getT1())
                .documentVerifications(tuple.getT2())
                .build());
    }

    /**
     * Count organizations awaiting admin review (status = PENDING_REVIEW).
     */
    private Mono<Integer> getPendingOrganizerApplicationsCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("status").is(OrganizationStatus.PENDING_REVIEW.name())),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, ORGANIZATIONS_COLLECTION, CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Count verification documents awaiting review (status = PENDING).
     */
    private Mono<Integer> getPendingDocumentVerificationsCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("status").is(DocumentStatus.PENDING.name())),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, VERIFICATION_DOCUMENTS_COLLECTION, CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    @lombok.Data
    private static class CountResult {
        private int count;
    }
}
