package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Journal Entry - Core of Double-Entry Bookkeeping
 *
 * A JournalEntry is the fundamental record of any financial transaction.
 * It contains one or more {@link JournalLine} items that represent the
 * debits and credits that must always balance.
 *
 * <h2>The Golden Rule of Double-Entry</h2>
 * <p><b>Total Debits MUST ALWAYS Equal Total Credits</b></p>
 *
 * <p>This constraint is enforced before any entry can be posted. An
 * unbalanced entry cannot affect account balances.</p>
 *
 * <h2>Journal Entry Lifecycle</h2>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │                     CREATE ENTRY                            │
 *     │  Status: DRAFT                                              │
 *     │  - Can add/remove/modify lines                              │
 *     │  - Does NOT affect account balances                         │
 *     │  - Can be deleted                                           │
 *     └──────────────────────────┬──────────────────────────────────┘
 *                                │ post()
 *                                │ [Validates: balanced, accounts exist]
 *                                ▼
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │                     POSTED ENTRY                            │
 *     │  Status: POSTED                                             │
 *     │  - Immutable (cannot modify)                                │
 *     │  - DOES affect account balances                             │
 *     │  - Cannot be deleted (audit trail)                          │
 *     │  - postedAt and postedBy are set                           │
 *     └──────────────────────────┬──────────────────────────────────┘
 *                                │ reverse() [Creates reversal entry]
 *                                ▼
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │                    REVERSED ENTRY                           │
 *     │  Status: REVERSED                                           │
 *     │  - Original entry marked reversed                           │
 *     │  - Reversal entry created (type: REVERSAL)                  │
 *     │  - Net effect = zero                                        │
 *     │  - reversedAt, reversedBy, reversedByEntryId set           │
 *     └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Entry Number Format</h2>
 * <p>Each entry has a unique, sequential entry number:</p>
 * <pre>
 * Format: JE-{YYYY}-{MM}-{NNNNN}
 * Example: JE-2024-01-00001
 *
 * Components:
 * - JE: Journal Entry prefix
 * - YYYY: Year
 * - MM: Month
 * - NNNNN: Sequential number within month (5 digits)
 * </pre>
 *
 * <h2>Example: Recording a Ticket Sale</h2>
 * <pre>
 * JournalEntry entry = JournalEntry.builder()
 *     .entryNumber("JE-2024-01-00001")
 *     .correlationId("PAY-abc123")
 *     .entryDate(LocalDate.now())
 *     .description("Ticket sale - Event 'Lusaka Jazz Night'")
 *     .type(JournalEntryType.STANDARD)
 *     .lines(List.of(
 *         JournalLine.debit("1021", K100, "Gateway receivable"),
 *         JournalLine.credit("2010-0001", K90, "Event escrow"),
 *         JournalLine.credit("4010", K10, "Platform commission")
 *     ))
 *     .build();
 *
 * entry.post("user-123");  // Validates and posts
 * </pre>
 *
 * @see JournalLine
 * @see JournalEntryType
 * @see JournalEntryStatus
 * @since 1.0.0
 */
@Document(collection = "journal_entries")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "date_status_idx", def = "{'entryDate': 1, 'status': 1}"),
    @CompoundIndex(name = "type_status_idx", def = "{'type': 1, 'status': 1}"),
    @CompoundIndex(name = "correlation_idx", def = "{'correlationId': 1}"),
    @CompoundIndex(name = "account_code_idx", def = "{'lines.accountCode': 1, 'status': 1}")
})
public class JournalEntry {

    /**
     * MongoDB document ID.
     * Auto-generated, used internally by MongoDB.
     */
    @Id
    private String id;

    /**
     * Human-readable, unique entry number.
     *
     * <p>Format: JE-{YYYY}-{MM}-{NNNNN}</p>
     * <p>Example: JE-2024-01-00001</p>
     *
     * <p>This is the business identifier used in reports and audits.
     * It must be unique and is typically generated sequentially.</p>
     */
    @NotBlank(message = "Entry number is required")
    @Indexed(unique = true)
    private String entryNumber;

