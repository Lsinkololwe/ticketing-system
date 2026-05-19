package com.pml.booking.repository;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.dto.PayoutTotalResult;
import com.pml.shared.constants.PayoutRequestStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB Repository for PayoutRequest entity.
 */
@Repository
public interface PayoutRequestRepository extends ReactiveMongoRepository<PayoutRequest, String> {

    Mono<PayoutRequest> findByRequestId(String requestId);

    Flux<PayoutRequest> findByOrganizerId(String organizerId);

    Flux<PayoutRequest> findByEventId(String eventId);

    Flux<PayoutRequest> findByStatus(PayoutRequestStatus status);

    Flux<PayoutRequest> findByOrganizerIdAndStatus(String organizerId, PayoutRequestStatus status);

    Flux<PayoutRequest> findByEventIdAndStatus(String eventId, PayoutRequestStatus status);

    Mono<Long> countByStatus(PayoutRequestStatus status);

    Mono<Long> countByOrganizerId(String organizerId);

    /**
     * Calculate total completed payouts across all organizers.
     *
     * NOTE: Returns wrapper DTO (PayoutTotalResult) instead of raw BigDecimal
     * to avoid Java 21+ module encapsulation issues with BigDecimal reflection.
     */
    @Aggregation(pipeline = {
        "{ $match: { status: 'COMPLETED' } }",
        "{ $group: { _id: null, total: { $sum: '$netPayoutAmount' } } }"
    })
    Mono<PayoutTotalResult> calculateTotalCompletedPayouts();

    /**
     * Calculate total completed payouts for a specific organizer.
     *
     * NOTE: Returns wrapper DTO (PayoutTotalResult) instead of raw BigDecimal
     * to avoid Java 21+ module encapsulation issues with BigDecimal reflection.
     */
    @Aggregation(pipeline = {
        "{ $match: { organizerId: ?0, status: 'COMPLETED' } }",
        "{ $group: { _id: null, total: { $sum: '$netPayoutAmount' } } }"
    })
    Mono<PayoutTotalResult> calculateTotalCompletedPayoutsByOrganizer(String organizerId);

    // Payout recovery queries
    Flux<PayoutRequest> findByReviewStatus(String reviewStatus);

    Flux<PayoutRequest> findByIsStuckTrue();

    Flux<PayoutRequest> findByIssueType(String issueType);

    Flux<PayoutRequest> findByReviewStatusIn(java.util.List<String> reviewStatuses);

    Mono<Long> countByReviewStatus(String reviewStatus);

    Mono<Long> countByIsStuckTrue();

    Mono<Long> countByIssueType(String issueType);

    Mono<Long> countByIssueTypeAndResolutionTypeIsNull(String issueType);

    Flux<PayoutRequest> findByResolvedAtAfter(java.time.LocalDateTime since);

    Mono<Long> countByResolvedAtAfter(java.time.LocalDateTime since);

    Flux<PayoutRequest> findByStatusAndRetryCountLessThan(PayoutRequestStatus status, int maxRetries);
}
