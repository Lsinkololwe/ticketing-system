package com.pml.booking.domain.enums;

/**
 * Reconciliation Item Status Enum - Status of Individual Reconciliation Items
 *
 * This enum represents the matching status of individual records within
 * a reconciliation run. Each item is either matched or has a specific
 * type of discrepancy.
 *
 * <h2>Matching Process</h2>
 * <pre>
 * External Records (from Gateway/Bank):
 *   ┌──────────────────────────────────┐
 *   │ TX001 | K100 | 2024-01-15 09:00 │
 *   │ TX002 | K250 | 2024-01-15 10:30 │
 *   │ TX003 | K75  | 2024-01-15 11:45 │  ← Exists externally, not internally
 *   └──────────────────────────────────┘
 *
 * Internal Records (from MongoDB):
 *   ┌──────────────────────────────────┐
 *   │ TX001 | K100 | 2024-01-15 09:00 │  ← MATCHED
 *   │ TX002 | K245 | 2024-01-15 10:30 │  ← AMOUNT_MISMATCH (K250 vs K245)
 *   │ TX004 | K200 | 2024-01-15 12:00 │  ← Exists internally, not externally
 *   └──────────────────────────────────┘
 *
 * Reconciliation Result:
 *   TX001 → MATCHED
 *   TX002 → AMOUNT_MISMATCH (external K250, internal K245)
 *   TX003 → UNMATCHED_EXTERNAL (exists in gateway, missing internally)
 *   TX004 → UNMATCHED_INTERNAL (exists internally, missing in gateway)
 * </pre>
 *
 * @see ReconciliationType
 * @see ReconciliationStatus
 * @since 1.0.0
 */
public enum ReconciliationItemStatus {

    /**
     * Matched - Record exists in both sources with matching amounts.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Transaction ID found in both external and internal records</li>
     *   <li>Amounts match (within tolerance threshold)</li>
     *   <li>No further action required</li>
     * </ul>
     *
     * <p>This is the ideal status for all items.</p>
     */
    MATCHED,

    /**
     * Unmatched External - Record exists in external source but not internally.
     *
     * <p>Severity: HIGH - Potential missing revenue/payment</p>
     *
     * <p>Possible causes:</p>
     * <ul>
     *   <li><b>Missing webhook</b>: Payment callback never received</li>
     *   <li><b>Processing error</b>: Webhook received but failed to process</li>
     *   <li><b>Timing</b>: External report is ahead of internal processing</li>
     *   <li><b>Wrong account</b>: Payment recorded to wrong system/tenant</li>
     * </ul>
     *
     * <p>Resolution steps:</p>
     * <ol>
     *   <li>Check webhook logs for the transaction ID</li>
     *   <li>Search for payment by amount/date if ID not found</li>
     *   <li>If missing, create manual payment record</li>
     *   <li>Investigate why webhook was missed (system issue?)</li>
     * </ol>
     */
    UNMATCHED_EXTERNAL,

    /**
     * Unmatched Internal - Record exists internally but not in external source.
     *
     * <p>Severity: MEDIUM - Potential duplicate or fraudulent entry</p>
     *
     * <p>Possible causes:</p>
     * <ul>
     *   <li><b>Duplicate entry</b>: Same payment recorded twice internally</li>
     *   <li><b>Timing</b>: Internal record created but gateway not yet settled</li>
     *   <li><b>Test transaction</b>: Test payment not in production report</li>
     *   <li><b>Fraud</b>: Manually created payment without actual money</li>
     * </ul>
     *
     * <p>Resolution steps:</p>
     * <ol>
     *   <li>Check if this is a future-dated settlement (timing issue)</li>
     *   <li>Search for duplicates of the same payment</li>
     *   <li>Verify with gateway API if payment actually exists</li>
     *   <li>If invalid, mark for reversal</li>
     * </ol>
     */
    UNMATCHED_INTERNAL,

    /**
     * Amount Mismatch - Record exists in both sources but amounts differ.
     *
     * <p>Severity: MEDIUM - Financial discrepancy needs investigation</p>
     *
     * <p>Possible causes:</p>
     * <ul>
     *   <li><b>Gateway fees</b>: External shows net after fee deduction</li>
     *   <li><b>Currency conversion</b>: Different exchange rates applied</li>
     *   <li><b>Partial refund</b>: Original amount vs. post-refund amount</li>
     *   <li><b>Data entry error</b>: Manual entry with wrong amount</li>
     *   <li><b>Rounding</b>: Different precision in different systems</li>
     * </ul>
     *
     * <p>Resolution steps:</p>
     * <ol>
     *   <li>Calculate the variance amount</li>
     *   <li>Check if variance equals known fee amounts</li>
     *   <li>Verify exchange rates if different currencies involved</li>
     *   <li>If fees, record adjustment entry for the fee</li>
     *   <li>If error, create correcting journal entry</li>
     * </ol>
     */
    AMOUNT_MISMATCH;

    /**
     * Checks if this status indicates a successful match.
     *
     * @return true only if both records exist and amounts match
     */
    public boolean isMatched() {
        return this == MATCHED;
    }

    /**
     * Checks if this status indicates a discrepancy requiring review.
     *
     * @return true if human review is needed
     */
    public boolean isDiscrepancy() {
        return this != MATCHED;
    }

    /**
     * Returns the severity level for this item status.
     *
     * @return Severity level for alerting and prioritization
     */
    public String getSeverity() {
        return switch (this) {
            case MATCHED -> "OK";
            case UNMATCHED_EXTERNAL -> "HIGH";
            case UNMATCHED_INTERNAL -> "MEDIUM";
            case AMOUNT_MISMATCH -> "MEDIUM";
        };
    }

    /**
     * Returns recommended investigation priority (1 = highest).
     *
     * <p>UNMATCHED_EXTERNAL is highest priority because it may indicate
     * missing revenue that needs to be recorded.</p>
     *
     * @return Priority number (1-3)
     */
    public int getPriority() {
        return switch (this) {
            case MATCHED -> 4;           // No action needed
            case UNMATCHED_EXTERNAL -> 1; // Potential missing revenue
            case AMOUNT_MISMATCH -> 2;    // Financial discrepancy
            case UNMATCHED_INTERNAL -> 3; // Potential duplicate
        };
    }

    /**
     * Returns a human-readable description of what this status means.
     *
     * @return Description suitable for display to users
     */
    public String getDescription() {
        return switch (this) {
            case MATCHED -> "Records match - no action required";
            case UNMATCHED_EXTERNAL -> "Found in gateway/bank but missing internally";
            case UNMATCHED_INTERNAL -> "Found internally but missing in gateway/bank";
            case AMOUNT_MISMATCH -> "Transaction found but amounts differ";
        };
    }
}
