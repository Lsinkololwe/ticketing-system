package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.PaymentAttemptService;
import com.pml.booking.web.graphql.dto.PaymentAttemptMutationResponse;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * GraphQL Mutation Resolver for Payment Attempt operations.
 *
 * <p>Handles the complete lifecycle of payment attempts:</p>
 * <ul>
 *   <li>Initiation - Start payment with PawaPay</li>
 *   <li>Webhook processing - Handle PawaPay callbacks</li>
 *   <li>Verification - Manually verify with gateway</li>
 *   <li>Fulfillment - Mark payment as fulfilled after accounting</li>
 *   <li>Cancellation - Cancel pending payments</li>
 *   <li>Recovery - Expire timed-out, poll pending</li>
 * </ul>
 *
 * <h2>Access Control</h2>
 * <ul>
 *   <li>INTERNAL - Service-to-service operations (initiate, webhook, fulfill)</li>
 *   <li>ADMIN - Manual operations (verify, cancel, add notes, review)</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (author, reviewedBy)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PaymentAttemptMutationResolver {

    private final PaymentAttemptService paymentAttemptService;
    private final TicketRepository ticketRepository;

    // ========================================================================
    // PAYMENT INITIATION
    // ========================================================================

    /**
     * Initiates a payment attempt with PawaPay.
     *
     * <p>Called by the checkout flow to start mobile money payment.
     * Creates a PaymentAttempt record and calls PawaPay API.</p>
     *
     * Schema: initiatePaymentAttempt(input: InitiatePaymentAttemptInput!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_ADMIN')")
    public Mono<PaymentAttemptMutationResponse> initiatePaymentAttempt(
            @InputArgument InitiatePaymentAttemptInput input
    ) {
        log.info("GraphQL mutation: initiatePaymentAttempt(ticketId={}, buyerId={}, provider={})",
                input.ticketId(), input.buyerId(), input.provider());

        return ticketRepository.findById(input.ticketId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + input.ticketId())))
                .flatMap(ticket -> paymentAttemptService.initiatePayment(
                        ticket,
                        input.buyerId(),
                        input.payerPhone(),
                        input.provider(),
                        input.correlationId(),
                        input.clientIp(),
                        input.sessionId()
                ))
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Payment initiated: " + attempt.getDepositId(), attempt))
                .onErrorResume(e -> {
                    log.error("Failed to initiate payment for ticket {}: {}", input.ticketId(), e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Retries a failed payment attempt.
     *
     * <p>Creates a new PaymentAttempt with a new depositId, linked to the
     * same ticket via correlationId.</p>
     *
     * Schema: retryPaymentAttempt(depositId: String!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_ADMIN')")
    public Mono<PaymentAttemptMutationResponse> retryPaymentAttempt(
            @InputArgument String depositId
    ) {
        log.info("GraphQL mutation: retryPaymentAttempt(depositId={})", depositId);

        return paymentAttemptService.retryPayment(depositId)
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Payment retry initiated: " + attempt.getDepositId(), attempt))
                .onErrorResume(e -> {
                    log.error("Failed to retry payment {}: {}", depositId, e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // WEBHOOK PROCESSING
    // ========================================================================

    /**
     * Processes a PawaPay webhook callback.
     *
     * <p>Called by the webhook handler when PawaPay sends a payment status update.</p>
     *
     * Schema: processPaymentWebhook(input: ProcessPaymentWebhookInput!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<PaymentAttemptMutationResponse> processPaymentWebhook(
            @InputArgument ProcessPaymentWebhookInput input
    ) {
        log.info("GraphQL mutation: processPaymentWebhook(depositId={}, status={})",
                input.depositId(), input.providerStatus());

        return paymentAttemptService.processWebhook(
                        input.depositId(),
                        input.providerStatus(),
                        input.providerTransactionId(),
                        input.failureCode(),
                        input.failureMessage(),
                        input.webhookPayload(),
                        input.sourceIp(),
                        input.signatureValid()
                )
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Webhook processed: " + attempt.getStatus(), attempt))
                .onErrorResume(e -> {
                    log.error("Failed to process webhook for {}: {}", input.depositId(), e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // VERIFICATION
    // ========================================================================

    /**
     * Manually verifies payment status with PawaPay API.
     *
     * <p>Used for:</p>
     * <ul>
     *   <li>OWASP compliance: Verify before fulfillment</li>
     *   <li>Recovery: Check status when webhook was missed</li>
     *   <li>Support: Investigate payment issues</li>
     * </ul>
     *
     * Schema: verifyPaymentWithGateway(depositId: String!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttemptMutationResponse> verifyPaymentWithGateway(
            @InputArgument String depositId
    ) {
        log.info("GraphQL mutation: verifyPaymentWithGateway(depositId={})", depositId);

        return paymentAttemptService.verifyPaymentWithGateway(depositId)
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Payment verified: " + attempt.getStatus(), attempt))
                .onErrorResume(e -> {
                    log.error("Failed to verify payment {}: {}", depositId, e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // FULFILLMENT
    // ========================================================================

    /**
     * Marks a payment as fulfilled after accounting operations complete.
     *
     * <p>Called after:</p>
     * <ul>
     *   <li>Journal entry created</li>
     *   <li>Commission recorded</li>
     *   <li>Escrow credited</li>
     * </ul>
     *
     * Schema: markPaymentFulfilled(input: MarkPaymentFulfilledInput!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<PaymentAttemptMutationResponse> markPaymentFulfilled(
            @InputArgument MarkPaymentFulfilledInput input
    ) {
        log.info("GraphQL mutation: markPaymentFulfilled(depositId={})", input.depositId());

        return paymentAttemptService.markFulfilled(
                        input.depositId(),
                        input.journalEntryId(),
                        input.commissionId(),
                        input.escrowTransactionId()
                )
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Payment fulfilled", attempt))
                .onErrorResume(e -> {
                    log.error("Failed to mark payment fulfilled {}: {}", input.depositId(), e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // CANCELLATION
    // ========================================================================

    /**
     * Cancels a pending payment attempt.
     *
     * Schema: cancelPaymentAttempt(depositId: String!, reason: String!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttemptMutationResponse> cancelPaymentAttempt(
            @InputArgument String depositId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: cancelPaymentAttempt(depositId={}, reason={})", depositId, reason);

        return paymentAttemptService.cancelPayment(depositId, reason)
                .map(attempt -> PaymentAttemptMutationResponse.success(
                        "Payment cancelled", attempt))
                .onErrorResume(e -> {
                    log.error("Failed to cancel payment {}: {}", depositId, e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // NOTES & REVIEW
    // ========================================================================

    /**
     * Adds an audit note to a payment attempt.
     * author is extracted from JWT - OWASP A01:2021 compliance
     *
     * Schema: addPaymentAttemptNote(depositId: String!, note: String!): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttemptMutationResponse> addPaymentAttemptNote(
            @InputArgument String depositId,
            @InputArgument String note
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(author -> log.info("GraphQL mutation: addPaymentAttemptNote(depositId={}, author={})", depositId, author))
                .flatMap(author -> paymentAttemptService.addNote(depositId, author, note)
                        .map(attempt -> PaymentAttemptMutationResponse.success(
                                "Note added", attempt)))
                .onErrorResume(e -> {
                    log.error("Failed to add note to payment {}: {}", depositId, e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Sets the review status for investigation.
     * reviewedBy is extracted from JWT - OWASP A01:2021 compliance
     *
     * Schema: setPaymentAttemptReviewStatus(depositId: String!, reviewStatus: String!, notes: String): PaymentAttemptMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttemptMutationResponse> setPaymentAttemptReviewStatus(
            @InputArgument String depositId,
            @InputArgument String reviewStatus,
            @InputArgument String notes
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewedBy -> log.info("GraphQL mutation: setPaymentAttemptReviewStatus(depositId={}, status={}, reviewedBy={})",
                        depositId, reviewStatus, reviewedBy))
                .flatMap(reviewedBy -> paymentAttemptService.setReviewStatus(depositId, reviewStatus, reviewedBy, notes)
                        .map(attempt -> PaymentAttemptMutationResponse.success(
                                "Review status updated", attempt)))
                .onErrorResume(e -> {
                    log.error("Failed to set review status for payment {}: {}", depositId, e.getMessage());
                    return Mono.just(PaymentAttemptMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // SCHEDULED JOB OPERATIONS
    // ========================================================================

    /**
     * Polls pending payments for status updates.
     *
     * <p>Called by scheduled job to handle missed webhooks.</p>
     *
     * Schema: pollPendingPayments: Int!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<Integer> pollPendingPayments() {
        log.info("GraphQL mutation: pollPendingPayments()");
        return paymentAttemptService.pollPendingPayments();
    }

    /**
     * Expires timed-out payment attempts.
     *
     * <p>Called by scheduled job to mark payments that exceeded
     * the 15-minute approval window.</p>
     *
     * Schema: expireTimedOutPayments: Int!
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE')")
    public Mono<Integer> expireTimedOutPayments() {
        log.info("GraphQL mutation: expireTimedOutPayments()");
        return paymentAttemptService.expireTimedOutPayments();
    }

    // ========================================================================
    // INPUT RECORD TYPES
    // ========================================================================

    public record InitiatePaymentAttemptInput(
            String ticketId,
            String eventId,
            String buyerId,
            BigDecimal amount,
            String currency,
            String provider,
            String payerPhone,
            String correlationId,
            String clientIp,
            String sessionId
    ) {}

    public record ProcessPaymentWebhookInput(
            String depositId,
            String providerStatus,
            String providerTransactionId,
            String failureCode,
            String failureMessage,
            String webhookPayload,
            String sourceIp,
            boolean signatureValid
    ) {}

    public record MarkPaymentFulfilledInput(
            String depositId,
            String journalEntryId,
            String commissionId,
            String escrowTransactionId
    ) {}
}
