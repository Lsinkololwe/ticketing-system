package com.pml.booking.repository;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
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

    /**
     * Find by PawaPay's transaction ID (set after COMPLETED).
     *
     * @param providerTransactionId PawaPay's internal transaction ID
     * @return The payment attempt or empty
     */
    Mono<PaymentAttempt> findByProviderTransactionId(String providerTransactionId);

    // ========================================================================
    // BUSINESS ENTITY QUERIES
    // ========================================================================

    /**
     * Find all payment attempts for a ticket.
     *
     * @param ticketId The ticket ID
     * @return Payment attempts for this ticket
     */
    Flux<PaymentAttempt> findByTicketId(String ticketId);

    /**
     * Find the latest payment attempt for a ticket.
     *
     * @param ticketId The ticket ID
     * @return Most recent payment attempt
     */
    Mono<PaymentAttempt> findFirstByTicketIdOrderByCreatedAtDesc(String ticketId);

    /**
     * Find successful payment attempt for a ticket.
     *
     * @param ticketId The ticket ID
     * @param statuses Successful statuses (CONFIRMED, COMPLETED)
     * @return The successful payment attempt
     */
    Mono<PaymentAttempt> findByTicketIdAndStatusIn(String ticketId, Collection<PaymentAttemptStatus> statuses);

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
     * Find by multiple statuses.
     *
     * @param statuses Collection of statuses to match
     * @return Payment attempts with any of these statuses
     */
    Flux<PaymentAttempt> findByStatusIn(Collection<PaymentAttemptStatus> statuses);

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

    // ========================================================================
    // POLLING & RECOVERY QUERIES (Critical for missed webhooks)
    // ========================================================================

    /**
     * Find payments needing polling (webhook may have been missed).
     * <p>
     * Use case: Scheduled job polls PawaPay for payments stuck in
     * PENDING_APPROVAL or PROCESSING without receiving a webhook.
     * </p>
     *
     * @param status The status to check (PENDING_APPROVAL, PROCESSING)
     * @return Payment attempts needing polling
     */
    Flux<PaymentAttempt> findByStatusAndWebhookProcessedFalse(PaymentAttemptStatus status);

    /**
     * Find payments needing polling that haven't been polled recently.
     *
     * @param statuses Statuses to check
     * @param webhookProcessed Whether webhook was processed
     * @param lastPolledBefore Only poll if last poll was before this time
     * @return Payment attempts to poll
     */
    Flux<PaymentAttempt> findByStatusInAndWebhookProcessedAndLastPolledAtBeforeOrLastPolledAtIsNull(
            Collection<PaymentAttemptStatus> statuses,
            boolean webhookProcessed,
            Instant lastPolledBefore
    );

    /**
     * Find payments eligible for retry.
     *
     * @param status The failed status
     * @param maxRetries Maximum retry count
     * @return Retriable payment attempts
     */
    Flux<PaymentAttempt> findByStatusAndRetryCountLessThan(PaymentAttemptStatus status, int maxRetries);

    /**
     * Find payments ready for retry (retry time has passed).
     *
     * @param status The status
     * @param maxRetries Maximum retry count
     * @param now Current time
     * @return Payment attempts ready for retry
     */
    Flux<PaymentAttempt> findByStatusAndRetryCountLessThanAndNextRetryAtBefore(
            PaymentAttemptStatus status, int maxRetries, Instant now);

    // ========================================================================
    // EXPIRATION QUERIES
    // ========================================================================

    /**
     * Find expired payments that haven't been marked as such.
     * <p>
     * Use case: Scheduled job marks expired payments (15-minute timeout).
     * </p>
     *
     * @param statuses Statuses that can expire (CREATED, PENDING_APPROVAL)
     * @param expiresAt Expiration threshold
     * @return Expired payment attempts
     */
    Flux<PaymentAttempt> findByStatusInAndExpiresAtBefore(Collection<PaymentAttemptStatus> statuses, Instant expiresAt);

    /**
     * Find payments created in a time range.
     *
     * @param start Start of range
     * @param end End of range
     * @return Payment attempts in this range
     */
    Flux<PaymentAttempt> findByCreatedAtBetween(Instant start, Instant end);

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
     * Find payments without verification before fulfillment.
     * <p>
     * Use case: Audit query for OWASP compliance check.
     * </p>
     *
     * @param fulfilled Whether fulfilled
     * @param verified Whether verified
     * @return Unverified fulfilled payments (should be empty!)
     */
    Flux<PaymentAttempt> findByFulfilledAndVerifiedBeforeFulfillment(boolean fulfilled, boolean verified);

    // ========================================================================
    // REVIEW & INVESTIGATION QUERIES
    // ========================================================================

    /**
     * Find payments with specific review status.
     *
     * @param reviewStatus The review status
     * @return Payment attempts with this review status
     */
    Flux<PaymentAttempt> findByReviewStatus(String reviewStatus);

    /**
     * Count by review status.
     *
     * @param reviewStatus The review status
     * @return Count of payment attempts
     */
    Mono<Long> countByReviewStatus(String reviewStatus);

    // ========================================================================
    // RECONCILIATION QUERIES
    // ========================================================================

    /**
     * Find completed payments in a date range for reconciliation.
     *
     * @param status COMPLETED status
     * @param start Start of range
     * @param end End of range
     * @return Completed payment attempts
     */
    Flux<PaymentAttempt> findByStatusAndCreatedAtBetween(
            PaymentAttemptStatus status, Instant start, Instant end);

    /**
     * Find by provider status (for matching with gateway settlement).
     *
     * @param providerStatus PawaPay's status (COMPLETED, FAILED, etc.)
     * @return Payment attempts with this provider status
     */
    Flux<PaymentAttempt> findByProviderStatus(String providerStatus);

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

    /**
     * Check if buyer has any successful payment for a ticket.
     *
     * @param ticketId The ticket ID
     * @param buyerId The buyer ID
     * @param statuses Successful statuses
     * @return Whether a successful payment exists
     */
    Mono<Boolean> existsByTicketIdAndBuyerIdAndStatusIn(
            String ticketId, String buyerId, Collection<PaymentAttemptStatus> statuses);
}
