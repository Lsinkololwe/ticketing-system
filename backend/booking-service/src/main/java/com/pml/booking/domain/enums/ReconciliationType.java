package com.pml.booking.domain.enums;

/**
 * Reconciliation Type Enum - Categories of Financial Reconciliation
 *
 * This enum defines the types of reconciliation runs supported by the
 * financial engine. Each type compares internal records against a
 * different external or internal data source.
 *
 * <h2>Reconciliation Overview</h2>
 * <p>Reconciliation is the process of ensuring that internal accounting
 * records match external sources (bank statements, gateway reports) or
 * internal sources (escrow balances).</p>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    RECONCILIATION TYPES                         │
 * ├──────────────────────────────────────────────────────────────────┤
 * │                                                                  │
 * │  GATEWAY Reconciliation (Daily)                                  │
 * │  ┌─────────────────┐          ┌─────────────────────────┐       │
 * │  │ PawaPay Report  │  ══════  │ PaymentIntent Records   │       │
 * │  │ (External)      │  compare │ (Internal)              │       │
 * │  └─────────────────┘          └─────────────────────────┘       │
 * │                                                                  │
 * │  BANK Reconciliation (Daily/Weekly)                              │
 * │  ┌─────────────────┐          ┌─────────────────────────┐       │
 * │  │ Bank Statement  │  ══════  │ JournalEntry Records    │       │
 * │  │ (External)      │  compare │ (Internal)              │       │
 * │  └─────────────────┘          └─────────────────────────┘       │
 * │                                                                  │
 * │  ESCROW Reconciliation (Real-time/Daily)                         │
 * │  ┌─────────────────┐          ┌─────────────────────────┐       │
 * │  │ Escrow.balance  │  ══════  │ SUM(EscrowTransactions) │       │
 * │  │ (Stored)        │  compare │ (Calculated)            │       │
 * │  └─────────────────┘          └─────────────────────────┘       │
 * │                                                                  │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see ReconciliationStatus
 * @see ReconciliationItemStatus
 * @since 1.0.0
 */
public enum ReconciliationType {

    /**
     * Gateway Reconciliation - Compares payment gateway records with internal records.
     *
     * <p>Purpose: Ensure all payments reported by the gateway (PawaPay) are recorded
     * internally, and vice versa.</p>
     *
     * <h3>What is Compared</h3>
     * <ul>
     *   <li>External: Gateway settlement report (CSV/API)</li>
     *   <li>Internal: PaymentIntent records in MongoDB</li>
     * </ul>
     *
     * <h3>Common Discrepancies</h3>
     * <ul>
     *   <li><b>UNMATCHED_EXTERNAL</b>: Gateway shows payment we don't have (webhook missed?)</li>
     *   <li><b>UNMATCHED_INTERNAL</b>: We show payment gateway doesn't have (duplicate?)</li>
     *   <li><b>AMOUNT_MISMATCH</b>: Same transaction, different amounts (currency conversion?)</li>
     * </ul>
     *
     * <h3>Frequency</h3>
     * <p>Run daily after gateway settlement (usually T+1 or T+0 for mobile money)</p>
     */
    GATEWAY,

    /**
     * Bank Reconciliation - Compares bank statement with internal cash records.
     *
     * <p>Purpose: Ensure all bank account movements are recorded internally,
     * identifying any missing deposits, withdrawals, or fees.</p>
     *
     * <h3>What is Compared</h3>
     * <ul>
     *   <li>External: Bank statement (MT940, CSV, or API)</li>
     *   <li>Internal: JournalEntry records affecting bank accounts (1010, 1011)</li>
     * </ul>
     *
     * <h3>Common Discrepancies</h3>
     * <ul>
     *   <li><b>UNMATCHED_EXTERNAL</b>: Bank fee not recorded internally</li>
     *   <li><b>UNMATCHED_INTERNAL</b>: Payout recorded but not yet on statement (timing)</li>
     *   <li><b>AMOUNT_MISMATCH</b>: Settlement includes fees not separately recorded</li>
     * </ul>
     *
     * <h3>Frequency</h3>
     * <p>Run daily or weekly depending on transaction volume</p>
     */
    BANK,

