package com.pml.catalog.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * PageInfo for Relay Cursor Connections.
 * Provides pagination metadata for both cursor and offset pagination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo {

    /**
     * Whether more items exist after the last edge
     */
    private boolean hasNextPage;

    /**
     * Whether more items exist before the first edge
     */
    private boolean hasPreviousPage;

    /**
     * Cursor of the first edge in the result set
     */
    private String startCursor;

    /**
     * Cursor of the last edge in the result set
     */
    private String endCursor;

    // ==========================================
    // Offset Pagination Fields
    // ==========================================

    /**
     * Total count of items across all pages
     */
    private Long totalCount;

    /**
     * Alias for totalCount (for schema compatibility)
     */
    private Long totalElements;

    /**
     * Total number of pages
     */
    private Integer totalPages;

    /**
     * Current page number (0-indexed)
     */
    private Integer currentPage;

    /**
     * Number of items per page
     */
    private Integer pageSize;

    /**
     * Alias for hasNextPage (for schema compatibility)
     */
    public boolean isHasNext() {
        return hasNextPage;
    }

    /**
     * Alias for hasPreviousPage (for schema compatibility)
     */
    public boolean isHasPrevious() {
        return hasPreviousPage;
    }

    /**
     * Factory method for cursor pagination
     */
    public static PageInfo forCursor(boolean hasNext, boolean hasPrevious, String start, String end) {
        return PageInfo.builder()
                .hasNextPage(hasNext)
                .hasPreviousPage(hasPrevious)
                .startCursor(start)
                .endCursor(end)
                .build();
    }

    /**
     * Factory method for offset pagination
     */
    public static PageInfo forOffset(int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return PageInfo.builder()
                .hasNextPage(page < totalPages - 1)
                .hasPreviousPage(page > 0)
                .totalCount(total)
                .totalElements(total)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .build();
    }
}
