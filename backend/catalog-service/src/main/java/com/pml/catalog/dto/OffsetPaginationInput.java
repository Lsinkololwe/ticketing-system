package com.pml.catalog.dto;

/**
 * Offset-based pagination input for admin/dashboard tables.
 * Uses page-based navigation (page 0, 1, 2...).
 * Matches the GraphQL schema OffsetPaginationInput type.
 */
public record OffsetPaginationInput(
        Integer page,
        Integer size,
        String sortBy,
        SortDirection sortDirection
) {
    public OffsetPaginationInput {
        if (page == null) page = 0;
        if (size == null) size = 20;
        if (sortBy == null) sortBy = "createdAt";
        if (sortDirection == null) sortDirection = SortDirection.DESC;
    }

    /**
     * Calculate the offset for database queries.
     */
    public int getOffset() {
        return page * size;
    }

    /**
     * Get the limit for database queries.
     */
    public int getLimit() {
        return size;
    }

    /**
     * Enum for sort direction matching GraphQL schema.
     */
    public enum SortDirection {
        ASC,
        DESC
    }
}
