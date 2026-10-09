package com.pml.catalog.web.graphql.dto;

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
        // Refuse, do not clamp. 81 paged fields across the three subgraphs take this input,
        // so an unchecked `size: 1000000` would produce a query with `limit(1000000)`, on
        // admin finance tables included.
        //
        // PageSize.require refuses above the ceiling rather than reducing to it: a caller
        // silently handed 100 of the 1000 rows it asked for believes it has read everything,
        // which is worse than an error because it is invisible on both sides.

        size = com.pml.shared.graphql.PageSize.require(size);
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
