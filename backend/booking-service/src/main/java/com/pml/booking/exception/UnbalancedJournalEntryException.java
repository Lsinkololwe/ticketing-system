package com.pml.booking.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a journal entry fails balance validation.
 *
 * <p>In double-entry bookkeeping, every journal entry MUST be balanced:
 * the sum of all debit amounts must equal the sum of all credit amounts.
 * This exception is thrown when attempting to post an unbalanced entry.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Missing journal line (e.g., forgot to add commission credit)</li>
 *   <li>Incorrect amount calculation (rounding errors)</li>
 *   <li>Partial entry construction (entry not fully built)</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * <ol>
 *   <li>Calculate total debits and credits separately</li>
 *   <li>Identify the variance amount</li>
 *   <li>Add or adjust lines to make debits equal credits</li>
 *   <li>Verify the business logic is correct</li>
 * </ol>
 *
 * @see com.pml.booking.domain.model.JournalEntry
 * @since 1.0.0
 */
public class UnbalancedJournalEntryException extends RuntimeException {

    /**
     * The entry number of the unbalanced entry.
     */
    private final String entryNumber;

    /**
     * Total debit amount in the entry.
     */
    private final BigDecimal totalDebits;

    /**
     * Total credit amount in the entry.
     */
    private final BigDecimal totalCredits;

    /**
     * The variance (debits - credits).
     */
    private final BigDecimal variance;

    /**
     * Creates a new UnbalancedJournalEntryException with basic message.
     *
     * @param message Error message
     */
    public UnbalancedJournalEntryException(String message) {
        super(message);
        this.entryNumber = null;
        this.totalDebits = null;
        this.totalCredits = null;
        this.variance = null;
    }

    /**
     * Creates a new UnbalancedJournalEntryException with full details.
     *
     * @param entryNumber The entry number being posted
     * @param totalDebits Total of all debit amounts
     * @param totalCredits Total of all credit amounts
     */
    public UnbalancedJournalEntryException(
            String entryNumber,
            BigDecimal totalDebits,
            BigDecimal totalCredits
    ) {
        super(String.format(
                "Journal entry %s is not balanced. Debits: %s, Credits: %s, Variance: %s",
                entryNumber,
                totalDebits,
                totalCredits,
                totalDebits.subtract(totalCredits)
        ));
        this.entryNumber = entryNumber;
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
        this.variance = totalDebits.subtract(totalCredits);
    }

    /**
     * Creates a new UnbalancedJournalEntryException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public UnbalancedJournalEntryException(String message, Throwable cause) {
        super(message, cause);
        this.entryNumber = null;
        this.totalDebits = null;
        this.totalCredits = null;
        this.variance = null;
    }

    // Getters

    public String getEntryNumber() {
        return entryNumber;
    }

    public BigDecimal getTotalDebits() {
        return totalDebits;
    }

    public BigDecimal getTotalCredits() {
        return totalCredits;
    }

    public BigDecimal getVariance() {
        return variance;
    }
}
