package com.pml.identity.web.graphql.dto.pagination;

/**
 * Offset-based pagination input for admin/dashboard tables.
 * Uses page-based navigation (page 0, 1, 2...).
 *
 * Schema definition:
 * input OffsetPaginationInput {
 *     page: Int = 0
 *     size: Int = 20
 *     sortBy: String = "createdAt"
 *     sortDirection: SortDirection = DESC
 * }
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
     * Create with defaults.
     */
    public static OffsetPaginationInput defaults() {
        return new OffsetPaginationInput(0, 20, "createdAt", SortDirection.DESC);
    }
}
