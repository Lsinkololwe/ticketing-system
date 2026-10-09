package com.pml.booking.repository;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.shared.constants.RefundRequestStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Refund Request Repository
 */
@Repository
public interface RefundRequestRepository extends ReactiveMongoRepository<RefundRequest, String> {

    Mono<RefundRequest> findByRequestId(String requestId);

    Flux<RefundRequest> findByTicketId(String ticketId);

    Flux<RefundRequest> findByBuyerId(String buyerId);

    Flux<RefundRequest> findByEventId(String eventId);

    Flux<RefundRequest> findByOrganizerId(String organizerId);

    Flux<RefundRequest> findByStatus(RefundRequestStatus status);

    Flux<RefundRequest> findByRequestedAtBetween(Instant start, Instant end);

    Mono<Long> countByEventId(String eventId);

    Mono<Long> countByStatus(RefundRequestStatus status);

    Mono<Boolean> existsByTicketId(String ticketId);

    /**
     * Find refund request by pawaPay refund ID.
     */
    Mono<RefundRequest> findByPawaPayRefundId(String pawaPayRefundId);

    /**
     * Find refunds by event and status.
     */
    Flux<RefundRequest> findByEventIdAndStatus(String eventId, RefundRequestStatus status);
}
