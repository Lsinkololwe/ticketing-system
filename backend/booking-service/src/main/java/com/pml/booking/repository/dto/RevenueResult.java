package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * Wrapper DTO for aggregation results that return revenue totals.
 *
 * This wrapper is required because Spring Data MongoDB cannot directly map
 * aggregation results to BigDecimal on Java 21+ due to module encapsulation.
 * The field name must match the projection output in the aggregation pipeline.
 */
public record RevenueResult(BigDecimal totalRevenue) {

    /**
     * Returns the total revenue value, or ZERO if null.
     */
    public BigDecimal getValueOrZero() {
        return totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
    }
}
