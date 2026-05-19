package com.pml.booking.service;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.web.graphql.dto.stats.PayoutIssueTypeStats;
import com.pml.booking.web.graphql.dto.stats.PayoutRecoverySummary;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.PayoutReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Service for payout recovery operations.
 *
 * Handles stuck payout requests, review workflows, and bulk recovery operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRecoveryService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * Get payout requests that need review.
     */
    public Flux<PayoutRequest> getPayoutRequestsForReview(String reviewStatus, int page, int size) {
        List<String> statuses = reviewStatus != null
                ? List.of(reviewStatus)
                : List.of(
                    PayoutReviewStatus.PENDING_REVIEW.name(),
                    PayoutReviewStatus.UNDER_REVIEW.name(),
                    PayoutReviewStatus.ESCALATED.name()
                );

        Query query = new Query(Criteria.where("reviewStatus").in(statuses))
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return mongoTemplate.find(query, PayoutRequest.class);
    }

    /**
     * Count payout requests for review.
     */
    public Mono<Long> countPayoutRequestsForReview(String reviewStatus) {
        if (reviewStatus != null) {
            return payoutRequestRepository.countByReviewStatus(reviewStatus);
        }
        return Mono.zip(
                payoutRequestRepository.countByReviewStatus(PayoutReviewStatus.PENDING_REVIEW.name()),
                payoutRequestRepository.countByReviewStatus(PayoutReviewStatus.UNDER_REVIEW.name()),
                payoutRequestRepository.countByReviewStatus(PayoutReviewStatus.ESCALATED.name())
        ).map(tuple -> tuple.getT1() + tuple.getT2() + tuple.getT3());
    }

    /**
     * Get stuck payout requests.
     */
    public Flux<PayoutRequest> getStuckPayoutRequests(int page, int size) {
        Query query = new Query(Criteria.where("isStuck").is(true))
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "stuckAt")));

        return mongoTemplate.find(query, PayoutRequest.class);
    }

    /**
     * Count stuck payout requests.
     */
    public Mono<Long> countStuckPayoutRequests() {
        return payoutRequestRepository.countByIsStuckTrue();
    }

    /**
     * Get payout requests by issue type.
     */
    public Flux<PayoutRequest> getPayoutRequestsByIssueType(String issueType, int page, int size) {
        Query query = new Query(Criteria.where("issueType").is(issueType))
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return mongoTemplate.find(query, PayoutRequest.class);
    }

    /**
     * Resume a stuck payout request.
     */
    public Mono<PayoutRequest> resumePayoutRequest(String payoutRequestId) {
        return payoutRequestRepository.findById(payoutRequestId)
                .switchIfEmpty(payoutRequestRepository.findByRequestId(payoutRequestId))
                .flatMap(payoutRequest -> {
                    if (!payoutRequest.isStuck()) {
                        return Mono.error(new IllegalStateException(
                                "Payout request is not stuck. Current status: " + payoutRequest.getStatus()));
                    }

                    if (!payoutRequest.canResume()) {
                        return Mono.error(new IllegalStateException(
                                "Payout request cannot be resumed. Max retries reached: " + payoutRequest.getRetryCount()));
                    }

                    payoutRequest.resume();
                    return payoutRequestRepository.save(payoutRequest);
                });
    }

    /**
     * Mark a payout request for review.
     */
    public Mono<PayoutRequest> markForReview(String payoutRequestId, String issueType, String notes) {
        return payoutRequestRepository.findById(payoutRequestId)
                .switchIfEmpty(payoutRequestRepository.findByRequestId(payoutRequestId))
                .flatMap(payoutRequest -> {
                    payoutRequest.markForReview(issueType, notes);
                    return payoutRequestRepository.save(payoutRequest);
                });
    }

    /**
     * Resolve a payout issue.
     */
    public Mono<PayoutRequest> resolveIssue(String payoutRequestId, String resolutionType,
                                            String resolvedBy, String notes, String newBankAccountId) {
        return payoutRequestRepository.findById(payoutRequestId)
                .switchIfEmpty(payoutRequestRepository.findByRequestId(payoutRequestId))
                .flatMap(payoutRequest -> {
                    payoutRequest.resolveIssue(resolutionType, resolvedBy, notes);

                    // If bank account was updated, update the reference
                    if (newBankAccountId != null && !newBankAccountId.isBlank()) {
                        payoutRequest.setBankAccountId(newBankAccountId);
                    }

                    return payoutRequestRepository.save(payoutRequest);
                });
    }

    /**
     * Escalate a payout request.
     */
    public Mono<PayoutRequest> escalatePayoutRequest(String payoutRequestId, String reason) {
        return payoutRequestRepository.findById(payoutRequestId)
                .switchIfEmpty(payoutRequestRepository.findByRequestId(payoutRequestId))
                .flatMap(payoutRequest -> {
                    payoutRequest.escalate(reason);
                    return payoutRequestRepository.save(payoutRequest);
                });
    }

    /**
     * Bulk retry failed payout requests.
     */
    public Flux<PayoutRequest> bulkRetryFailedPayouts(List<String> payoutRequestIds) {
        return Flux.fromIterable(payoutRequestIds)
                .flatMap(id -> payoutRequestRepository.findById(id)
                        .switchIfEmpty(payoutRequestRepository.findByRequestId(id)))
                .filter(payout -> payout.canRetry())
                .flatMap(payout -> {
                    payout.markForRetry("Bulk retry initiated");
                    return payoutRequestRepository.save(payout);
                });
    }

    /**
     * Bulk mark payout requests for review.
     */
    public Flux<PayoutRequest> bulkMarkForReview(List<String> payoutRequestIds, String issueType, String notes) {
        return Flux.fromIterable(payoutRequestIds)
                .flatMap(id -> payoutRequestRepository.findById(id)
                        .switchIfEmpty(payoutRequestRepository.findByRequestId(id)))
                .flatMap(payout -> {
                    payout.markForReview(issueType, notes);
                    return payoutRequestRepository.save(payout);
                });
    }

    /**
     * Get payout recovery summary for dashboard.
     */
    public Mono<PayoutRecoverySummary> getRecoverySummary() {
        return Mono.zip(
                countPayoutRequestsForReview(null),
                payoutRequestRepository.countByReviewStatus(PayoutReviewStatus.PENDING_REVIEW.name()),
                payoutRequestRepository.countByReviewStatus(PayoutReviewStatus.UNDER_REVIEW.name()),
                countStuckPayoutRequests(),
                countRetryablePayouts(),
                getIssueTypeStats(),
                countRecentlyResolved(),
                calculateTotalAmountAtRisk()
        ).map(tuple -> PayoutRecoverySummary.builder()
                .totalPayoutsForReview(tuple.getT1().intValue())
                .pendingReviewCount(tuple.getT2().intValue())
                .underReviewCount(tuple.getT3().intValue())
                .stuckPayoutsCount(tuple.getT4().intValue())
                .retryablePayoutsCount(tuple.getT5().intValue())
                .issuesByType(tuple.getT6())
                .recentlyResolvedCount(tuple.getT7().intValue())
                .averageResolutionTimeMinutes(null) // TODO: Calculate from data
                .totalAmountAtRisk(tuple.getT8())
                .build());
    }

    private Mono<Long> countRetryablePayouts() {
        return payoutRequestRepository.countByStatus(PayoutRequestStatus.FAILED)
                .flatMap(count -> {
                    // Filter by retry count < MAX_RETRY_COUNT
                    Query query = new Query(
                            Criteria.where("status").is(PayoutRequestStatus.FAILED.name())
                                    .and("retryCount").lt(MAX_RETRY_COUNT)
                    );
                    return mongoTemplate.count(query, PayoutRequest.class);
                });
    }

    private Mono<Long> countRecentlyResolved() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return payoutRequestRepository.countByResolvedAtAfter(since);
    }

    private Mono<BigDecimal> calculateTotalAmountAtRisk() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("reviewStatus").in(
                        PayoutReviewStatus.PENDING_REVIEW.name(),
                        PayoutReviewStatus.UNDER_REVIEW.name(),
                        PayoutReviewStatus.ESCALATED.name()
                ).orOperator(Criteria.where("isStuck").is(true))),
                group().sum("requestedAmount").as("total"),
                project("total")
        );

        return mongoTemplate.aggregate(aggregation, "payout_requests", AmountResult.class)
                .next()
                .map(result -> result.getTotal() != null ? result.getTotal() : BigDecimal.ZERO)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Mono<List<PayoutIssueTypeStats>> getIssueTypeStats() {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("issueType").ne(null)),
                group("issueType")
                        .count().as("count")
                        .sum("requestedAmount").as("totalAmount"),
                project("count", "totalAmount").and("_id").as("issueType")
        );

        return mongoTemplate.aggregate(aggregation, "payout_requests", IssueTypeCount.class)
                .collectList()
                .flatMap(counts -> {
                    int total = counts.stream().mapToInt(IssueTypeCount::getCount).sum();
                    List<PayoutIssueTypeStats> stats = new ArrayList<>();

                    for (IssueTypeCount count : counts) {
                        double percentage = total > 0 ? (count.getCount() * 100.0 / total) : 0;
                        stats.add(PayoutIssueTypeStats.builder()
                                .issueType(count.getIssueType())
                                .count(count.getCount())
                                .percentage(percentage)
                                .unresolvedCount(0) // Will be updated below
                                .totalAmount(count.getTotalAmount() != null ? count.getTotalAmount() : BigDecimal.ZERO)
                                .build());
                    }

                    // Update unresolved counts
                    return Flux.fromIterable(stats)
                            .flatMap(stat -> payoutRequestRepository
                                    .countByIssueTypeAndResolutionTypeIsNull(stat.getIssueType())
                                    .map(unresolvedCount -> {
                                        stat.setUnresolvedCount(unresolvedCount.intValue());
                                        return stat;
                                    }))
                            .collectList();
                });
    }

    // Helper classes for aggregation results
    @lombok.Data
    private static class IssueTypeCount {
        private String issueType;
        private int count;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    private static class AmountResult {
        private BigDecimal total;
    }
}
