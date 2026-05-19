package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * GraphQL Query Resolver for Payment Attempt operations.
 *
 * <p>Payment attempts track the complete lifecycle of payment operations
 * through PawaPay mobile money gateway.</p>
 *
 * <h2>Access Control</h2>
 * <p>All queries are restricted to ADMIN role only. Payment attempt
 * details contain sensitive operational and security information.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PaymentAttemptQueryResolver {

    private final PaymentAttemptService paymentAttemptService;

    private static final Set<PaymentAttemptStatus> PENDING_STATUSES = Set.of(
            PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.PENDING_APPROVAL,
            PaymentAttemptStatus.PROCESSING
    );

    // ========================================================================
    // SINGLE PAYMENT ATTEMPT LOOKUP
    // ========================================================================

    /**
     * Get a payment attempt by its database ID.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> paymentAttempt(@InputArgument String id) {
        log.debug("GraphQL query: paymentAttempt(id={})", id);
        return paymentAttemptService.findById(id);
    }

    /**
     * Get a payment attempt by its PawaPay depositId (UUID).
     * This is the primary lookup for webhook processing and idempotency checks.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> paymentAttemptByDepositId(@InputArgument String depositId) {
        log.debug("GraphQL query: paymentAttemptByDepositId(depositId={})", depositId);
        return paymentAttemptService.findByDepositId(depositId);
    }

    /**
     * Get a payment attempt by its human-readable attempt number.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> paymentAttemptByAttemptNumber(@InputArgument String attemptNumber) {
        log.debug("GraphQL query: paymentAttemptByAttemptNumber(attemptNumber={})", attemptNumber);
        return paymentAttemptService.findByAttemptNumber(attemptNumber);
    }

    // ========================================================================
    // PAYMENT ATTEMPTS BY TICKET
    // ========================================================================

    /**
     * Get all payment attempts for a ticket (shows retry history).
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<PaymentAttempt> paymentAttemptsByTicket(@InputArgument String ticketId) {
        log.debug("GraphQL query: paymentAttemptsByTicket(ticketId={})", ticketId);
        return paymentAttemptService.findByTicketId(ticketId);
    }

    /**
     * Get the most recent payment attempt for a ticket.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> latestPaymentAttemptByTicket(@InputArgument String ticketId) {
        log.debug("GraphQL query: latestPaymentAttemptByTicket(ticketId={})", ticketId);
        return paymentAttemptService.findLatestByTicketId(ticketId);
    }

    /**
     * Get the successful payment attempt for a ticket (CONFIRMED or COMPLETED).
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PaymentAttempt> successfulPaymentAttemptByTicket(@InputArgument String ticketId) {
        log.debug("GraphQL query: successfulPaymentAttemptByTicket(ticketId={})", ticketId);
        return paymentAttemptService.findSuccessfulByTicketId(ticketId);
    }

    // ========================================================================
    // PAYMENT ATTEMPTS BY STATUS (Monitoring & Recovery)
    // ========================================================================

    /**
     * Get all payment attempts by status.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<PaymentAttempt> paymentAttemptsByStatus(@InputArgument PaymentAttemptStatus status) {
        log.debug("GraphQL query: paymentAttemptsByStatus(status={})", status);
        return paymentAttemptService.findByStatus(status);
    }

    /**
     * Get all pending payment attempts (CREATED, PENDING_APPROVAL, PROCESSING).
     * Used for monitoring and recovery dashboard.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<PaymentAttempt>> pendingPaymentAttempts() {
        log.debug("GraphQL query: pendingPaymentAttempts()");
        return Flux.fromIterable(PENDING_STATUSES)
                .flatMap(paymentAttemptService::findByStatus)
                .collectList();
    }

    /**
     * Get confirmed but unfulfilled payment attempts.
     * These need fulfillment (escrow, commission, journal).
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<PaymentAttempt> confirmedUnfulfilledPaymentAttempts() {
        log.debug("GraphQL query: confirmedUnfulfilledPaymentAttempts()");
        return paymentAttemptService.findConfirmedUnfulfilled();
    }

    // ========================================================================
    // PAYMENT ATTEMPTS BY BUSINESS ENTITY
    // ========================================================================

    /**
     * Get all payment attempts for an event.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<PaymentAttempt> paymentAttemptsByEvent(@InputArgument String eventId) {
        log.debug("GraphQL query: paymentAttemptsByEvent(eventId={})", eventId);
        return paymentAttemptService.findByEventId(eventId);
    }

    /**
     * Get all payment attempts by a buyer.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<PaymentAttempt> paymentAttemptsByBuyer(@InputArgument String buyerId) {
        log.debug("GraphQL query: paymentAttemptsByBuyer(buyerId={})", buyerId);
        return paymentAttemptService.findByBuyerId(buyerId);
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Count payment attempts by status.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Long> paymentAttemptCountByStatus(@InputArgument PaymentAttemptStatus status) {
        log.debug("GraphQL query: paymentAttemptCountByStatus(status={})", status);
        return paymentAttemptService.countByStatus(status);
    }

    /**
     * Check if a ticket has a successful payment.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Boolean> hasSuccessfulPayment(@InputArgument String ticketId) {
        log.debug("GraphQL query: hasSuccessfulPayment(ticketId={})", ticketId);
        return paymentAttemptService.hasSuccessfulPayment(ticketId);
    }
}
