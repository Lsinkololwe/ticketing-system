package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.BookingPendingCounts;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.RefundRequestStatus;
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
 * Computes the Booking-owned pending request-queue counts (payouts and refunds
 * awaiting review) for the admin action-center sidebar badges.
 *
 * <p>Per the project rule, the counts use MongoDB aggregation pipelines via
 * {@link ReactiveMongoTemplate} rather than fetching documents and counting in
 * memory. The two independent counts are executed in parallel with
 * {@link Mono#zip}. "Pending" mirrors the existing pending-queue resolvers,
 * which filter on the {@code PENDING} status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingApprovalStatsService {

    private static final String PAYOUT_REQUESTS_COLLECTION = "booking_payout_requests";
    private static final String REFUND_REQUESTS_COLLECTION = "refund_requests";

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Pending payout requests + pending refund requests.
     */
    public Mono<BookingPendingCounts> getPendingCounts() {
        return Mono.zip(
                getPendingPayoutRequestsCount(),
                getPendingRefundRequestsCount()
        ).map(tuple -> new BookingPendingCounts(tuple.getT1(), tuple.getT2()));
    }

    /**
     * Count payout requests awaiting review (status = PENDING).
     */
    private Mono<Integer> getPendingPayoutRequestsCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("status").is(PayoutRequestStatus.PENDING.name())),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, PAYOUT_REQUESTS_COLLECTION, CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    /**
     * Count refund requests awaiting review (status = PENDING).
     */
    private Mono<Integer> getPendingRefundRequestsCount() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("status").is(RefundRequestStatus.PENDING.name())),
                count().as("count")
        );

        return mongoTemplate.aggregate(aggregation, REFUND_REQUESTS_COLLECTION, CountResult.class)
                .singleOrEmpty()
                .map(CountResult::getCount)
                .defaultIfEmpty(0);
    }

    @lombok.Data
    private static class CountResult {
        private int count;
    }
}