    /**
     * Correlation ID linking this entry to a business transaction.
     *
     * <p>Used to find all journal entries related to a single business
     * operation. For example, a refund might create multiple entries
     * (refund, commission clawback) all sharing the same correlation ID.</p>
     *
     * <p>Common patterns:</p>
     * <ul>
     *   <li>Payment: PAY-{paymentIntentId}</li>
     *   <li>Refund: REF-{refundRequestId}</li>
     *   <li>Payout: OUT-{payoutRequestId}</li>
     *   <li>Chargeback: CHB-{chargebackId}</li>
     * </ul>
     */
    @Indexed
    private String correlationId;

    /**
     * The date of the journal entry for accounting purposes.
     *
     * <p>This is the date used in financial reports. It may differ from
     * createdAt if an entry is backdated (e.g., recording yesterday's
     * transactions today).</p>
     */
    @NotNull(message = "Entry date is required")
    @Indexed
    private LocalDate entryDate;

    /**
     * The effective date for the transaction.
     *
     * <p>Optional. If different from entryDate, this is when the
     * transaction should be recognized (e.g., accrual date vs. posting date).</p>
     *
     * <p>Defaults to entryDate if not specified.</p>
     */
    private LocalDate effectiveDate;

    /**
     * Description of the journal entry.
     *
     * <p>Provides context for what business event this entry records.
     * Should be clear enough for auditors to understand without
     * needing to look up additional documents.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Ticket sale - Event 'Lusaka Jazz Night' - VIP Section"</li>
     *   <li>"Refund processed - Customer request - Order #12345"</li>
     *   <li>"Commission recognition - Event completed + 7-day hold"</li>
     * </ul>
     */
    @NotBlank(message = "Description is required")
    private String description;

    /**
     * Type of journal entry.
     *
     * @see JournalEntryType
     */
    @NotNull(message = "Entry type is required")
    @Indexed
    private JournalEntryType type;

    /**
     * Current status of the journal entry.
     *
     * @see JournalEntryStatus
     */
    @NotNull(message = "Entry status is required")
    @Indexed
    @Builder.Default
    private JournalEntryStatus status = JournalEntryStatus.DRAFT;

    /**
     * List of journal lines (debits and credits).
     *
     * <p>MUST have at least 2 lines (one debit, one credit).
     * Total debits MUST equal total credits.</p>
     */
    @NotEmpty(message = "Journal entry must have at least one line")
    @Valid
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    /**
     * Currency code for this entry.
     *
     * <p>All lines in an entry must use the same currency.
     * Multi-currency transactions require separate entries or
     * conversion at transaction time.</p>
     */
    @Builder.Default
    private String currency = "ZMW";

    // ========================================================================
    // AUDIT FIELDS
    // ========================================================================

    /**
     * User ID who created this entry.
     *
     * <p>Captures who initiated the entry for accountability.</p>
     */
    private String createdBy;

    /**
     * User ID who posted this entry.
     *
     * <p>Set when status transitions to POSTED. May be different from
     * createdBy if entries go through an approval workflow.</p>
     */
    private String postedBy;

    /**
     * Timestamp when the entry was posted.
     *
     * <p>Set automatically when post() is called successfully.</p>
     */
    private Instant postedAt;

    /**
     * User ID who reversed this entry.
     *
     * <p>Set when another entry reverses this one.</p>
     */
    private String reversedBy;

    /**
     * Timestamp when the entry was reversed.
     *
     * <p>Set when status transitions to REVERSED.</p>
     */
    private Instant reversedAt;

    /**
     * ID of the journal entry that reverses this entry.
     *
     * <p>Set when this entry is reversed. The reversal entry will have
     * type = REVERSAL and reference this entry.</p>
     */
    @Indexed
    private String reversedByEntryId;

    /**
     * ID of the journal entry that this entry reverses.
     *
     * <p>Set only on REVERSAL type entries. Points to the original
     * entry being reversed.</p>
     */
    @Indexed
    private String reversalOfEntryId;

    /**
     * Additional metadata for extensibility.
     *
     * <p>Store arbitrary key-value pairs for integration needs,
     * custom tracking, or domain-specific data.</p>
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    // ========================================================================
    // TIMESTAMPS & VERSIONING
    // ========================================================================

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     *
     * <p>Prevents concurrent modifications to the same entry.</p>
     */
    @Version
    private Long version;

    // ========================================================================
    // VALIDATION METHODS
    // ========================================================================

