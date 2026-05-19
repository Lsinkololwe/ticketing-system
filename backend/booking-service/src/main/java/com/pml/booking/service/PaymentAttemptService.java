package com.pml.booking.service;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.Ticket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service for managing payment attempt lifecycle.
 *
 * <h2>Payment Attempt Lifecycle</h2>
 * <pre>
 * 1. INITIATE: {@link #initiatePayment} → Creates attempt, calls PawaPay
 * 2. WEBHOOK:  {@link #processWebhook} → Updates status from PawaPay callback
 * 3. POLL:     {@link #pollPendingPayments} → Checks status for missed webhooks
 * 4. FULFILL:  {@link #markFulfilled} → Records successful fulfillment
 * 5. EXPIRE:   {@link #expireTimedOutPayments} → Marks 15-minute timeout
 * </pre>
 *
 * <h2>Idempotency</h2>
 * <p>Payment attempts use depositId (UUID) as the idempotency key for PawaPay.
 * If the same depositId is sent twice, PawaPay returns the same response.</p>
 *
 * <h2>Crash Recovery</h2>
 * <p>The depositId is generated and saved BEFORE calling PawaPay. If the system
 * crashes after calling PawaPay but before receiving the response, the scheduled
 * polling job will discover the payment status and update accordingly.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>Webhook signature verification: {@link #processWebhook}</li>
 *   <li>Server-side verification before fulfillment: {@link #verifyPaymentWithGateway}</li>
 *   <li>Optimistic locking: Prevents TOCTOU race conditions</li>
 * </ul>
 *
 * @see PaymentAttempt
 * @see PaymentAttemptStatus
 * @since 1.0.0
 */
public interface PaymentAttemptService {

    // ========================================================================
    // PAYMENT INITIATION
    // ========================================================================

    /**
     * Initiates a payment attempt for a ticket.
     *
     * <p><b>Flow:</b></p>
     * <ol>
     *   <li>Generate depositId (UUID) for idempotency</li>
     *   <li>Create PaymentAttempt record (status: CREATED)</li>
     *   <li>Save to database (crash recovery point)</li>
     *   <li>Call PawaPay POST /v2/deposits</li>
     *   <li>Update status based on response (PENDING_APPROVAL or REJECTED)</li>
     * </ol>
     *
     * @param ticket The ticket being purchased
     * @param buyerId The ID of the user making the payment
     * @param payerPhone Phone number in E.164 format (+260...)
     * @param provider Mobile money provider code (MTN_MOMO_ZMB, AIRTEL_OAPI_ZMB)
     * @param correlationId Optional correlation ID for tracing
     * @param clientIp Client's IP address
     * @param sessionId Session ID for tracing
     * @return The created payment attempt
     */
    Mono<PaymentAttempt> initiatePayment(
            Ticket ticket,
            String buyerId,
            String payerPhone,
            String provider,
            String correlationId,
            String clientIp,
            String sessionId
    );

    /**
     * Retry a failed payment attempt.
     *
     * <p>Creates a NEW payment attempt with a new depositId, linked to the
     * same ticket. The original failed attempt is preserved for audit.</p>
     *
     * @param originalAttemptId The failed attempt to retry
     * @return New payment attempt
     */
    Mono<PaymentAttempt> retryPayment(String originalAttemptId);

    // ========================================================================
    // WEBHOOK PROCESSING
    // ========================================================================

    /**
     * Processes a webhook callback from PawaPay.
     *
     * <p><b>Security (OWASP):</b></p>
     * <ul>
     *   <li>Validates webhook signature (RFC-9421)</li>
     *   <li>Verifies source IP from allowed ranges</li>
     *   <li>Idempotent: safe to call multiple times for same webhook</li>
     * </ul>
     *
     * @param depositId The depositId from webhook
     * @param providerStatus PawaPay's status (COMPLETED, FAILED)
     * @param providerTransactionId PawaPay's transaction ID (if COMPLETED)
     * @param failureCode Failure code (if FAILED)
     * @param failureMessage Failure message (if FAILED)
     * @param webhookPayload Raw webhook payload for audit
     * @param sourceIp Source IP of webhook request
     * @param signatureValid Whether webhook signature was valid
     * @return Updated payment attempt
     */
    Mono<PaymentAttempt> processWebhook(
            String depositId,
            String providerStatus,
            String providerTransactionId,
            String failureCode,
            String failureMessage,
            String webhookPayload,
            String sourceIp,
            boolean signatureValid
    );

    // ========================================================================
    // STATUS VERIFICATION & POLLING
    // ========================================================================

    /**
     * Verifies payment status directly with PawaPay API.
     *
     * <p><b>Use cases:</b></p>
     * <ul>
     *   <li>OWASP requirement: Verify before fulfillment</li>
     *   <li>Recovery: Check status when webhook was missed</li>
     *   <li>Support: Investigate payment issues</li>
     * </ul>
     *
     * @param depositId The depositId to verify
     * @return Updated payment attempt with verification recorded
     */
    Mono<PaymentAttempt> verifyPaymentWithGateway(String depositId);

    /**
     * Polls pending payments for status updates.
     *
     * <p>Called by scheduled job to handle missed webhooks. Queries payments
     * in PENDING_APPROVAL or PROCESSING status that haven't received a webhook.</p>
     *
     * @return Count of payments that were updated
     */
    Mono<Integer> pollPendingPayments();

    // ========================================================================
    // FULFILLMENT
    // ========================================================================

    /**
     * Records successful fulfillment for a payment.
     *
     * <p><b>Prerequisites:</b></p>
     * <ul>
     *   <li>Payment must be in CONFIRMED status</li>
     *   <li>Payment must be verified with gateway</li>
     *   <li>Journal entry, commission, and escrow transaction must be created</li>
     * </ul>
     *
     * @param depositId The payment to mark fulfilled
     * @param journalEntryId ID of the journal entry created
     * @param commissionId ID of the commission record created
     * @param escrowTransactionId ID of the escrow transaction created
     * @return Updated payment attempt (status: COMPLETED)
     */
    Mono<PaymentAttempt> markFulfilled(
            String depositId,
            String journalEntryId,
            String commissionId,
            String escrowTransactionId
    );

    /**
     * Handles fulfillment failure.
     *
     * <p>If fulfillment fails (e.g., database error), the payment remains
     * in CONFIRMED status. A recovery job will retry fulfillment.</p>
     *
     * @param depositId The payment attempt
     * @param errorMessage The error that occurred
     * @return Updated payment attempt
     */
    Mono<PaymentAttempt> recordFulfillmentFailure(String depositId, String errorMessage);

    // ========================================================================
    // EXPIRATION & CANCELLATION
    // ========================================================================

    /**
     * Expires timed-out payment attempts.
     *
     * <p>Called by scheduled job. Marks payments that have exceeded the
     * 15-minute approval window as EXPIRED.</p>
     *
     * @return Count of payments expired
     */
    Mono<Integer> expireTimedOutPayments();

    /**
     * Cancels a payment attempt.
     *
     * <p>Can only cancel payments in non-final states (CREATED, PENDING_APPROVAL).</p>
     *
     * @param depositId The payment to cancel
     * @param reason Reason for cancellation
     * @return Updated payment attempt
     */
    Mono<PaymentAttempt> cancelPayment(String depositId, String reason);

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Find by database ID.
     *
     * @param id MongoDB document ID
     * @return Payment attempt or empty
     */
    Mono<PaymentAttempt> findById(String id);

    /**
     * Find by PawaPay depositId.
     *
     * @param depositId The UUID sent to PawaPay
     * @return Payment attempt or empty
     */
    Mono<PaymentAttempt> findByDepositId(String depositId);

    /**
     * Find by attempt number.
     *
     * @param attemptNumber Format: PAY-{YYYYMMDD}-{XXXXX}
     * @return Payment attempt or empty
     */
    Mono<PaymentAttempt> findByAttemptNumber(String attemptNumber);

    /**
     * Find all payment attempts for a ticket.
     *
     * @param ticketId The ticket ID
     * @return All payment attempts, newest first
     */
    Flux<PaymentAttempt> findByTicketId(String ticketId);

    /**
     * Find the latest payment attempt for a ticket.
     *
     * @param ticketId The ticket ID
     * @return Most recent attempt
     */
    Mono<PaymentAttempt> findLatestByTicketId(String ticketId);

    /**
     * Find successful payment for a ticket.
     *
     * @param ticketId The ticket ID
     * @return The successful payment (CONFIRMED or COMPLETED)
     */
    Mono<PaymentAttempt> findSuccessfulByTicketId(String ticketId);

    /**
     * Find all payment attempts by a buyer.
     *
     * @param buyerId The buyer's user ID
     * @return Payment attempts, newest first
     */
    Flux<PaymentAttempt> findByBuyerId(String buyerId);

    /**
     * Find all payment attempts for an event.
     *
     * @param eventId The event ID
     * @return Payment attempts for this event
     */
    Flux<PaymentAttempt> findByEventId(String eventId);

    /**
     * Find payments by status.
     *
     * @param status The status to find
     * @return Payment attempts with this status
     */
    Flux<PaymentAttempt> findByStatus(PaymentAttemptStatus status);

    /**
     * Find confirmed but unfulfilled payments (for recovery).
     *
     * @return Payments needing fulfillment
     */
    Flux<PaymentAttempt> findConfirmedUnfulfilled();

    // ========================================================================
    // NOTES & REVIEW
    // ========================================================================

    /**
     * Add an audit note to a payment attempt.
     *
     * @param depositId The payment attempt
     * @param author Note author (SYSTEM, ADMIN, SUPPORT)
     * @param note The note content
     * @return Updated payment attempt
     */
    Mono<PaymentAttempt> addNote(String depositId, String author, String note);

    /**
     * Set review status for investigation.
     *
     * @param depositId The payment attempt
     * @param reviewStatus Review status (PENDING_REVIEW, UNDER_INVESTIGATION, RESOLVED)
     * @param reviewedBy Who is reviewing
     * @param notes Review notes
     * @return Updated payment attempt
     */
    Mono<PaymentAttempt> setReviewStatus(
            String depositId,
            String reviewStatus,
            String reviewedBy,
            String notes
    );

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Count payments by status.
     *
     * @param status The status to count
     * @return Count of payments
     */
    Mono<Long> countByStatus(PaymentAttemptStatus status);

    /**
     * Count payments by event and status.
     *
     * @param eventId The event ID
     * @param status The status to count
     * @return Count of payments
     */
    Mono<Long> countByEventIdAndStatus(String eventId, PaymentAttemptStatus status);

    /**
     * Check if a successful payment exists for a ticket.
     *
     * @param ticketId The ticket ID
     * @return Whether a successful payment exists
     */
    Mono<Boolean> hasSuccessfulPayment(String ticketId);

    // ========================================================================
    // RECONCILIATION SUPPORT
    // ========================================================================

    /**
     * Find completed payments in a date range for reconciliation.
     *
     * @param start Start of date range
     * @param end End of date range
     * @return Completed payments in range
     */
    Flux<PaymentAttempt> findCompletedInDateRange(Instant start, Instant end);

    /**
     * Save a payment attempt.
     *
     * @param paymentAttempt The attempt to save
     * @return Saved payment attempt
     */
    Mono<PaymentAttempt> save(PaymentAttempt paymentAttempt);
}
