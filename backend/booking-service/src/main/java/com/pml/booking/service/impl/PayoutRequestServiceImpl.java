package com.pml.booking.service.impl;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.service.PayoutRequestService;
import com.pml.shared.constants.PayoutRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

/**
 * Implementation of {@link PayoutRequestService}.
 *
 * <p>The one place payout request reads and writes happen, so a resolver never queries or
 * mutates {@code booking_payout_requests} directly.</p>
 *
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutRequestServiceImpl implements PayoutRequestService {

    private final PayoutRequestRepository payoutRequestRepository;

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    @Override
    public Mono<PayoutRequest> findById(String id) {
        log.debug("Finding payout request by ID: {}", id);
        return payoutRequestRepository.findById(id);
    }

    @Override
    public Mono<PayoutRequest> findByRequestId(String requestId) {
        log.debug("Finding payout request by request ID: {}", requestId);
        return payoutRequestRepository.findByRequestId(requestId);
    }

    @Override
    public Mono<PayoutRequest> findByIdempotencyKey(String idempotencyKey) {
        return payoutRequestRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Flux<PayoutRequest> findByOrganizerId(String organizerId) {
        log.debug("Finding payout requests for organizer: {}", organizerId);
        return payoutRequestRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<PayoutRequest> findByStatus(PayoutRequestStatus status) {
        log.debug("Finding payout requests by status: {}", status);
        return payoutRequestRepository.findByStatus(status);
    }

    @Override
    public Flux<PayoutRequest> findByEventId(String eventId) {
        log.debug("Finding payout requests for event: {}", eventId);
        return payoutRequestRepository.findByEventId(eventId);
    }

    @Override
    public Mono<Boolean> hasOpenPayoutRequest(String eventId) {
        return payoutRequestRepository.findByEventId(eventId)
                .any(request -> !request.getStatus().isFinal());
    }

    @Override
    public Flux<PayoutRequest> findAll() {
        log.debug("Finding all payout requests");
        return payoutRequestRepository.findAll();
    }

    @Override
    public Flux<PayoutRequest> findRetryable(int maxRetries) {
        log.debug("Finding retryable payout requests with max retries: {}", maxRetries);
        return payoutRequestRepository.findByStatusAndRetryCountLessThan(PayoutRequestStatus.FAILED, maxRetries);
    }

    @Override
    public Flux<PayoutRequest> findResolvedAfter(Instant since) {
        log.debug("Finding payout requests resolved after: {}", since);
        return payoutRequestRepository.findByResolvedAtAfter(since);
    }

    @Override
    public Mono<Long> countByIssueType(String issueType) {
        log.debug("Counting payout requests by issue type: {}", issueType);
        return payoutRequestRepository.countByIssueType(issueType);
    }

    // ========================================================================
    // MUTATION METHODS
    // ========================================================================

    @Override
    public Mono<PayoutRequest> save(PayoutRequest payoutRequest) {
        return payoutRequestRepository.save(payoutRequest);
    }

    @Override
    public Flux<PayoutRequest> findByOrganizationIdIn(Collection<String> organizationIds) {
        if (organizationIds.isEmpty()) {
            return Flux.empty();
        }
        return payoutRequestRepository.findByOrganizationIdIn(organizationIds);
    }

    @Override
    public Flux<PayoutRequest> findByOrganizationIdInAndStatus(
            Collection<String> organizationIds, PayoutRequestStatus status) {
        if (organizationIds.isEmpty()) {
            return Flux.empty();
        }
        return payoutRequestRepository.findByOrganizationIdInAndStatus(organizationIds, status);
    }
}