package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.JournalEntry;

import java.util.List;

/**
 * Offset-based pagination for Journal Entries.
 *
 * @param data List of entries for the current page
 * @param paginationInfo Pagination metadata
 *
 * @since 1.0.0
 */
public record JournalEntryOffsetPage(
    List<JournalEntry> data,
    PaginationInfo paginationInfo
) {
    /**
     * Create an empty page.
     */
    public static JournalEntryOffsetPage empty() {
        return new JournalEntryOffsetPage(
            List.of(),
            new PaginationInfo(0, 0, 1, 0, false, false)
        );
    }
}
