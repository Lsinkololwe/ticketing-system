package com.pml.identity.web.graphql.dto.pagination;

/**
 * Page information for both offset and cursor-based pagination.
 * Supports both Relay specification fields and offset pagination fields.
 *
 * Schema definition:
 * type PageInfo @shareable {
 *     hasNextPage: Boolean @shareable
 *     hasPreviousPage: Boolean @shareable
 *     startCursor: String @shareable
 *     endCursor: String @shareable
 *     totalCount: Int @shareable
 *     totalElements: Int @shareable
 *     totalPages: Int @shareable
 *     currentPage: Int @shareable
 *     pageSize: Int @shareable
 *     hasNext: Boolean @shareable
 *     hasPrevious: Boolean @shareable
 * }
 */
public record PageInfo(
        Boolean hasNextPage,
        Boolean hasPreviousPage,
        String startCursor,
        String endCursor,
        Integer totalCount,
        Integer totalElements,
        Integer totalPages,
        Integer currentPage,
        Integer pageSize,
        Boolean hasNext,
        Boolean hasPrevious
) {
    public static PageInfo empty() {
        return new PageInfo(false, false, null, null, 0, 0, 0, 0, 0, false, false);
    }

    /**
     * Create PageInfo for cursor-based pagination.
     */
    public static PageInfo forCursor(boolean hasNextPage, boolean hasPreviousPage,
                                      String startCursor, String endCursor, int totalCount) {
        return new PageInfo(
                hasNextPage, hasPreviousPage, startCursor, endCursor,
                totalCount, totalCount, null, null, null,
                hasNextPage, hasPreviousPage
        );
    }

    /**
     * Create PageInfo for offset-based pagination.
     */
    public static PageInfo forOffset(int totalElements, int pageSize, int currentPage, int totalPages,
                                      boolean hasNextPage, boolean hasPreviousPage) {
        return new PageInfo(
                hasNextPage, hasPreviousPage, null, null,
                totalElements, totalElements, totalPages, currentPage, pageSize,
                hasNextPage, hasPreviousPage
        );
    }
}
