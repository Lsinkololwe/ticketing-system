package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.StandaloneEscrowTransaction;

import java.util.List;

/**
 * Offset-based pagination for Standalone Escrow Transactions.
 *
 * @param data List of escrow transactions for the current page
 * @param paginationInfo Pagination metadata
 *
 * @since 1.0.0
 */
public record EscrowTransactionOffsetPage(
    List<StandaloneEscrowTransaction> data,
    PaginationInfo paginationInfo
) {
    /**
     * Create an empty page.
     */
    public static EscrowTransactionOffsetPage empty() {
        return new EscrowTransactionOffsetPage(
            List.of(),
            new PaginationInfo(0, 0, 1, 0, false, false)
        );
    }
}
