package com.pml.booking.repository;

import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository for PaymentIntent entities.
 *
 * Provides reactive access to payment intent data stored in MongoDB.
 * Supports pawaPay mobile money payment lifecycle management.
 */
@Repository
public interface PaymentIntentRepository extends ReactiveMongoRepository<PaymentIntent, String> {

    /**
     * Find by idempotency key to prevent duplicate payments.
     */
    Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find by our internal transaction reference.
     */
    Mono<PaymentIntent> findByTransactionRef(String transactionRef);

    /**
     * Find by pawaPay's transaction ID.
     */
    Mono<PaymentIntent> findByProviderTransactionId(String providerTransactionId);

    /**
     * Find by ticket ID.
     */
    Mono<PaymentIntent> findByTicketId(String ticketId);

    /**
     * Find all payment intents for an event.
     */
    Flux<PaymentIntent> findByEventId(String eventId);

    /**
     * Find all payment intents for a user.
     */
    Flux<PaymentIntent> findByUserId(String userId);

    /**
     * Find by status.
     */
    Flux<PaymentIntent> findByStatus(PaymentStatus status);

    /**
     * Find processing payments that have expired (for cleanup job).
     */
    Flux<PaymentIntent> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant expiresBefore);

    /**
     * Find pending payments older than threshold (for timeout processing).
     */
    Flux<PaymentIntent> findByStatusInAndExpiresAtBefore(Iterable<PaymentStatus> statuses, Instant expiresBefore);

    /**
     * Count successful payments for an event.
     */
    Mono<Long> countByEventIdAndStatus(String eventId, PaymentStatus status);

    /**
     * Count payments by user and status.
     */
    Mono<Long> countByUserIdAndStatus(String userId, PaymentStatus status);

    /**
     * Check if idempotency key exists.
     */
    Mono<Boolean> existsByIdempotencyKey(String idempotencyKey);
}
