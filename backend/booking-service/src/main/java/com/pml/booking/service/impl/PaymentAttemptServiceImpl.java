package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of {@link PaymentAttemptService}.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Crash Recovery</b>: depositId saved BEFORE calling PawaPay</li>
 *   <li><b>Idempotency</b>: Same depositId always produces same result</li>
 *   <li><b>OWASP Compliance</b>: Verify with gateway before fulfillment</li>
 *   <li><b>Audit Trail</b>: Complete history of all state transitions</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <p>This service coordinates with:</p>
 * <ul>
 *   <li>PawaPay API (via PawaPayClient - to be injected)</li>
 *   <li>Webhook handler (receives callbacks)</li>
 *   <li>Scheduler (polls pending, expires timed-out)</li>
 *   <li>Fulfillment (credits escrow, creates commission, posts journal)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;

    // TODO: Inject PawaPayClient when ready
    // private final PawaPayClient pawaPayClient;

    private static final int MAX_RETRIES = 3;
    private static final int EXPIRATION_MINUTES = 15;
    private static final Set<PaymentAttemptStatus> SUCCESSFUL_STATUSES = Set.of(
            PaymentAttemptStatus.CONFIRMED,
            PaymentAttemptStatus.COMPLETED
    );
    private static final Set<PaymentAttemptStatus> EXPIRABLE_STATUSES = Set.of(
            PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.PENDING_APPROVAL
    );
    private static final Set<PaymentAttemptStatus> POLLABLE_STATUSES = Set.of(
            PaymentAttemptStatus.PENDING_APPROVAL,
            PaymentAttemptStatus.PROCESSING
    );

    // Simple counter for attempt numbers (in production, use a sequence service)
    private final AtomicLong attemptCounter = new AtomicLong(1);

    // ========================================================================
    // PAYMENT INITIATION
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> initiatePayment(
            Ticket ticket,
            String buyerId,
            String payerPhone,
            String provider,
            String correlationId,
            String clientIp,
            String sessionId
    ) {
        log.info("Initiating payment for ticket {} by buyer {}", ticket.getId(), buyerId);

        // Step 1: Generate depositId (UUID) for PawaPay idempotency
        String depositId = UUID.randomUUID().toString();
        String attemptNumber = generateAttemptNumber();
        String effectiveCorrelationId = correlationId != null ? correlationId : generateCorrelationId();

        // Step 2: Create PaymentAttempt record
        PaymentAttempt attempt = PaymentAttempt.create(
                depositId,
                ticket.getId(),
                ticket.getEventId(),
                ticket.getOrganizerId(),
                ticket.getOrganizationId(),
                buyerId,
                ticket.getPrice(),
                ticket.getCurrency() != null ? ticket.getCurrency() : "ZMW",
                provider,
                payerPhone
        );

        attempt.setAttemptNumber(attemptNumber);
        attempt.setCorrelationId(effectiveCorrelationId);
        attempt.setClientReferenceId(ticket.getTicketNumber());
        attempt.setClientIpAddress(clientIp);
        attempt.setSessionId(sessionId);
        attempt.setCustomerMessage("TKT " + ticket.getTicketNumber().substring(Math.max(0, ticket.getTicketNumber().length() - 8)));

        // Step 3: Save to database (CRASH RECOVERY POINT)
        // If system crashes after this, scheduled job will discover and poll PawaPay
        return paymentAttemptRepository.save(attempt)
                .doOnSuccess(saved -> log.info("PaymentAttempt created: {} for ticket {}", saved.getDepositId(), ticket.getId()))
                .flatMap(this::callPawaPayApi)
                .doOnError(e -> log.error("Failed to initiate payment for ticket {}: {}", ticket.getId(), e.getMessage()));
    }

    @Override
    public Mono<PaymentAttempt> retryPayment(String originalAttemptId) {
        log.info("Retrying payment from original attempt: {}", originalAttemptId);

        return findByDepositId(originalAttemptId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Original payment attempt not found: " + originalAttemptId)))
                .flatMap(original -> {
                    if (!original.canRetry()) {
                        return Mono.error(new IllegalStateException(
                                "Payment attempt cannot be retried. Status: " + original.getStatus() +
                                        ", Retry count: " + original.getRetryCount()));
                    }

                    // Create NEW attempt with new depositId (linked via correlationId)
                    String newDepositId = UUID.randomUUID().toString();
                    String attemptNumber = generateAttemptNumber();

                    PaymentAttempt newAttempt = PaymentAttempt.create(
                            newDepositId,
                            original.getTicketId(),
                            original.getEventId(),
                            original.getOrganizerId(),
                            original.getOrganizationId(),
                            original.getBuyerId(),
                            original.getAmount(),
                            original.getCurrency(),
                            original.getProvider(),
                            original.getPayerPhone()
                    );

                    newAttempt.setAttemptNumber(attemptNumber);
                    newAttempt.setCorrelationId(original.getCorrelationId());
                    newAttempt.setClientReferenceId(original.getClientReferenceId());
                    newAttempt.setRetryCount(original.getRetryCount() + 1);
                    newAttempt.addNote("SYSTEM", "Retry of failed attempt: " + original.getDepositId());

                    // Mark original as superseded
                    original.addNote("SYSTEM", "Superseded by retry attempt: " + newDepositId);

                    return paymentAttemptRepository.save(original)
                            .then(paymentAttemptRepository.save(newAttempt))
                            .flatMap(this::callPawaPayApi);
                });
    }

    /**
     * Calls PawaPay API to initiate the deposit.
     * <p>
     * TODO: Replace with actual PawaPayClient call when available.
     * For now, simulates the API call and updates status.
     * </p>
     */
    private Mono<PaymentAttempt> callPawaPayApi(PaymentAttempt attempt) {
        log.info("Calling PawaPay API for depositId: {}", attempt.getDepositId());

        attempt.setApiCalledAt(Instant.now());

        // TODO: Replace with actual PawaPay API call
        // return pawaPayClient.createDeposit(
        //         attempt.getDepositId(),
        //         attempt.getAmount(),
        //         attempt.getCurrency(),
        //         attempt.getProvider(),
        //         attempt.getPayerPhone(),
        //         attempt.getClientReferenceId(),
        //         attempt.getCustomerMessage()
        // )
        // .flatMap(response -> handleApiResponse(attempt, response));

        // Simulated response - ACCEPTED (waiting for customer approval)
        attempt.markApiResponded("ACCEPTED");
        attempt.setApiHttpStatus(202);
        attempt.transitionTo(PaymentAttemptStatus.PENDING_APPROVAL);
        attempt.addNote("SYSTEM", "PawaPay API called - waiting for customer approval");

        return paymentAttemptRepository.save(attempt)
                .doOnSuccess(saved -> log.info("Payment attempt {} updated to PENDING_APPROVAL", saved.getDepositId()));
    }

    // ========================================================================
    // WEBHOOK PROCESSING
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> processWebhook(
            String depositId,
            String providerStatus,
            String providerTransactionId,
            String failureCode,
            String failureMessage,
            String webhookPayload,
            String sourceIp,
            boolean signatureValid
    ) {
        log.info("Processing webhook for depositId: {}, status: {}", depositId, providerStatus);

        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found for depositId: " + depositId)))
                .flatMap(attempt -> {
                    // Record webhook receipt
                    attempt.recordWebhook(webhookPayload, sourceIp, signatureValid);

                    // Security check: Invalid signature
                    if (!signatureValid) {
                        log.warn("Invalid webhook signature for depositId: {}", depositId);
                        attempt.addNote("SECURITY", "Webhook signature validation failed from IP: " + sourceIp);
                        return paymentAttemptRepository.save(attempt);
                    }

                    // Check if already processed (idempotency)
                    if (attempt.isWebhookProcessed()) {
                        log.info("Webhook already processed for depositId: {}", depositId);
                        return Mono.just(attempt);
                    }

                    // Update status based on provider response
                    switch (providerStatus.toUpperCase()) {
                        case "COMPLETED":
                            attempt.markConfirmed(providerTransactionId);
                            attempt.addNote("SYSTEM", "Payment confirmed by PawaPay: " + providerTransactionId);
                            break;

                        case "FAILED":
                            attempt.markFailed(failureCode, failureMessage);
                            attempt.addNote("SYSTEM", "Payment failed: " + failureCode + " - " + failureMessage);
                            break;

                        case "PROCESSING":
                            if (attempt.getStatus() == PaymentAttemptStatus.PENDING_APPROVAL) {
                                attempt.transitionTo(PaymentAttemptStatus.PROCESSING);
                                attempt.setProviderStatus("PROCESSING");
                                attempt.addNote("SYSTEM", "Payment processing by provider");
                            }
                            break;

                        default:
                            log.warn("Unknown provider status: {} for depositId: {}", providerStatus, depositId);
                            attempt.addNote("SYSTEM", "Unknown provider status: " + providerStatus);
                    }

                    attempt.markWebhookProcessed();

                    return paymentAttemptRepository.save(attempt)
                            .doOnSuccess(saved -> log.info("Webhook processed for depositId: {}, new status: {}",
                                    depositId, saved.getStatus()));
                });
    }

    // ========================================================================
    // STATUS VERIFICATION & POLLING
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> verifyPaymentWithGateway(String depositId) {
        log.info("Verifying payment with gateway for depositId: {}", depositId);

        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    // TODO: Call PawaPay GET /v2/deposits/{depositId}
                    // return pawaPayClient.getDeposit(depositId)
                    //     .flatMap(response -> updateFromGatewayResponse(attempt, response));

                    // Record verification attempt
                    attempt.recordPoll("SIMULATED", attempt.getProviderStatus());
                    attempt.addNote("SYSTEM", "Verification with gateway attempted");

                    return paymentAttemptRepository.save(attempt);
                });
    }

    @Override
    public Mono<Integer> pollPendingPayments() {
        log.info("Polling pending payments for status updates");

        Instant pollThreshold = Instant.now().minusSeconds(60); // Don't poll more than once per minute

        return paymentAttemptRepository.findByStatusInAndWebhookProcessedAndLastPolledAtBeforeOrLastPolledAtIsNull(
                        POLLABLE_STATUSES, false, pollThreshold)
                .flatMap(this::pollSinglePayment)
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Polled {} pending payments", count));
    }

    private Mono<PaymentAttempt> pollSinglePayment(PaymentAttempt attempt) {
        log.debug("Polling payment: {}", attempt.getDepositId());

        // TODO: Call PawaPay API and update status
        // return pawaPayClient.getDeposit(attempt.getDepositId())
        //     .flatMap(response -> {
        //         attempt.recordPoll("FOUND", response.getStatus());
        //         // Update status based on response...
        //         return paymentAttemptRepository.save(attempt);
        //     });

        // Simulated poll
        attempt.recordPoll("SIMULATED", attempt.getProviderStatus());
        return paymentAttemptRepository.save(attempt);
    }

    // ========================================================================
    // FULFILLMENT
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> markFulfilled(
            String depositId,
            String journalEntryId,
            String commissionId,
            String escrowTransactionId
    ) {
        log.info("Marking payment {} as fulfilled", depositId);

        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    if (attempt.getStatus() != PaymentAttemptStatus.CONFIRMED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot mark as fulfilled - payment not in CONFIRMED status. Current: " + attempt.getStatus()));
                    }

                    if (!attempt.isVerifiedBeforeFulfillment()) {
                        log.warn("OWASP Warning: Payment {} fulfilled without gateway verification", depositId);
                        attempt.addNote("SECURITY", "WARNING: Fulfilled without gateway verification");
                    }

                    attempt.markFulfilled(journalEntryId, commissionId, escrowTransactionId);
                    attempt.addNote("SYSTEM", String.format(
                            "Fulfillment completed: journal=%s, commission=%s, escrow=%s",
                            journalEntryId, commissionId, escrowTransactionId));

                    return paymentAttemptRepository.save(attempt)
                            .doOnSuccess(saved -> log.info("Payment {} marked as COMPLETED", depositId));
                });
    }

    @Override
    public Mono<PaymentAttempt> recordFulfillmentFailure(String depositId, String errorMessage) {
        log.error("Recording fulfillment failure for payment {}: {}", depositId, errorMessage);

        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    attempt.setLastError(errorMessage);
                    attempt.scheduleRetry(5); // Retry in 5 minutes
                    attempt.addNote("SYSTEM", "Fulfillment failed: " + errorMessage);

                    return paymentAttemptRepository.save(attempt);
                });
    }

    // ========================================================================
    // EXPIRATION & CANCELLATION
    // ========================================================================

    @Override
    public Mono<Integer> expireTimedOutPayments() {
        log.info("Expiring timed-out payment attempts");

        return paymentAttemptRepository.findByStatusInAndExpiresAtBefore(EXPIRABLE_STATUSES, Instant.now())
                .flatMap(attempt -> {
                    attempt.markExpired();
                    attempt.addNote("SYSTEM", "Payment expired - customer did not approve within " + EXPIRATION_MINUTES + " minutes");
                    return paymentAttemptRepository.save(attempt);
                })
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("Expired {} timed-out payments", count));
    }

    @Override
    public Mono<PaymentAttempt> cancelPayment(String depositId, String reason) {
        log.info("Cancelling payment {}: {}", depositId, reason);

        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    if (attempt.getStatus().isFinal()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot cancel payment in final state: " + attempt.getStatus()));
                    }

                    attempt.transitionTo(PaymentAttemptStatus.CANCELLED);
                    attempt.addNote("SYSTEM", "Cancelled: " + reason);

                    return paymentAttemptRepository.save(attempt);
                });
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> findById(String id) {
        return paymentAttemptRepository.findById(id);
    }

    @Override
    public Mono<PaymentAttempt> findByDepositId(String depositId) {
        return paymentAttemptRepository.findByDepositId(depositId);
    }

    @Override
    public Mono<PaymentAttempt> findByAttemptNumber(String attemptNumber) {
        return paymentAttemptRepository.findByAttemptNumber(attemptNumber);
    }

    @Override
    public Flux<PaymentAttempt> findByTicketId(String ticketId) {
        return paymentAttemptRepository.findByTicketId(ticketId);
    }

    @Override
    public Mono<PaymentAttempt> findLatestByTicketId(String ticketId) {
        return paymentAttemptRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    @Override
    public Mono<PaymentAttempt> findSuccessfulByTicketId(String ticketId) {
        return paymentAttemptRepository.findByTicketIdAndStatusIn(ticketId, SUCCESSFUL_STATUSES);
    }

    @Override
    public Flux<PaymentAttempt> findByBuyerId(String buyerId) {
        return paymentAttemptRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    @Override
    public Flux<PaymentAttempt> findByEventId(String eventId) {
        return paymentAttemptRepository.findByEventId(eventId);
    }

    @Override
    public Flux<PaymentAttempt> findByStatus(PaymentAttemptStatus status) {
        return paymentAttemptRepository.findByStatus(status);
    }

    @Override
    public Flux<PaymentAttempt> findConfirmedUnfulfilled() {
        return paymentAttemptRepository.findByStatusAndFulfilled(PaymentAttemptStatus.CONFIRMED, false);
    }

    // ========================================================================
    // NOTES & REVIEW
    // ========================================================================

    @Override
    public Mono<PaymentAttempt> addNote(String depositId, String author, String note) {
        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    attempt.addNote(author, note);
                    return paymentAttemptRepository.save(attempt);
                });
    }

    @Override
    public Mono<PaymentAttempt> setReviewStatus(
            String depositId,
            String reviewStatus,
            String reviewedBy,
            String notes
    ) {
        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    attempt.setReviewStatus(reviewStatus);
                    attempt.setReviewedBy(reviewedBy);
                    attempt.setReviewedAt(Instant.now());
                    attempt.setReviewNotes(notes);
                    attempt.addNote(reviewedBy, "Review status set to: " + reviewStatus + " - " + notes);
                    return paymentAttemptRepository.save(attempt);
                });
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    @Override
    public Mono<Long> countByStatus(PaymentAttemptStatus status) {
        return paymentAttemptRepository.countByStatus(status);
    }

    @Override
    public Mono<Long> countByEventIdAndStatus(String eventId, PaymentAttemptStatus status) {
        return paymentAttemptRepository.countByEventIdAndStatus(eventId, status);
    }

    @Override
    public Mono<Boolean> hasSuccessfulPayment(String ticketId) {
        return paymentAttemptRepository.findByTicketIdAndStatusIn(ticketId, SUCCESSFUL_STATUSES)
                .hasElement();
    }

    // ========================================================================
    // RECONCILIATION SUPPORT
    // ========================================================================

    @Override
    public Flux<PaymentAttempt> findCompletedInDateRange(Instant start, Instant end) {
        return paymentAttemptRepository.findByStatusAndCreatedAtBetween(
                PaymentAttemptStatus.COMPLETED, start, end);
    }

    @Override
    public Mono<PaymentAttempt> save(PaymentAttempt paymentAttempt) {
        return paymentAttemptRepository.save(paymentAttempt);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String generateAttemptNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long counter = attemptCounter.getAndIncrement();
        return String.format("PAY-%s-%05d", date, counter);
    }

    private String generateCorrelationId() {
        return "COR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
