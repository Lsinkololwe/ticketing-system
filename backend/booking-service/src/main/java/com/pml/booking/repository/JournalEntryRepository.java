package com.pml.booking.repository;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.model.JournalEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * Reactive Repository for Journal Entries
 *
 * Provides reactive access to the journal_entries collection in MongoDB.
 * This repository is central to all double-entry bookkeeping operations.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Entry Number Lookup</b>: Find entries by business identifier</li>
 *   <li><b>Correlation Tracking</b>: Find all entries for a business transaction</li>
 *   <li><b>Account Queries</b>: Find entries affecting specific accounts</li>
 *   <li><b>Date Range Queries</b>: For financial reporting periods</li>
 *   <li><b>Status Filtering</b>: DRAFT, POSTED, REVERSED entries</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Entry number lookup uses unique index</li>
 *   <li>Date + status queries use compound index</li>
 *   <li>Account code queries use embedded document index</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <pre>
 * // Find all entries for a payment
 * repository.findByCorrelationId("PAY-abc123")
 *     .collectList();
 *
 * // Get posted entries for a date range (for reports)
 * repository.findByEntryDateBetweenAndStatus(
 *     startDate, endDate, JournalEntryStatus.POSTED)
 *     .collectList();
 *
 * // Find entries affecting an account
 * repository.findByAccountCode("2010-0001")
 *     .collectList();
 * </pre>
 *
 * @see JournalEntry
 * @since 1.0.0
 */
@Repository
public interface JournalEntryRepository extends ReactiveMongoRepository<JournalEntry, String> {

    // ========================================================================
    // ENTRY NUMBER LOOKUPS (Business Identifier)
    // ========================================================================

    /**
     * Find entry by unique entry number.
     *
     * <p>Entry numbers follow format: JE-{YYYY}-{MM}-{NNNNN}</p>
     *
     * @param entryNumber The unique entry number
     * @return Mono containing the entry if found
     */
    Mono<JournalEntry> findByEntryNumber(String entryNumber);

    /**
     * Check if an entry number exists.
     *
     * <p>Use before generating new entry numbers to ensure uniqueness.</p>
     *
     * @param entryNumber The entry number to check
     * @return Mono<Boolean> true if exists
     */
    Mono<Boolean> existsByEntryNumber(String entryNumber);

    /**
     * Find entries with entry number starting with prefix.
     *
     * <p>Useful for finding entries in a specific month:
     * findByEntryNumberStartingWith("JE-2024-01")</p>
     *
     * @param prefix Entry number prefix
     * @return Flux of matching entries
     */
    Flux<JournalEntry> findByEntryNumberStartingWith(String prefix);

    // ========================================================================
    // CORRELATION ID LOOKUPS (Transaction Tracking)
    // ========================================================================

    /**
     * Find all entries for a business transaction.
     *
     * <p>A single business event (like a refund) may create multiple
     * journal entries. The correlation ID links them together.</p>
     *
     * <p>Correlation ID patterns:</p>
     * <ul>
     *   <li>PAY-{paymentIntentId}</li>
     *   <li>REF-{refundRequestId}</li>
     *   <li>OUT-{payoutRequestId}</li>
     *   <li>CHB-{chargebackId}</li>
     * </ul>
     *
     * @param correlationId The correlation identifier
     * @return Flux of related entries
     */
    Flux<JournalEntry> findByCorrelationId(String correlationId);

    /**
     * Find posted entries for a correlation ID.
     *
     * <p>Excludes draft and reversed entries.</p>
     *
     * @param correlationId The correlation identifier
     * @return Flux of posted entries for this transaction
     */
    Flux<JournalEntry> findByCorrelationIdAndStatus(
            String correlationId,
            JournalEntryStatus status
    );

    // ========================================================================
    // DATE RANGE QUERIES (Financial Reporting)
    // ========================================================================

    /**
     * Find entries within a date range.
     *
     * <p>For generating financial reports for a specific period.</p>
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Flux of entries within the date range
     */
    Flux<JournalEntry> findByEntryDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Find entries within a date range with specific status.
     *
     * <p>For financial reports, typically filter to POSTED entries only.</p>
     *
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param status Entry status filter
     * @return Flux of matching entries
     */
    Flux<JournalEntry> findByEntryDateBetweenAndStatus(
            LocalDate startDate,
            LocalDate endDate,
            JournalEntryStatus status
    );

    /**
     * Find entries on a specific date.
     *
     * @param entryDate The entry date
     * @return Flux of entries for that date
     */
    Flux<JournalEntry> findByEntryDate(LocalDate entryDate);

    /**
     * Find entries on a specific date with status.
     *
     * @param entryDate The entry date
     * @param status Entry status filter
     * @return Flux of matching entries
     */
    Flux<JournalEntry> findByEntryDateAndStatus(LocalDate entryDate, JournalEntryStatus status);

    // ========================================================================
    // STATUS QUERIES
    // ========================================================================

    /**
     * Find all entries with a specific status.
     *
     * @param status The entry status
     * @return Flux of entries with that status
     */
    Flux<JournalEntry> findByStatus(JournalEntryStatus status);

    /**
     * Find entries with status, ordered by entry date (descending).
     *
     * @param status The entry status
     * @param pageable Pagination parameters
     * @return Flux of entries
     */
    Flux<JournalEntry> findByStatusOrderByEntryDateDesc(
            JournalEntryStatus status,
            Pageable pageable
    );

