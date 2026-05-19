package com.pml.booking.domain.enums;

/**
 * Journal Entry Status Enum - Lifecycle State of a Journal Entry
 *
 * This enum represents the status of a journal entry as it moves through
 * the accounting workflow. Entries must be validated and posted before
 * they affect account balances.
 *
 * <h2>Status Lifecycle</h2>
 * <pre>
 *     ┌─────────┐
 *     │  DRAFT  │  Entry created but not yet finalized
 *     └────┬────┘
 *          │ post()
 *          ▼
 *     ┌─────────┐
 *     │ POSTED  │  Entry is finalized and affects balances
 *     └────┬────┘
 *          │ reverse()
 *          ▼
 *     ┌──────────┐
 *     │ REVERSED │  Entry has been reversed by another entry
 *     └──────────┘
 * </pre>
 *
 * <h2>Status Rules</h2>
 * <ul>
 *   <li><b>DRAFT</b>: Can be edited or deleted. Does NOT affect account balances.</li>
 *   <li><b>POSTED</b>: Immutable. Affects account balances. Can only be reversed.</li>
 *   <li><b>REVERSED</b>: Immutable. A reversal entry exists. Net effect = zero.</li>
 * </ul>
 *
 * <h2>Posting Requirements</h2>
 * <p>Before an entry can transition from DRAFT to POSTED:</p>
 * <ol>
 *   <li>Entry must have at least 2 lines (one debit, one credit)</li>
 *   <li>Total debits MUST equal total credits (balanced)</li>
 *   <li>All account codes must exist and be active</li>
 *   <li>Entry date must be within open accounting period</li>
 *   <li>Required fields (description, correlation ID) must be set</li>
 * </ol>
 *
 * @see JournalEntryType
 * @since 1.0.0
 */
public enum JournalEntryStatus {

    /**
     * Draft - Entry created but not yet posted.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Can be edited (lines added, removed, modified)</li>
     *   <li>Can be deleted without trace</li>
     *   <li>Does NOT affect account balances</li>
     *   <li>Does NOT appear in financial reports</li>
     * </ul>
     *
     * <p>Transitions:</p>
     * <ul>
     *   <li>→ POSTED: When post() is called with valid, balanced entry</li>
     *   <li>→ (deleted): Entry can be deleted while in draft</li>
     * </ul>
     */
    DRAFT,

    /**
     * Posted - Entry is finalized and immutable.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>CANNOT be edited (immutable)</li>
     *   <li>CANNOT be deleted (audit trail requirement)</li>
     *   <li>DOES affect account balances</li>
     *   <li>DOES appear in financial reports</li>
     *   <li>Has postedAt timestamp and postedBy user</li>
     * </ul>
     *
     * <p>Transitions:</p>
     * <ul>
     *   <li>→ REVERSED: When a reversal entry is posted against this entry</li>
     * </ul>
     *
     * <p>To "fix" a posted entry, create a reversal entry to cancel it,
     * then create a new correct entry.</p>
     */
    POSTED,

    /**
     * Reversed - Entry has been reversed by another entry.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>CANNOT be edited or deleted</li>
     *   <li>Has reversedAt timestamp and reversedBy user</li>
     *   <li>References the reversal entry (reversedByEntryId)</li>
     *   <li>Net effect with reversal entry = zero</li>
     *   <li>Still appears in audit trails and historical reports</li>
     * </ul>
     *
     * <p>An entry can only be reversed once. The reversal entry itself
     * becomes POSTED (not REVERSED) unless it too is reversed later.</p>
     */
    REVERSED;

    /**
     * Checks if this status allows the entry to be modified.
     *
     * <p>Only DRAFT entries can be modified.</p>
     *
     * @return true if the entry can be edited in this status
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * Checks if this status allows the entry to be deleted.
     *
     * <p>Only DRAFT entries can be deleted. Posted and Reversed entries
     * must be preserved for audit trail.</p>
     *
     * @return true if the entry can be deleted in this status
     */
    public boolean isDeletable() {
        return this == DRAFT;
    }

    /**
     * Checks if this status means the entry affects account balances.
     *
     * <p>Only POSTED entries affect current account balances.
     * REVERSED entries had their effect nullified by the reversal.</p>
     *
     * @return true if the entry currently affects account balances
     */
    public boolean affectsBalances() {
        return this == POSTED;
    }

    /**
     * Checks if this status allows the entry to be reversed.
     *
     * <p>Only POSTED entries can be reversed. DRAFT entries should be
     * deleted instead. REVERSED entries are already nullified.</p>
     *
     * @return true if a reversal entry can be created against this entry
     */
    public boolean canBeReversed() {
        return this == POSTED;
    }

    /**
     * Checks if this is a terminal status (no further transitions).
     *
     * <p>REVERSED is a terminal status - once reversed, an entry
     * cannot change status again.</p>
     *
     * @return true if no further status transitions are possible
     */
    public boolean isTerminal() {
        return this == REVERSED;
    }
}
