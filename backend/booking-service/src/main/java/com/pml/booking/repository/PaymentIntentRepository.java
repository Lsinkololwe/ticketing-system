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
     * Find by the platform reference sent to the provider as its deposit id.
     */
    Mono<PaymentIntent> findByDepositId(String depositId);

    /**
     * Find the intent paying for a reservation.
     *
     * <p>The purchase workflow reads this: a purchase's durable state is the
     * reservation's status plus this intent's, and a reservation still
     * {@code HELD} whose intent is terminal is precisely the case a crash leaves
     * behind.
     */
    Mono<PaymentIntent> findByReservationId(String reservationId);

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
     * Count successful payments for an event.
     */
    Mono<Long> countByEventIdAndStatus(String eventId, PaymentStatus status);
}