    /**
     * Count entries by status.
     *
     * @param status The entry status
     * @return Mono<Long> count of entries
     */
    Mono<Long> countByStatus(JournalEntryStatus status);

    // ========================================================================
    // TYPE QUERIES
    // ========================================================================

    /**
     * Find entries by type.
     *
     * @param type The entry type (STANDARD, ADJUSTMENT, REVERSAL)
     * @return Flux of entries of that type
     */
    Flux<JournalEntry> findByType(JournalEntryType type);

    /**
     * Find entries by type and status.
     *
     * @param type Entry type
     * @param status Entry status
     * @return Flux of matching entries
     */
    Flux<JournalEntry> findByTypeAndStatus(JournalEntryType type, JournalEntryStatus status);

    // ========================================================================
    // ACCOUNT QUERIES (Embedded Document Queries)
    // ========================================================================

    /**
     * Find entries containing a specific account code.
     *
     * <p>Searches within the embedded lines array for matching accountCode.</p>
     *
     * <p>Use for:</p>
     * <ul>
     *   <li>Finding all transactions for an account</li>
     *   <li>Building account ledger reports</li>
     *   <li>Calculating account balances</li>
     * </ul>
     *
     * @param accountCode The account code to search for
     * @return Flux of entries affecting that account
     */
    @Query("{ 'lines.accountCode': ?0 }")
    Flux<JournalEntry> findByAccountCode(String accountCode);

    /**
     * Find posted entries containing a specific account code.
     *
     * <p>For account balance calculations, only consider POSTED entries.</p>
     *
     * @param accountCode The account code
     * @param status Entry status filter
     * @return Flux of matching entries
     */
    @Query("{ 'lines.accountCode': ?0, 'status': ?1 }")
    Flux<JournalEntry> findByAccountCodeAndStatus(
            String accountCode,
            JournalEntryStatus status
    );

    /**
     * Find entries for an account within a date range.
     *
     * <p>For account ledger reports within a period.</p>
     *
     * @param accountCode The account code
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return Flux of matching entries
     */
    @Query("{ 'lines.accountCode': ?0, 'entryDate': { $gte: ?1, $lte: ?2 } }")
    Flux<JournalEntry> findByAccountCodeAndEntryDateBetween(
            String accountCode,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Find posted entries for an account within a date range.
     *
     * <p>The typical query for account ledger reports.</p>
     *
     * @param accountCode The account code
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param status Entry status (typically POSTED)
     * @return Flux of matching entries
     */
    @Query("{ 'lines.accountCode': ?0, 'entryDate': { $gte: ?1, $lte: ?2 }, 'status': ?3 }")
    Flux<JournalEntry> findByAccountCodeAndEntryDateBetweenAndStatus(
            String accountCode,
            LocalDate startDate,
            LocalDate endDate,
            JournalEntryStatus status
    );

    // ========================================================================
    // REVERSAL QUERIES
    // ========================================================================

    /**
     * Find the reversal entry for a given original entry.
     *
     * <p>If an entry has been reversed, find the reversal entry.</p>
     *
     * @param originalEntryId The ID of the original (reversed) entry
     * @return Mono containing the reversal entry if it exists
     */
    Mono<JournalEntry> findByReversalOfEntryId(String originalEntryId);

    /**
     * Find entries that have been reversed.
     *
     * @return Flux of entries with REVERSED status
     */
    Flux<JournalEntry> findByReversedByEntryIdNotNull();

    // ========================================================================
    // USER QUERIES
    // ========================================================================

    /**
     * Find entries created by a specific user.
     *
     * @param userId The user ID
     * @return Flux of entries created by that user
     */
    Flux<JournalEntry> findByCreatedBy(String userId);

    /**
     * Find entries posted by a specific user.
     *
     * @param userId The user ID
     * @return Flux of entries posted by that user
     */
    Flux<JournalEntry> findByPostedBy(String userId);

    // ========================================================================
    // ENTRY NUMBER GENERATION SUPPORT
    // ========================================================================

    /**
     * Find the last entry for a specific month prefix.
     *
     * <p>Used for generating sequential entry numbers.</p>
     *
     * <p>Example: To generate the next entry number for January 2024,
     * find the last entry with prefix "JE-2024-01".</p>
     *
     * @param prefix Entry number prefix (e.g., "JE-2024-01")
     * @return Mono containing the last entry for that prefix
     */
    Mono<JournalEntry> findFirstByEntryNumberStartingWithOrderByEntryNumberDesc(String prefix);

    /**
     * Count entries with a specific prefix.
     *
     * <p>Alternative approach for entry number generation.</p>
     *
     * @param prefix Entry number prefix
     * @return Mono<Long> count of entries
     */
    Mono<Long> countByEntryNumberStartingWith(String prefix);

    // ========================================================================
    // PAGINATION SUPPORT
    // ========================================================================

    /**
     * Find all entries with pagination, ordered by entry date descending.
     *
     * @param pageable Pagination parameters
     * @return Flux of entries
     */
    Flux<JournalEntry> findAllByOrderByEntryDateDesc(Pageable pageable);

    /**
     * Find entries by status with pagination.
     *
     * @param status Entry status
     * @param pageable Pagination parameters
     * @return Flux of entries
     */
    Flux<JournalEntry> findByStatus(JournalEntryStatus status, Pageable pageable);
}
