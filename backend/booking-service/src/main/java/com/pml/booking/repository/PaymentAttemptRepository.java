package com.pml.booking.repository;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Repository for PaymentAttempt entities.
 *
 * <h2>Key Queries by Use Case</h2>
 * <ul>
 *   <li><b>Idempotency</b>: {@link #findByDepositId(String)} - ensure no duplicate PawaPay calls</li>
 *   <li><b>Webhook Processing</b>: {@link #findByDepositId(String)} - lookup by PawaPay's depositId</li>
 *   <li><b>Status Polling</b>: {@link #findByStatusAndWebhookProcessedFalse(PaymentAttemptStatus)} - find pending payments needing poll</li>
 *   <li><b>Expiration</b>: {@link #findByStatusInAndExpiresAtBefore(Collection, Instant)} - find expired payments</li>
 *   <li><b>Recovery</b>: {@link #findByStatusAndRetryCountLessThan(PaymentAttemptStatus, int)} - find retriable failures</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Repository
public interface PaymentAttemptRepository extends ReactiveMongoRepository<PaymentAttempt, String> {

    // ========================================================================
    // IDEMPOTENCY & LOOKUP
    // ========================================================================

    /**
     * Find by PawaPay depositId (UUID).
     * <p>Primary key for idempotency and webhook correlation.</p>
     *
     * @param depositId The UUID sent to PawaPay
     * @return The payment attempt or empty
     */
    Mono<PaymentAttempt> findByDepositId(String depositId);

    /** The row for the provider call made under {@code providerReference}; unique per row. */
    Mono<PaymentAttempt> findByProviderReference(String providerReference);

    /**
     * Find every attempt made against one payment intent, newest first.
     *
     * @param paymentIntentId the intent's id
     * @return the rows behind {@code paymentAttempts(intentId)}
     */
    Flux<PaymentAttempt> findByPaymentIntentIdOrderByCreatedAtDesc(String paymentIntentId);

    /**
     * Find by human-readable attempt number.
     *
     * @param attemptNumber Format: PAY-{YYYYMMDD}-{XXXXX}
     * @return The payment attempt or empty
     */
    Mono<PaymentAttempt> findByAttemptNumber(String attemptNumber);

    /**
     * Find by correlation ID (for tracing related operations).
     *
     * @param correlationId The correlation ID
     * @return Payment attempts with this correlation ID
     */
    Flux<PaymentAttempt> findByCorrelationId(String correlationId);

    // ========================================================================
    // BUSINESS ENTITY QUERIES
    // ========================================================================

    /**
     * Find all payment attempts for a ticket.
     *
     * @param reservationId The ticket ID
     * @return Payment attempts for this ticket
     */
    Flux<PaymentAttempt> findByReservationId(String reservationId);

    /**
     * Find the latest payment attempt for a ticket.
     *
     * @param reservationId The ticket ID
     * @return Most recent payment attempt
     */
    Mono<PaymentAttempt> findFirstByReservationIdOrderByCreatedAtDesc(String reservationId);

    /**
     * Find successful payment attempt for a ticket.
     *
     * @param reservationId The ticket ID
     * @param statuses Successful statuses (CONFIRMED, COMPLETED)
     * @return The successful payment attempt
     */
    Mono<PaymentAttempt> findByReservationIdAndStatusIn(String reservationId, Collection<PaymentAttemptStatus> statuses);

    /**
     * Find all payment attempts for an event.
     *
     * @param eventId The event ID
     * @return Payment attempts for this event
     */
    Flux<PaymentAttempt> findByEventId(String eventId);

    /**
     * Find all payment attempts by a buyer.
     *
     * @param buyerId The buyer's user ID
     * @return Payment attempts by this buyer, newest first
     */
    Flux<PaymentAttempt> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    /**
     * Find all payment attempts for an organizer's events.
     *
     * @param organizerId The organizer's ID
     * @return Payment attempts for this organizer
     */
    Flux<PaymentAttempt> findByOrganizerId(String organizerId);

    // ========================================================================
    // STATUS-BASED QUERIES
    // ========================================================================

    /**
     * Find by status.
     *
     * @param status The payment attempt status
     * @return Payment attempts with this status
     */
    Flux<PaymentAttempt> findByStatus(PaymentAttemptStatus status);

    /**
     * Count by status.
     *
     * @param status The status to count
     * @return Count of payment attempts with this status
     */
    Mono<Long> countByStatus(PaymentAttemptStatus status);

    /**
     * Count by status for an event.
     *
     * @param eventId The event ID
     * @param status The status to count
     * @return Count of payment attempts
     */
    Mono<Long> countByEventIdAndStatus(String eventId, PaymentAttemptStatus status);

    /**
     * Find payments eligible for retry.
     *
     * @param status The failed status
     * @param maxRetries Maximum retry count
     * @return Retriable payment attempts
     */
    Flux<PaymentAttempt> findByStatusAndRetryCountLessThan(PaymentAttemptStatus status, int maxRetries);

    // ========================================================================
    // FULFILLMENT QUERIES
    // ========================================================================

    /**
     * Find confirmed but not yet fulfilled payments.
     * <p>
     * Use case: Recovery job for payments confirmed but fulfillment failed.
     * </p>
     *
     * @param status CONFIRMED status
     * @param fulfilled false
     * @return Payments needing fulfillment
     */
    Flux<PaymentAttempt> findByStatusAndFulfilled(PaymentAttemptStatus status, boolean fulfilled);

    /**
     * Count by review status.
     *
     * @param reviewStatus The review status
     * @return Count of payment attempts
     */
    Mono<Long> countByReviewStatus(String reviewStatus);

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Count payments by event and status.
     *
     * @param eventId The event ID
     * @param statuses Statuses to count
     * @return Count of matching payments
     */
    Mono<Long> countByEventIdAndStatusIn(String eventId, Collection<PaymentAttemptStatus> statuses);

    /**
     * Count payments by buyer.
     *
     * @param buyerId The buyer ID
     * @return Count of payments by this buyer
     */
    Mono<Long> countByBuyerId(String buyerId);
}
