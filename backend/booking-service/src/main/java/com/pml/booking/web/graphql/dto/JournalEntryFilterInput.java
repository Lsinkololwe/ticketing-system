package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;

import java.time.LocalDateTime;

/**
 * Filter input for querying journal entries.
 *
 * @param status Filter by entry status (DRAFT, POSTED, REVERSED)
 * @param type Filter by entry type (STANDARD, ADJUSTMENT, REVERSAL)
 * @param correlationId Filter by correlation ID
 * @param accountCode Filter entries affecting a specific account
 * @param startDate Start of date range (inclusive)
 * @param endDate End of date range (inclusive)
 *
 * @since 1.0.0
 */
public record JournalEntryFilterInput(
    JournalEntryStatus status,
    JournalEntryType type,
    String correlationId,
    String accountCode,
    LocalDateTime startDate,
    LocalDateTime endDate
) {
    /**
     * Check if any filters are active.
     */
    public boolean hasFilters() {
        return status != null || type != null || correlationId != null ||
               accountCode != null || startDate != null || endDate != null;
    }
}
