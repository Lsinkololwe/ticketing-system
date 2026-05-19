package com.pml.booking.web.graphql.dto;

/**
 * Page information for cursor-based pagination following Relay specification.
 */
public record PageInfo(
        Boolean hasNextPage,
        Boolean hasPreviousPage,
        String startCursor,
        String endCursor,
        Integer totalCount
) {
    public static PageInfo empty() {
        return new PageInfo(false, false, null, null, 0);
    }

    public static PageInfo of(boolean hasNextPage, boolean hasPreviousPage,
                              String startCursor, String endCursor, int totalCount) {
        return new PageInfo(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);
    }
}
