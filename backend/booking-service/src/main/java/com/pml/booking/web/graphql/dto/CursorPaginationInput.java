package com.pml.booking.web.graphql.dto;

/**
 * Cursor-based pagination input following Relay specification.
 * Used for infinite scroll and mobile applications.
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
}
