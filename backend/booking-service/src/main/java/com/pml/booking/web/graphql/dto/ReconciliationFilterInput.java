package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;

import java.time.LocalDateTime;

/**
 * Filter input for querying reconciliation runs.
 *
 * @param type Filter by reconciliation type
 * @param status Filter by run status
 * @param startDate Start of date range (inclusive)
 * @param endDate End of date range (inclusive)
 *
 * @since 1.0.0
 */
public record ReconciliationFilterInput(
    ReconciliationType type,
    ReconciliationStatus status,
    LocalDateTime startDate,
    LocalDateTime endDate
) {
    /**
     * Check if any filters are active.
     */
    public boolean hasFilters() {
        return type != null || status != null || startDate != null || endDate != null;
    }
}
