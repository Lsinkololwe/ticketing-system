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
     * The first page, with the common defaults.
     *
     * <p>A STATIC FACTORY, not a constructor, and deliberately so. This record
     * previously declared a no-arg and a two-arg constructor alongside its
     * canonical four-arg one. Reflective input mappers — DGS's argument
     * binding, Spring's ConversionService — pick a constructor by arity, and
     * with three to choose from they intermittently chose the two-arg one and
     * then failed with "wrong number of arguments: 4 expected: 2". It surfaced
     * as an INTERNAL_ERROR on a payout query under concurrent load and was
     * invisible when the same query was exercised on its own. Leaving exactly
     * one constructor removes the ambiguity rather than papering over it.
     *
     * <p>Note the page: 0, not 1. The old two-arg default was called as
     * {@code new OffsetPaginationInput(1, 20)} in ten resolvers, and since
     * {@link #getOffset()} is {@code page * size} that skipped the first
     * twenty rows for any client that sent no pagination at all.
     */
    public static OffsetPaginationInput defaults() {
        return new OffsetPaginationInput(0, 20, "createdAt", SortDirection.DESC);
    }

    /** A specific page, with the default sort. */
    public static OffsetPaginationInput of(int page, int size) {
        return new OffsetPaginationInput(page, size, "createdAt", SortDirection.DESC);
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
