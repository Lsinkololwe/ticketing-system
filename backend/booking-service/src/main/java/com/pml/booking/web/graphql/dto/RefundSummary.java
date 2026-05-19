package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record RefundSummary(
        int totalRefunds,
        BigDecimal totalAmount,
        String currency,
        BigDecimal averageRefundAmount,
        List<RefundStatusSummary> refundsByStatus,
        List<RefundTypeSummary> refundsByType
) {
    /**
     * Factory with automatic average calculation.
     */
    public static RefundSummary create(
            int totalRefunds,
            BigDecimal totalAmount,
            String currency,
            List<RefundStatusSummary> refundsByStatus,
            List<RefundTypeSummary> refundsByType
    ) {
        BigDecimal average = totalRefunds > 0
                ? totalAmount.divide(BigDecimal.valueOf(totalRefunds), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new RefundSummary(totalRefunds, totalAmount, currency, average, refundsByStatus, refundsByType);
    }
}
