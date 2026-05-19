package com.pml.booking.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund Calculation DTO
 *
 * Business Intent: Shows customers what they'll receive before confirming a refund.
 * Applies the organizer's refund policy and shows fee breakdown for transparency.
 *
 * Used by mobile checkout flow to preview refund amounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCalculation {

    /** The ticket ID being refunded */
    private String ticketId;

    /** The ticket number */
    private String ticketNumber;

    /** The event ID */
    private String eventId;

    /** The event date (used to calculate days before event) */
    private LocalDateTime eventDate;

    /** The original ticket price */
    private BigDecimal originalAmount;

    /** Days remaining before the event */
    private int daysBeforeEvent;

    /** Refund percentage based on policy (100 = full refund) */
    private float refundPercentage;

    /** Amount the customer will receive */
    private BigDecimal refundAmount;

    /** Commission amount being refunded (cancelled, not clawed back) */
    private BigDecimal commissionRefund;

    /** Amount the platform retains (processing fees) */
    private BigDecimal platformRetains;

    /** Processing fee charged to customer (if applicable) */
    private BigDecimal processingFee;

    /** Whether the ticket is eligible for refund */
    private boolean eligible;

    /** Reason if not eligible */
    private String ineligibilityReason;

    /** Applied refund policy details */
    private String policyDetails;

    /**
     * Factory method for an eligible refund.
     */
    public static RefundCalculation eligible(
            String ticketId,
            String ticketNumber,
            String eventId,
            LocalDateTime eventDate,
            BigDecimal originalAmount,
            int daysBeforeEvent,
            float refundPercentage,
            BigDecimal refundAmount,
            BigDecimal commissionRefund,
            BigDecimal platformRetains,
            BigDecimal processingFee,
            String policyDetails
    ) {
        return RefundCalculation.builder()
                .ticketId(ticketId)
                .ticketNumber(ticketNumber)
                .eventId(eventId)
                .eventDate(eventDate)
                .originalAmount(originalAmount)
                .daysBeforeEvent(daysBeforeEvent)
                .refundPercentage(refundPercentage)
                .refundAmount(refundAmount)
                .commissionRefund(commissionRefund)
                .platformRetains(platformRetains)
                .processingFee(processingFee)
                .eligible(true)
                .policyDetails(policyDetails)
                .build();
    }

    /**
     * Factory method for an ineligible refund.
     */
    public static RefundCalculation ineligible(
            String ticketId,
            String ticketNumber,
            String eventId,
            LocalDateTime eventDate,
            BigDecimal originalAmount,
            String reason
    ) {
        return RefundCalculation.builder()
                .ticketId(ticketId)
                .ticketNumber(ticketNumber)
                .eventId(eventId)
                .eventDate(eventDate)
                .originalAmount(originalAmount)
                .daysBeforeEvent(0)
                .refundPercentage(0f)
                .refundAmount(BigDecimal.ZERO)
                .commissionRefund(BigDecimal.ZERO)
                .platformRetains(BigDecimal.ZERO)
                .processingFee(BigDecimal.ZERO)
                .eligible(false)
                .ineligibilityReason(reason)
                .build();
    }
}
