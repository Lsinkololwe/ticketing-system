package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * Wrapper DTO for aggregation results that return total spent amounts.
 *
 * This wrapper is required because Spring Data MongoDB cannot directly map
 * aggregation results to BigDecimal on Java 21+ due to module encapsulation.
 * The field name must match the projection output in the aggregation pipeline.
 */
public record SpentResult(BigDecimal totalSpent) {

    /**
     * Returns the total spent value, or ZERO if null.
     */
    public BigDecimal getValueOrZero() {
        return totalSpent != null ? totalSpent : BigDecimal.ZERO;
    }
}
