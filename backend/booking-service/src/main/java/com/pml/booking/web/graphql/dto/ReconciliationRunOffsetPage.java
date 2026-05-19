package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.ReconciliationRun;

import java.util.List;

/**
 * Offset-based pagination for Reconciliation Runs.
 *
 * @param data List of reconciliation runs for the current page
 * @param paginationInfo Pagination metadata
 *
 * @since 1.0.0
 */
public record ReconciliationRunOffsetPage(
    List<ReconciliationRun> data,
    PaginationInfo paginationInfo
) {
    /**
     * Create an empty page.
     */
    public static ReconciliationRunOffsetPage empty() {
        return new ReconciliationRunOffsetPage(
            List.of(),
            new PaginationInfo(0, 0, 1, 0, false, false)
        );
    }
}
