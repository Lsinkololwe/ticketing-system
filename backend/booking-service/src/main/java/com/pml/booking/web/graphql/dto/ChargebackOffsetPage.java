package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.ChargebackRecord;

import java.util.List;

/**
 * Offset-based pagination for Chargeback Records.
 *
 * @param data List of chargebacks for the current page
 * @param paginationInfo Pagination metadata
 *
 * @since 1.0.0
 */
public record ChargebackOffsetPage(
    List<ChargebackRecord> data,
    PaginationInfo paginationInfo
) {
    /**
     * Create an empty page.
     */
    public static ChargebackOffsetPage empty() {
        return new ChargebackOffsetPage(
            List.of(),
            new PaginationInfo(0, 0, 1, 0, false, false)
        );
    }
}
