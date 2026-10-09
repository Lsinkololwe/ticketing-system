package com.pml.booking.repository;


import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.dto.PayoutTotalResult;
import com.pml.shared.constants.PayoutRequestStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

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

    /** A provider callback names the payout id stored when settlement began. */
    Mono<PayoutRequest> findByPawaPayPayoutId(String pawaPayPayoutId);

    /**
     * Open requests against one escrow account.
     *
     * <p>At most one open request is allowed per account. Two open
     * requests against the same balance would both pass their own balance check
     * and between them withdraw the money twice.
     */
    Flux<PayoutRequest> findByEscrowAccountIdAndStatusIn(
            String escrowAccountId, java.util.Collection<PayoutRequestStatus> statuses);

    /**
     * Every payout request belonging to an organization.
     *
     * <p>{@code organizationId} is the tenant key. Scoping by
     * organizer would hide an organization's payouts from everyone except the
     * person who happened to request them.
     */
    Flux<PayoutRequest> findByOrganizationId(String organizationId);

    /** Payout requests for an organization in a given state. */
    Flux<PayoutRequest> findByOrganizationIdAndStatus(String organizationId, PayoutRequestStatus status);

    /** @deprecated Payouts are scoped by organization; never use for access decisions. */
    @Deprecated
    Flux<PayoutRequest> findByOrganizerId(String organizerId);

    Flux<PayoutRequest> findByEventId(String eventId);

    Flux<PayoutRequest> findByStatus(PayoutRequestStatus status);

    Flux<PayoutRequest> findByOrganizerIdAndStatus(String organizerId, PayoutRequestStatus status);

    Flux<PayoutRequest> findByEventIdAndStatus(String eventId, PayoutRequestStatus status);

    Mono<Long> countByStatus(PayoutRequestStatus status);

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

    Mono<Long> countByReviewStatus(String reviewStatus);

    Mono<Long> countByIsStuckTrue();

    Mono<Long> countByIssueType(String issueType);

    Mono<Long> countByIssueTypeAndResolutionTypeIsNull(String issueType);

    Flux<PayoutRequest> findByResolvedAtAfter(java.time.Instant since);

    Mono<Long> countByResolvedAtAfter(java.time.Instant since);

    Flux<PayoutRequest> findByStatusAndRetryCountLessThan(PayoutRequestStatus status, int maxRetries);

    /**
     * Payout requests belonging to any of the caller's organizations.
     *
     * <p>The boundary is the {@code IN} clause, not a comparison a caller must remember. A set
     * rather than one id because a user may belong to several organizations, and pinning them to
     * one is how {@code ActorOrganizationResolver} ended up returning {@code organizations.get(0)}.
     *
     * @param organizationIds the caller's active memberships; empty matches nothing
     */
    Flux<PayoutRequest> findByOrganizationIdIn(Collection<String> organizationIds);

    /** As above, narrowed to one status. */
    Flux<PayoutRequest> findByOrganizationIdInAndStatus(Collection<String> organizationIds, PayoutRequestStatus status);

    Mono<PayoutRequest> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);

    Mono<PayoutRequest> findByRequestIdAndOrganizationIdIn(String requestId, Collection<String> organizationIds);
}