    /**
     * Checks if this journal entry is balanced (debits = credits).
     *
     * <p>This is THE fundamental validation for double-entry accounting.
     * An entry CANNOT be posted unless this returns true.</p>
     *
     * @return true if total debits equal total credits
     */
    public boolean isBalanced() {
        BigDecimal totalDebits = getTotalDebits();
        BigDecimal totalCredits = getTotalCredits();
        return totalDebits.compareTo(totalCredits) == 0;
    }

    /**
     * Calculates the total of all debit amounts in the entry.
     *
     * @return Sum of all debit amounts
     */
    public BigDecimal getTotalDebits() {
        if (lines == null) return BigDecimal.ZERO;
        return lines.stream()
                .map(line -> line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the total of all credit amounts in the entry.
     *
     * @return Sum of all credit amounts
     */
    public BigDecimal getTotalCredits() {
        if (lines == null) return BigDecimal.ZERO;
        return lines.stream()
                .map(line -> line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the variance between debits and credits.
     *
     * <p>Should be zero for a balanced entry. Positive means debits
     * exceed credits; negative means credits exceed debits.</p>
     *
     * @return Debits - Credits
     */
    public BigDecimal getVariance() {
        return getTotalDebits().subtract(getTotalCredits());
    }

    /**
     * Validates all lines in the entry.
     *
     * @return true if all lines are valid (XOR constraint satisfied)
     */
    public boolean hasValidLines() {
        if (lines == null || lines.isEmpty()) return false;
        return lines.stream().allMatch(JournalLine::isValid);
    }

    /**
     * Returns a list of validation errors for this entry.
     *
     * @return List of error messages, empty if valid
     */
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();

        if (lines == null || lines.isEmpty()) {
            errors.add("Entry must have at least one line");
            return errors;
        }

        if (lines.size() < 2) {
            errors.add("Entry must have at least two lines (one debit and one credit)");
        }

        // Check each line
        for (int i = 0; i < lines.size(); i++) {
            JournalLine line = lines.get(i);
            String lineError = line.getValidationError();
            if (lineError != null) {
                errors.add("Line " + (i + 1) + ": " + lineError);
            }
        }

        // Check balance
        if (!isBalanced()) {
            errors.add(String.format(
                    "Entry is not balanced: Debits=%s, Credits=%s, Variance=%s",
                    getTotalDebits(), getTotalCredits(), getVariance()
            ));
        }

        return errors;
    }

    /**
     * Checks if this entry can be posted.
     *
     * @return true if entry is in DRAFT status and valid
     */
    public boolean canPost() {
        return status == JournalEntryStatus.DRAFT
                && hasValidLines()
                && isBalanced();
    }

    // ========================================================================
    // STATE TRANSITION METHODS
    // ========================================================================

    /**
     * Posts this journal entry, making it affect account balances.
     *
     * <p>This is a critical operation that:</p>
     * <ol>
     *   <li>Validates the entry is balanced</li>
     *   <li>Validates all lines are correct</li>
     *   <li>Changes status to POSTED</li>
     *   <li>Sets postedAt and postedBy</li>
     * </ol>
     *
     * <p>Once posted, an entry is immutable. To correct a mistake,
     * create a reversal entry.</p>
     *
     * @param userId The user performing the post operation
     * @throws IllegalStateException if entry is not in DRAFT status
     * @throws IllegalArgumentException if entry is not balanced or has invalid lines
     */
    public void post(String userId) {
        if (status != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot post entry in status " + status + ". Entry must be in DRAFT status."
            );
        }

        List<String> errors = getValidationErrors();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot post unbalanced or invalid entry: " + String.join("; ", errors)
            );
        }

        this.status = JournalEntryStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = userId;
    }

    /**
     * Marks this entry as reversed by another entry.
     *
     * <p>Called when a reversal entry is posted against this entry.
     * The reversal entry ID is recorded for audit trail.</p>
     *
     * @param reversalEntryId The ID of the reversal entry
     * @param userId The user performing the reversal
     * @throws IllegalStateException if entry is not in POSTED status
     */
    public void markReversed(String reversalEntryId, String userId) {
        if (status != JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Cannot reverse entry in status " + status + ". Entry must be in POSTED status."
            );
        }