    /**
     * Escrow Reconciliation - Verifies escrow account balance matches transaction history.
     *
     * <p>Purpose: Ensure the stored balance in each EventEscrowAccount matches
     * the calculated sum of all its transactions. Detects data corruption or
     * missing transactions.</p>
     *
     * <h3>What is Compared</h3>
     * <ul>
     *   <li>Stored: EventEscrowAccount.currentBalance</li>
     *   <li>Calculated: SUM(EscrowTransaction.amount) for that account</li>
     * </ul>
     *
     * <h3>Common Discrepancies</h3>
     * <ul>
     *   <li><b>AMOUNT_MISMATCH</b>: Stored balance != calculated balance (bug or corruption)</li>
     *   <li><b>UNMATCHED_INTERNAL</b>: Transaction references non-existent escrow</li>
     * </ul>
     *
     * <h3>Frequency</h3>
     * <p>Run in real-time after each transaction, plus daily batch verification</p>
     */
    ESCROW,

    /**
     * Escrow-Journal Cross-Verification - Ensures escrow balances match journal entries.
     *
     * <p>Purpose: Verify that the stored balance in EventEscrowAccount matches
     * the calculated balance from journal entries for the corresponding
     * Chart of Accounts escrow account (2010-xxx). This is a critical integrity
     * check that ensures dual-tracking consistency.</p>
     *
     * <h3>What is Compared</h3>
     * <ul>
     *   <li>Escrow Balance: EventEscrowAccount.currentBalance</li>
     *   <li>Journal Balance: getAccountBalance("2010-{eventId}") from posted journal entries</li>
     * </ul>
     *
     * <h3>Why This Matters</h3>
     * <p>The system maintains dual tracking for escrow accounts:</p>
     * <ul>
     *   <li><b>EventEscrowAccount</b>: Optimized for fast balance queries and operational use</li>
     *   <li><b>Journal Entries</b>: Double-entry bookkeeping for accounting compliance</li>
     * </ul>
     * <p>Both MUST always agree. If they diverge, it indicates a bug in the code
     * where one was updated without the other.</p>
     *
     * <h3>Common Discrepancies</h3>
     * <ul>
     *   <li><b>AMOUNT_MISMATCH</b>: Journal entries created but escrow not updated (or vice versa)</li>
     *   <li><b>MISSING_JOURNAL</b>: Escrow account exists but no corresponding CoA entry</li>
     *   <li><b>ORPHANED_JOURNAL</b>: CoA entry exists but no EventEscrowAccount</li>
     * </ul>
     *
     * <h3>Frequency</h3>
     * <p>Run daily as part of end-of-day financial integrity checks</p>
     */
    ESCROW_JOURNAL;

    /**
     * Returns the recommended frequency for this reconciliation type.
     *
     * @return Human-readable frequency recommendation
     */
    public String getRecommendedFrequency() {
        return switch (this) {
            case GATEWAY -> "Daily (after gateway settlement)";
            case BANK -> "Daily or Weekly (based on volume)";
            case ESCROW -> "Real-time + Daily batch";
            case ESCROW_JOURNAL -> "Daily (end-of-day integrity check)";
        };
    }

    /**
     * Returns the external data source for this reconciliation type.
     *
     * @return Description of the external/comparison data source
     */
    public String getDataSource() {
        return switch (this) {
            case GATEWAY -> "Payment Gateway Settlement Report";
            case BANK -> "Bank Statement (MT940/CSV/API)";
            case ESCROW -> "Calculated Sum of Escrow Transactions";
            case ESCROW_JOURNAL -> "Journal Entry Balance (2010-xxx accounts)";
        };
    }

    /**
     * Returns the internal data source for this reconciliation type.
     *
     * @return Description of the internal data being compared
     */
    public String getInternalSource() {
        return switch (this) {
            case GATEWAY -> "PaymentIntent and PaymentAttempt Records";
            case BANK -> "JournalEntry Records (Bank Accounts)";
            case ESCROW -> "EventEscrowAccount.currentBalance";
            case ESCROW_JOURNAL -> "EventEscrowAccount.currentBalance";
        };
    }

    /**
     * Returns whether this reconciliation type involves cross-system verification.
     *
     * @return true if this type verifies consistency between two internal systems
     */
    public boolean isCrossSystemVerification() {
        return this == ESCROW_JOURNAL;
    }
}
