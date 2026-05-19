package com.pml.booking.service;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Journal Service Interface
 *
 * <p>Manages journal entries - the core of double-entry bookkeeping.
 * Every financial transaction is recorded as a journal entry with
 * balanced debit and credit lines.</p>
 *
 * <h2>Double-Entry Principle</h2>
 * <p>Every journal entry must satisfy: SUM(debits) = SUM(credits)</p>
 *
 * <h2>Journal Entry Lifecycle</h2>
 * <pre>
 * DRAFT → POSTED → (optional) REVERSED
 *   │        │          │
 *   │        │          └── Creates new REVERSAL entry
 *   │        └── Entry becomes immutable
 *   └── Entry can be modified
 * </pre>
 *
 * <h2>Entry Types</h2>
 * <ul>
 *   <li><b>STANDARD</b>: Normal business transaction</li>
 *   <li><b>ADJUSTMENT</b>: Correction or adjustment entry</li>
 *   <li><b>REVERSAL</b>: Reverses a previous entry</li>
 * </ul>
 *
 * <h2>Entry Number Format</h2>
 * <p>JE-YYYY-MM-NNNNN (e.g., JE-2024-03-00001)</p>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>Entries must balance before posting (debits = credits)</li>
 *   <li>Posted entries are immutable</li>
 *   <li>Reversals create a new offsetting entry</li>
 *   <li>All account codes must exist and be active</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.JournalEntry
 * @see com.pml.booking.domain.model.JournalLine
 * @see com.pml.booking.service.ChartOfAccountsService
 * @since 1.0.0
 */
public interface JournalService {

    // ========================================================================
    // ENTRY CREATION
    // ========================================================================

    /**
     * Creates a new journal entry in DRAFT status.
     *
     * <p>The entry is not yet posted to the ledger. Use {@link #postEntry(String, String)}
     * to post after creation.</p>
     *
     * @param correlationId   Business transaction ID (e.g., payment intent ID)
     * @param entryDate       Date of the entry
     * @param effectiveDate   Date the entry takes effect (for reporting)
     * @param description     Human-readable description
     * @param type            Entry type (STANDARD, ADJUSTMENT)
     * @param lines           The journal lines (must balance)
     * @param createdBy       User/system that created the entry
     * @param metadata        Additional metadata (reference IDs, etc.)
     * @return Created journal entry
     */
    Mono<JournalEntry> createEntry(
            String correlationId,
            LocalDateTime entryDate,
            LocalDateTime effectiveDate,
            String description,
            JournalEntryType type,
            List<JournalLine> lines,
            String createdBy,
            java.util.Map<String, String> metadata
    );

    /**
     * Creates and immediately posts a journal entry.
     *
     * <p>Convenience method that combines creation and posting in one operation.
     * Use this for automated entries (e.g., from payment processing).</p>
     *
     * @param correlationId   Business transaction ID
     * @param entryDate       Date of the entry
     * @param description     Human-readable description
     * @param type            Entry type
     * @param lines           The journal lines (must balance)
     * @param createdBy       User/system that created the entry
     * @param metadata        Additional metadata
     * @return Posted journal entry
     */
    Mono<JournalEntry> createAndPostEntry(
            String correlationId,
            LocalDateTime entryDate,
            String description,
            JournalEntryType type,
            List<JournalLine> lines,
            String createdBy,
            java.util.Map<String, String> metadata
    );

    // ========================================================================
    // ENTRY POSTING
    // ========================================================================

    /**
     * Posts a draft journal entry.
     *
     * <p>Posting validates:</p>
     * <ul>
     *   <li>Entry is in DRAFT status</li>
     *   <li>Debits equal credits</li>
     *   <li>All account codes are valid and active</li>
     * </ul>
     *
     * <p>After posting:</p>
     * <ul>
     *   <li>Entry status becomes POSTED</li>
     *   <li>Entry becomes immutable</li>
     *   <li>Account balances are updated</li>
     * </ul>
     *
     * @param entryId  The entry ID to post
     * @param postedBy User/system posting the entry
     * @return Posted journal entry
     */
    Mono<JournalEntry> postEntry(String entryId, String postedBy);

