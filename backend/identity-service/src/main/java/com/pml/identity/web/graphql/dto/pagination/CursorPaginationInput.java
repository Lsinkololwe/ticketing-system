package com.pml.identity.web.graphql.dto.pagination;

/**
 * Cursor-based pagination input following Relay specification.
 * Used for infinite scroll and mobile applications.
 *
 * Schema definition:
 * input CursorPaginationInput {
 *     first: Int
 *     after: String
 *     last: Int
 *     before: String
 * }
 */
public record CursorPaginationInput(
        Integer first,
        String after,
        Integer last,
        String before
) {
    public CursorPaginationInput {
        // Refuse, do not clamp. 81 paged fields across the three subgraphs take this input,
        // so an unchecked size turns `size: 1000000` into a query with `limit(1000000)`, on
        // admin finance tables included.
        //
        // PageSize.require refuses above the ceiling rather than reducing to it: a caller
        // silently handed 100 of the 1000 rows it asked for believes it has read everything,
        // which is worse than an error because it is invisible on both sides.

        if (first != null) first = com.pml.shared.graphql.PageSize.require(first);
        if (last != null) last = com.pml.shared.graphql.PageSize.require(last);
        if (first == null && last == null) {
            first = 20; // Default page size
        }
    }

    public int getLimit() {
        return first != null ? first : (last != null ? last : 20);
    }

    /**
     * Create with defaults.
     */
    public static CursorPaginationInput defaults() {
        return new CursorPaginationInput(20, null, null, null);
    }
}