        this.status = JournalEntryStatus.REVERSED;
        this.reversedAt = Instant.now();
        this.reversedBy = userId;
        this.reversedByEntryId = reversalEntryId;
    }

    // ========================================================================
    // LINE MANAGEMENT METHODS
    // ========================================================================

    /**
     * Adds a line to this entry.
     *
     * @param line The journal line to add
     * @throws IllegalStateException if entry is not in DRAFT status
     */
    public void addLine(JournalLine line) {
        if (status != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify posted entry");
        }
        if (lines == null) {
            lines = new ArrayList<>();
        }
        lines.add(line);
    }

    /**
     * Removes a line from this entry by index.
     *
     * @param index The index of the line to remove
     * @throws IllegalStateException if entry is not in DRAFT status
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void removeLine(int index) {
        if (status != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify posted entry");
        }
        if (lines != null && index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if this entry can be modified.
     *
     * @return true if in DRAFT status
     */
    public boolean isEditable() {
        return status.isEditable();
    }

    /**
     * Checks if this entry affects account balances.
     *
     * @return true if in POSTED status (not DRAFT or REVERSED)
     */
    public boolean affectsBalances() {
        return status.affectsBalances();
    }

    /**
     * Gets the number of lines in this entry.
     *
     * @return Number of journal lines
     */
    public int getLineCount() {
        return lines != null ? lines.size() : 0;
    }

    /**
     * Gets lines affecting a specific account.
     *
     * @param accountCode The account code to filter by
     * @return List of lines for that account
     */
    public List<JournalLine> getLinesForAccount(String accountCode) {
        if (lines == null) return List.of();
        return lines.stream()
                .filter(line -> accountCode.equals(line.getAccountCode()))
                .toList();
    }

    /**
     * Gets all unique account codes in this entry.
     *
     * @return List of distinct account codes
     */
    public List<String> getAffectedAccountCodes() {
        if (lines == null) return List.of();
        return lines.stream()
                .map(JournalLine::getAccountCode)
                .distinct()
                .toList();
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a reversal entry for this entry.
     *
     * <p>A reversal entry has:</p>
     * <ul>
     *   <li>Type = REVERSAL</li>
     *   <li>All lines with opposite direction (debits→credits, credits→debits)</li>
     *   <li>Reference to the original entry (reversalOfEntryId)</li>
     *   <li>Same correlation ID for traceability</li>
     * </ul>
     *
     * @param newEntryNumber Entry number for the reversal
     * @param reason Reason for the reversal
     * @param userId User creating the reversal
     * @return New reversal entry (in DRAFT status)
     * @throws IllegalStateException if this entry is not in POSTED status
     */
    public JournalEntry createReversal(String newEntryNumber, String reason, String userId) {
        if (status != JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Cannot create reversal for entry in status " + status + ". Must be POSTED."
            );
        }

        List<JournalLine> reversedLines = lines.stream()
                .map(JournalLine::reversed)
                .toList();

        return JournalEntry.builder()
                .entryNumber(newEntryNumber)
                .correlationId(this.correlationId)
                .entryDate(LocalDate.now())
                .effectiveDate(LocalDate.now())
                .description("Reversal of " + this.entryNumber + ": " + reason)
                .type(JournalEntryType.REVERSAL)
                .status(JournalEntryStatus.DRAFT)
                .lines(new ArrayList<>(reversedLines))
                .currency(this.currency)
                .reversalOfEntryId(this.id)
                .createdBy(userId)
                .build();
    }

    /**
     * Creates a simple two-line entry (one debit, one credit).
     *
     * <p>Useful for simple transactions with a single debit and credit.</p>
     *
     * @param entryNumber Unique entry number
     * @param entryDate Date of the entry
     * @param description Entry description
     * @param debitAccountCode Account to debit
     * @param creditAccountCode Account to credit
     * @param amount Amount to transfer
     * @param createdBy User creating the entry
     * @return New journal entry in DRAFT status
     */
    public static JournalEntry createSimpleEntry(
            String entryNumber,
            LocalDate entryDate,
            String description,
            String debitAccountCode,
            String creditAccountCode,
            BigDecimal amount,
            String createdBy
    ) {
        return JournalEntry.builder()
                .entryNumber(entryNumber)
                .entryDate(entryDate)
                .description(description)
                .type(JournalEntryType.STANDARD)
                .status(JournalEntryStatus.DRAFT)
                .lines(List.of(
                        JournalLine.debit(debitAccountCode, amount, description),
                        JournalLine.credit(creditAccountCode, amount, description)
                ))
                .createdBy(createdBy)
                .build();
    }
}
