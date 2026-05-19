package com.pml.booking.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a refund is successfully processed.
 *
 * Internal Listeners:
 * - EscrowEventListener: Debits escrow account
 * - CommissionEventListener: Cancels or claws back commission based on timing
 *
 * External Listeners (via Azure Service Bus):
 * - Catalog Service: Increments available tickets
 * - Identity Service: Notifies buyer of successful refund
 *
 * Two-Stage Commission Handling:
 * - If refund BEFORE event: Commission status → CANCELLED (no money moved, just cancelled)
 * - If refund AFTER event: Commission status → CLAWED_BACK (money moved back from earned)
 */
@Externalized("payment-events::RefundCompleted")
public record RefundCompletedEvent(
        String refundRequestId,
        String ticketId,
        String ticketNumber,
        String eventId,
        String buyerId,
        String organizerId,
        BigDecimal originalTicketPrice,
        BigDecimal refundAmount,
        BigDecimal processingFee,
        BigDecimal netRefundAmount,
        BigDecimal commissionAmount,
        String commissionAction,
        String refundType,
        String refundReason,
        String payoutReference,
        Instant occurredAt
) {
    public RefundCompletedEvent(
            String refundRequestId,
            String ticketId,
            String ticketNumber,
            String eventId,
            String buyerId,
            String organizerId,
            BigDecimal originalTicketPrice,
            BigDecimal refundAmount,
            BigDecimal processingFee,
            BigDecimal netRefundAmount,
            BigDecimal commissionAmount,
            String commissionAction,
            String refundType,
            String refundReason,
            String payoutReference
    ) {
        this(refundRequestId, ticketId, ticketNumber, eventId, buyerId, organizerId,
                originalTicketPrice, refundAmount, processingFee, netRefundAmount,
                commissionAmount, commissionAction, refundType, refundReason,
                payoutReference, Instant.now());
    }
}
