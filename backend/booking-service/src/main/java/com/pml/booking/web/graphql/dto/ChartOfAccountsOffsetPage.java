package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.ChartOfAccountsEntry;

import java.util.List;

/**
 * Offset-based pagination for Chart of Accounts entries.
 *
 * @param data List of entries for the current page
 * @param paginationInfo Pagination metadata
 *
 * @since 1.0.0
 */
public record ChartOfAccountsOffsetPage(
    List<ChartOfAccountsEntry> data,
    PaginationInfo paginationInfo
) {
    /**
     * Create an empty page.
     */
    public static ChartOfAccountsOffsetPage empty() {
        return new ChartOfAccountsOffsetPage(
            List.of(),
            new PaginationInfo(0, 0, 1, 0, false, false)
        );
    }
}
