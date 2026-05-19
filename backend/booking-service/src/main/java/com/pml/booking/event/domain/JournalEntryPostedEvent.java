package com.pml.booking.event.domain;

import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain Event: Journal Entry Posted
 *
 * <p>Published when a journal entry is successfully posted to the ledger.
 * Posted entries affect account balances and cannot be modified.</p>
 *
 * <h2>Triggered By</h2>
 * <ul>
 *   <li>JournalService.postEntry()</li>
 *   <li>AccountingService.record*() methods (auto-post)</li>
 * </ul>
 *
 * <h2>Potential Listeners</h2>
 * <ul>
 *   <li>Balance recalculation services</li>
 *   <li>Financial reporting aggregators</li>
 *   <li>Audit logging systems</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Value
@Builder
public class JournalEntryPostedEvent {

    /**
     * Unique identifier of the journal entry.
     */
    String journalEntryId;

    /**
     * Human-readable entry number (e.g., "JE-2024-01-00001").
     */
    String entryNumber;

    /**
     * Correlation ID linking related transactions.
     */
    String correlationId;

    /**
     * Type of journal entry.
     */
    JournalEntryType type;

    /**
     * Description of the entry.
     */
    String description;

    /**
     * Date when the entry was posted.
     */
    Instant postedAt;

    /**
     * User who posted the entry.
     */
    String postedBy;

    /**
     * Total amount of debits (equals credits for balanced entries).
     */
    BigDecimal totalAmount;

    /**
     * Account codes affected by this entry.
     */
    List<String> affectedAccountCodes;

    /**
     * Timestamp when the event was created.
     */
    Instant eventTimestamp;

    /**
     * Factory method for creating from a posted journal entry domain object.
     *
     * @param entry The posted JournalEntry
     * @return A new JournalEntryPostedEvent
     */
    public static JournalEntryPostedEvent of(JournalEntry entry) {
        List<String> affectedAccountCodes = entry.getLines().stream()
                .map(JournalLine::getAccountCode)
                .distinct()
                .collect(Collectors.toList());

        return JournalEntryPostedEvent.builder()
                .journalEntryId(entry.getId())
                .entryNumber(entry.getEntryNumber())
                .correlationId(entry.getCorrelationId())
                .type(entry.getType())
                .description(entry.getDescription())
                .postedAt(entry.getPostedAt())
                .postedBy(entry.getPostedBy())
                .totalAmount(entry.getTotalDebits())
                .affectedAccountCodes(affectedAccountCodes)
                .eventTimestamp(Instant.now())
                .build();
    }

    /**
     * Factory method for creating from individual parameters.
     */
    public static JournalEntryPostedEvent of(
            String journalEntryId,
            String entryNumber,
            String correlationId,
            JournalEntryType type,
            String description,
            Instant postedAt,
            String postedBy,
            BigDecimal totalAmount,
            List<String> affectedAccountCodes
    ) {
        return JournalEntryPostedEvent.builder()
                .journalEntryId(journalEntryId)
                .entryNumber(entryNumber)
                .correlationId(correlationId)
                .type(type)
                .description(description)
                .postedAt(postedAt)
                .postedBy(postedBy)
                .totalAmount(totalAmount)
                .affectedAccountCodes(affectedAccountCodes)
                .eventTimestamp(Instant.now())
                .build();
    }
}
