package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.JournalEntry;

import java.util.List;
import java.util.Map;

/**
 * Standard mutation response for Journal Entry operations.
 *
 * <p>Provides consistent response structure for all Journal Entry mutations:
 * create, post, and reverse operations.</p>
 *
 * @since 1.0.0
 */
public record JournalEntryMutationResponse(
    boolean success,
    String message,
    JournalEntry data,
    List<String> errors,
    Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     *
     * @param message Success message
     * @param entry The created/updated Journal Entry
     * @return Success response
     */
    public static JournalEntryMutationResponse success(String message, JournalEntry entry) {
        return new JournalEntryMutationResponse(true, message, entry, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     *
     * @param message Error message
     * @return Error response
     */
    public static JournalEntryMutationResponse error(String message) {
        return new JournalEntryMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     *
     * @param message Summary error message
     * @param errors List of detailed error messages
     * @return Error response
     */
    public static JournalEntryMutationResponse error(String message, List<String> errors) {
        return new JournalEntryMutationResponse(false, message, null, errors, null);
    }
}
