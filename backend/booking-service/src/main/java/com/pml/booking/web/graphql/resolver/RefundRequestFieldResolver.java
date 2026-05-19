package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.RefundRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Field Resolver for RefundRequest type.
 *
 * Resolves fields that have name mismatches or need computation:
 * - reason: maps to requestReason
 * - refundPercentage: computed from refundAmount/originalTicketPrice
 * - platformRetains: computed from originalTicketPrice - refundAmount
 * - daysBeforeEvent: not stored, needs to be passed via context or set to 0
 * - policyApplied: descriptive string based on refund percentage
 */
@Slf4j
@DgsComponent
public class RefundRequestFieldResolver {

    /**
     * Resolve RefundRequest.reason - maps to requestReason.
     */
    @DgsData(parentType = "RefundRequest", field = "reason")
    public String reason(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        return request.getRequestReason();
    }

    /**
     * Resolve RefundRequest.refundPercentage - computed from amounts.
     * Returns percentage (0-100) of original amount being refunded.
     */
    @DgsData(parentType = "RefundRequest", field = "refundPercentage")
    public Float refundPercentage(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        BigDecimal originalPrice = request.getOriginalTicketPrice();
        BigDecimal refundAmount = request.getRefundAmount();

        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 100f; // Full refund if original price unknown
        }
        if (refundAmount == null) {
            return 0f;
        }

        return refundAmount
                .divide(originalPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .floatValue();
    }

    /**
     * Resolve RefundRequest.platformRetains - computed from originalPrice - refundAmount.
     */
    @DgsData(parentType = "RefundRequest", field = "platformRetains")
    public BigDecimal platformRetains(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        BigDecimal originalPrice = request.getOriginalTicketPrice();
        BigDecimal refundAmount = request.getRefundAmount();

        if (originalPrice == null || refundAmount == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal retained = originalPrice.subtract(refundAmount);
        return retained.max(BigDecimal.ZERO); // Ensure non-negative
    }

    /**
     * Resolve RefundRequest.daysBeforeEvent - not stored on entity.
     * Returns 0 as a default since event date is not stored on RefundRequest.
     * For accurate values, this should be computed when creating the request.
     */
    @DgsData(parentType = "RefundRequest", field = "daysBeforeEvent")
    public Integer daysBeforeEvent(DgsDataFetchingEnvironment dfe) {
        // Event date is not stored on RefundRequest entity
        // Return 0 as default - accurate value should be set during request creation
        return 0;
    }

    /**
     * Resolve RefundRequest.policyApplied - descriptive string based on refund percentage.
     */
    @DgsData(parentType = "RefundRequest", field = "policyApplied")
    public String policyApplied(DgsDataFetchingEnvironment dfe) {
        RefundRequest request = dfe.getSource();
        BigDecimal originalPrice = request.getOriginalTicketPrice();
        BigDecimal refundAmount = request.getRefundAmount();

        if (originalPrice == null || refundAmount == null) {
            return "UNKNOWN";
        }

        if (originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return "FULL_REFUND";
        }

        float percentage = refundAmount
                .divide(originalPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .floatValue();

        if (percentage >= 100) {
            return "FULL_REFUND";
        } else if (percentage >= 75) {
            return "75_PERCENT_REFUND";
        } else if (percentage >= 50) {
            return "50_PERCENT_REFUND";
        } else if (percentage >= 25) {
            return "25_PERCENT_REFUND";
        } else if (percentage > 0) {
            return "PARTIAL_REFUND";
        } else {
            return "NO_REFUND";
        }
    }
}
