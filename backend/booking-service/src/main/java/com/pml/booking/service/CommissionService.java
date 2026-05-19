package com.pml.booking.service;

import com.pml.booking.domain.model.CommissionRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Commission Service Interface
 *
 * Manages platform commission using the Two-Stage Commission Model:
 *
 * Stage 1 - PENDING:
 *   - Commission recorded at purchase time
 *   - NOT yet earned revenue
 *   - If refunded before event, simply CANCELLED (no money movement)
 *
 * Stage 2 - EARNED:
 *   - After event completes + 7-day hold period
 *   - Commission becomes actual platform revenue
 *   - If refunded after this point, must be CLAWED_BACK (rare)
 */
public interface CommissionService {

    /**
     * Get the current commission rate.
     * Default: 5% (0.05)
     */
    BigDecimal getCommissionRate();

    /**
     * Calculate commission for a ticket price.
     *
     * @param ticketPrice The ticket price
     * @return Commission amount
     */
    BigDecimal calculateCommission(BigDecimal ticketPrice);

    /**
     * Calculate net amount (ticket price minus commission).
     *
     * @param ticketPrice The ticket price
     * @return Net amount for organizer
     */
    BigDecimal calculateNetAmount(BigDecimal ticketPrice);

    /**
     * Create a pending commission record for a ticket sale.
     * Called after successful payment.
     *
     * @param ticketId       The ticket ID
     * @param eventId        The event ID
     * @param organizerId    The organizer ID
     * @param organizationId The organization ID (for multi-tenant tracking)
     * @param ticketPrice    The ticket price
     * @return Created commission record
     */
    Mono<CommissionRecord> createPendingCommission(
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            BigDecimal ticketPrice
    );

    /**
     * Mark commission as earned.
     * Called 7 days after event completion.
     *
     * @param ticketId The ticket ID
     * @return Updated commission record
     */
    Mono<CommissionRecord> markCommissionEarned(String ticketId);

    /**
     * Mark all pending commissions for an event as earned.
     * Called by batch job after hold period.
     *
     * @param eventId The event ID
     * @return Count of commissions marked as earned
     */
    Mono<Long> markEventCommissionsEarned(String eventId);

    /**
     * Cancel pending commission (refund before event).
     * No money movement needed.
     *
     * @param ticketId        The ticket ID
     * @param refundRequestId The refund request ID
     * @param reason          Cancellation reason
     * @return Cancelled commission record
     */
    Mono<CommissionRecord> cancelPendingCommission(
            String ticketId,
            String refundRequestId,
            String reason
    );

    /**
     * Clawback earned commission (rare - refund after event).
     * Actual money movement from earned revenue.
     *
     * @param ticketId        The ticket ID
     * @param refundRequestId The refund request ID
     * @param reason          Clawback reason
     * @return Clawed back commission record
     */
    Mono<CommissionRecord> clawbackEarnedCommission(
            String ticketId,
            String refundRequestId,
            String reason
    );

    /**
     * Find commission record by ticket ID.
     */
    Mono<CommissionRecord> findByTicketId(String ticketId);

    /**
     * Find all commission records for an event.
     */
    Flux<CommissionRecord> findByEventId(String eventId);

    /**
     * Find commission records by event and status.
     */
    Flux<CommissionRecord> findByEventIdAndStatus(
            String eventId,
            CommissionRecord.CommissionStatus status
    );

    /**
     * Find all commission records for an organizer.
     */
    Flux<CommissionRecord> findByOrganizerId(String organizerId);

    /**
     * Get total pending commission for an event.
     */
    Mono<BigDecimal> getTotalPendingCommission(String eventId);

    /**
     * Get total earned commission for an event.
     */
    Mono<BigDecimal> getTotalEarnedCommission(String eventId);

    /**
     * Get total platform earned commission (all events).
     */
    Mono<BigDecimal> getTotalPlatformEarnedCommission();
}
