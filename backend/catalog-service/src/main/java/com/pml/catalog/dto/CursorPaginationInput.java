package com.pml.catalog.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Cursor-based pagination input matching GraphQL schema.
 * Follows the Relay Cursor Connections Specification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPaginationInput {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Number of items to fetch (forward pagination)
     */
    private Integer first;

    /**
     * Cursor to fetch items after (forward pagination)
     */
    private String after;

    /**
     * Number of items to fetch (backward pagination)
     */
    private Integer last;

    /**
     * Cursor to fetch items before (backward pagination)
     */
    private String before;

    /**
     * Get the page size limit
     */
    public int getLimit() {
        if (first != null && first > 0) {
            return Math.min(first, 100); // Cap at 100
        }
        if (last != null && last > 0) {
            return Math.min(last, 100); // Cap at 100
        }
        return DEFAULT_PAGE_SIZE;
    }

    /**
     * Check if this is forward pagination
     */
    public boolean isForward() {
        return first != null || after != null;
    }

    /**
     * Check if this is backward pagination
     */
    public boolean isBackward() {
        return last != null || before != null;
    }

    /**
     * Check if pagination has a cursor
     */
    public boolean hasCursor() {
        return (after != null && !after.isBlank()) || (before != null && !before.isBlank());
    }
}
