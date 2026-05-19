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
