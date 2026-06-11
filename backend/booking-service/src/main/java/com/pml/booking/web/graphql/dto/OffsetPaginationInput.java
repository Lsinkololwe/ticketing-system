package com.pml.booking.web.graphql.dto;

/**
 * Offset-based pagination input for admin/dashboard tables.
 * Uses page-based navigation (page 0, 1, 2, 3...).
 */
public record OffsetPaginationInput(
        Integer page,
        Integer size,
        String sortBy,
        SortDirection sortDirection
) {
    public enum SortDirection {
        ASC, DESC
    }

    public OffsetPaginationInput {
        if (page == null) page = 0;
        if (size == null) size = 20;
        if (sortBy == null) sortBy = "createdAt";
        if (sortDirection == null) sortDirection = SortDirection.DESC;
    }

    /**
     * Default constructor with common defaults.
     */
    public OffsetPaginationInput() {
        this(0, 20, "createdAt", SortDirection.DESC);
    }

    /**
     * Convenience constructor with page and size.
     */
    public OffsetPaginationInput(int page, int size) {
        this(page, size, "createdAt", SortDirection.DESC);
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
     * Get page size (alias for size).
     */
    public int getPageSize() {
        return size;
    }
}
