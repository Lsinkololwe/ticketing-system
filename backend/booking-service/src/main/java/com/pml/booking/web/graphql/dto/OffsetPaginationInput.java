package com.pml.booking.web.graphql.dto;

/**
 * Offset-based pagination input for admin/dashboard tables.
 * Uses page-based navigation (page 1, 2, 3...).
 */
public record OffsetPaginationInput(
        Integer page,
        Integer pageSize
) {
    public OffsetPaginationInput {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 20;
    }

    /**
     * Calculate the offset for database queries.
     */
    public int getOffset() {
        return (page - 1) * pageSize;
    }

    /**
     * Get the limit for database queries.
     */
    public int getLimit() {
        return pageSize;
    }
}