    /**
     * Posts a journal entry by entry number.
     *
     * @param entryNumber The entry number (e.g., JE-2024-03-00001)
     * @param postedBy    User/system posting the entry
     * @return Posted journal entry
     */
    Mono<JournalEntry> postEntryByNumber(String entryNumber, String postedBy);

    // ========================================================================
    // ENTRY REVERSAL
    // ========================================================================

    /**
     * Reverses a posted journal entry.
     *
     * <p>Creates a new REVERSAL entry with all debits and credits swapped.
     * The original entry is marked as REVERSED.</p>
     *
     * <p>Both entries are linked:</p>
     * <ul>
     *   <li>Original: reversedByEntryId points to reversal</li>
     *   <li>Reversal: reversalEntryId points to original</li>
     * </ul>
     *
     * @param entryId    The entry ID to reverse
     * @param reason     Reason for reversal
     * @param reversedBy User/system reversing the entry
     * @return The reversal journal entry (new entry)
     */
    Mono<JournalEntry> reverseEntry(String entryId, String reason, String reversedBy);

    /**
     * Reverses a journal entry by entry number.
     *
     * @param entryNumber The entry number to reverse
     * @param reason      Reason for reversal
     * @param reversedBy  User/system reversing the entry
     * @return The reversal journal entry
     */
    Mono<JournalEntry> reverseEntryByNumber(String entryNumber, String reason, String reversedBy);

    // ========================================================================
    // ENTRY QUERIES
    // ========================================================================

    /**
     * Finds a journal entry by ID.
     *
     * @param id The entry ID
     * @return The journal entry, or empty if not found
     */
    Mono<JournalEntry> findById(String id);

    /**
     * Finds a journal entry by entry number.
     *
     * @param entryNumber The entry number (e.g., JE-2024-03-00001)
     * @return The journal entry, or empty if not found
     */
    Mono<JournalEntry> findByEntryNumber(String entryNumber);

    /**
     * Finds all journal entries.
     *
     * @return All journal entries
     */
    Flux<JournalEntry> findAll();

    /**
     * Finds all journal entries for a business transaction.
     *
     * <p>A correlation ID groups related entries (e.g., original + reversal).</p>
     *
     * @param correlationId The business transaction ID
     * @return All related journal entries
     */
    Flux<JournalEntry> findByCorrelationId(String correlationId);

    /**
     * Finds journal entries within a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate   End date (inclusive)
     * @return All entries in the date range
     */
    Flux<JournalEntry> findByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Finds journal entries by status.
     *
     * @param status The entry status
     * @return All entries with the specified status
     */
    Flux<JournalEntry> findByStatus(JournalEntryStatus status);

    /**
     * Finds journal entries that affect a specific account.
     *
     * @param accountCode The account code
     * @return All entries with lines for the account
     */
    Flux<JournalEntry> findByAccountCode(String accountCode);

    /**
     * Finds journal entries that affect a specific account within a date range.
     *
     * @param accountCode The account code
     * @param startDate   Start date (inclusive)
     * @param endDate     End date (inclusive)
     * @return All matching entries
     */
    Flux<JournalEntry> findByAccountCodeAndDateRange(
            String accountCode,
            LocalDate startDate,
            LocalDate endDate
    );

    // ========================================================================
    // ENTRY NUMBER GENERATION
    // ========================================================================

    /**
     * Generates the next entry number for a given month.
     *
     * <p>Format: JE-YYYY-MM-NNNNN</p>
     * <p>Example: JE-2024-03-00001</p>
     *
     * @param entryDate The entry date (used for year/month)
     * @return Generated entry number
     */
    Mono<String> generateEntryNumber(LocalDateTime entryDate);

    // ========================================================================
    // VALIDATION
    // ========================================================================

    /**
     * Validates a journal entry before posting.
     *
     * <p>Checks:</p>
     * <ul>
     *   <li>Entry is balanced (debits = credits)</li>
     *   <li>All account codes exist and are active</li>
     *   <li>Each line has valid debit XOR credit</li>
     * </ul>
     *
     * @param entry The entry to validate
     * @return Mono completing successfully if valid, error otherwise
     */
    Mono<Void> validateEntry(JournalEntry entry);
}
