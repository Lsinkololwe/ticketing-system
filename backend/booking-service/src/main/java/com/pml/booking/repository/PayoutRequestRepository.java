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

    /**
     * Look up an existing request by its client-supplied idempotency key.
     *
     * <p>The unique sparse index on {@code idempotencyKey} is what actually
     * guarantees one payout per key; this lookup exists so a retry can be
     * answered with the ORIGINAL request instead of a duplicate-key error the
     * caller would have to interpret.
     */
    Mono<PayoutRequest> findByIdempotencyKey(String idempotencyKey);

    Mono<PayoutRequest> findByRequestId(String requestId);

    /**
     * Open requests against one escrow account.
     *
     * <p>ET-FIN-003 R2 allows at most one open request per account. Two open
     * requests against the same balance would both pass their own balance check
     * and between them withdraw the money twice.
     */
    Flux<PayoutRequest> findByEscrowAccountIdAndStatusIn(
            String escrowAccountId, java.util.Collection<PayoutRequestStatus> statuses);

    /**
     * Every payout request belonging to an organization.
     *
     * <p>ET-FIN-003 §4 makes {@code organizationId} the tenant key. Scoping by
     * organizer would hide an organization's payouts from everyone except the
     * person who happened to request them.
     */
    Flux<PayoutRequest> findByOrganizationId(String organizationId);

    /** Payout requests for an organization in a given state. */
    Flux<PayoutRequest> findByOrganizationIdAndStatus(String organizationId, PayoutRequestStatus status);

    /** @deprecated ET-FIN-003 scopes payouts by organization; never use for access decisions. */
    @Deprecated
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
        "{ $group: { _id: null, total: { $sum: '$settledAmount' } } }"
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
        "{ $group: { _id: null, total: { $sum: '$settledAmount' } } }"
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
