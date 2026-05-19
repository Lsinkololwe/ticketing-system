package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * Wrapper DTO for payout aggregation results.
 *
 * This wrapper is required because Spring Data MongoDB cannot directly map
 * aggregation results to BigDecimal on Java 21+ due to module encapsulation.
 * The field name must match the projection output in the aggregation pipeline.
 */
public record PayoutTotalResult(BigDecimal total) {

    /**
     * Returns the total value, or ZERO if null.
     */
    public BigDecimal getValueOrZero() {
        return total != null ? total : BigDecimal.ZERO;
    }
}
