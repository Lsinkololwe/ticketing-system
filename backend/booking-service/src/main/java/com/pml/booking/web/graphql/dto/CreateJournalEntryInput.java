package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.JournalEntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Input for creating a journal entry.
 *
 * <p>Journal entries must be balanced: sum of debits must equal sum of credits.</p>
 *
 * @param correlationId Unique identifier linking related transactions
 * @param entryDate Date of the journal entry
 * @param effectiveDate Date when the entry takes effect (optional, defaults to entryDate)
 * @param description Entry description
 * @param type Entry type (STANDARD, ADJUSTMENT, REVERSAL)
 * @param lines List of journal lines (debits and credits)
 * @param metadata Additional metadata (optional)
 *
 * @since 1.0.0
 */
public record CreateJournalEntryInput(
    String correlationId,
    LocalDateTime entryDate,
    LocalDateTime effectiveDate,
    String description,
    JournalEntryType type,
    List<JournalLineInput> lines,
    Map<String, Object> metadata
) {
    /**
     * Constructor with validation.
     */
    public CreateJournalEntryInput {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Correlation ID is required");
        }
        if (entryDate == null) {
            throw new IllegalArgumentException("Entry date is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Entry type is required");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one journal line is required");
        }
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A journal entry must have at least 2 lines (one debit, one credit)");
        }

        // Validate balance
        BigDecimal totalDebits = lines.stream()
            .filter(line -> line.debit() != null)
            .map(JournalLineInput::debit)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = lines.stream()
            .filter(line -> line.credit() != null)
            .map(JournalLineInput::credit)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException(
                "Journal entry is unbalanced: debits=" + totalDebits + ", credits=" + totalCredits
            );
        }
    }

    /**
     * Get effective date, defaulting to entry date if not specified.
     */
    public LocalDateTime getEffectiveDateOrDefault() {
        return effectiveDate != null ? effectiveDate : entryDate;
    }
}
