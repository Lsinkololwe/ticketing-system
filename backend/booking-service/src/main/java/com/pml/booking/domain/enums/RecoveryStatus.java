package com.pml.booking.domain.enums;

/**
 * Recovery Status Enum - Status of Chargeback Fund Recovery from Organizers
 *
 * When a chargeback is accepted or lost, the platform typically recovers
 * the loss from the event organizer (since it was their event that caused
 * the issue). This enum tracks that recovery process.
 *
 * <h2>Recovery Lifecycle</h2>
 * <pre>
 *     ┌─────────────────┐
 *     │   NOT_STARTED   │  Chargeback not yet finalized
 *     └────────┬────────┘
 *              │ Chargeback ACCEPTED or LOST
 *              ▼
 *     ┌─────────────────┐
 *     │  IN_PROGRESS    │  Recovery attempt underway
 *     └────────┬────────┘
 *              │
 *     ┌────────┴────────┐
 *     │                 │
 *     ▼                 ▼
 * ┌───────────┐    ┌────────────┐
 * │ RECOVERED │    │ WRITTEN_OFF│
 * │ (success) │    │  (failed)  │
 * └───────────┘    └────────────┘
 * </pre>
 *
 * <h2>Recovery Priority (Waterfall)</h2>
 * <p>Recovery is attempted in this order until fully recovered:</p>
 * <ol>
 *   <li><b>Organizer Escrow</b> - Debit from event's escrow account</li>
 *   <li><b>Future Payouts</b> - Deduct from pending payouts</li>
 *   <li><b>Platform Reserve</b> - Use platform's reserve account</li>
 *   <li><b>Write-Off</b> - Record as bad debt expense</li>
 * </ol>
 *
 * @see ChargebackStatus
 * @see ChargebackFundSource
 * @since 1.0.0
 */
public enum RecoveryStatus {

    /**
     * Not Started - Recovery has not yet begun.
     *
     * <p>This is the initial state. Recovery begins when:</p>
     * <ul>
     *   <li>Chargeback is ACCEPTED (decision not to fight), or</li>
     *   <li>Chargeback is LOST (dispute unsuccessful)</li>
     * </ul>
     *
     * <p>While in this state, the chargeback may still be disputed.</p>
     */
    NOT_STARTED,

    /**
     * In Progress - Recovery attempt is underway.
     *
     * <p>During this state:</p>
     * <ul>
     *   <li>System is checking available fund sources</li>
     *   <li>Deductions may be in process</li>
     *   <li>Partial recovery may have occurred</li>
     * </ul>
     *
     * <h3>Recovery Waterfall</h3>
     * <pre>
     * Check organizer's event escrow balance
     *   ├── Has funds? → Debit from escrow
     *   └── Insufficient? → Check future payouts
     *         ├── Has pending payouts? → Reduce payout
     *         └── No payouts? → Use platform reserve
     *               ├── Reserve available? → Debit reserve
     *               └── No reserve? → Write off as bad debt
     * </pre>
     *
     * <h3>Partial Recovery</h3>
     * <p>If the full amount cannot be recovered from one source,
     * the system will combine multiple sources (e.g., some from
     * escrow, remainder from future payouts).</p>
     */
    IN_PROGRESS,

    /**
     * Recovered - Full chargeback amount has been recovered.
     *
     * <p>This is a successful terminal state.</p>
     *
     * <h3>What This Means</h3>
     * <ul>
     *   <li>Platform did not lose money on this chargeback</li>
     *   <li>Organizer bore the cost (as per terms)</li>
     *   <li>Recovery journal entries completed</li>
     * </ul>
     *
     * <h3>Journal Entry Created</h3>
     * <pre>
     * DR  Chargeback Recovery Receivable (1023)   -K100  (reduce)
     * DR  Event Escrow (2010-XXXX)                 K100  (reduce liability)
     *   OR
     * DR  Payout Payable (2021)                    K100  (reduce liability)
     * </pre>
     */
    RECOVERED,

    /**
     * Written Off - Unable to recover; recorded as bad debt.
     *
     * <p>This is an unsuccessful terminal state.</p>
     *
     * <h3>When This Happens</h3>
     * <ul>
     *   <li>Organizer has no escrow balance</li>
     *   <li>Organizer has no pending payouts</li>
     *   <li>Platform reserve is insufficient</li>
     *   <li>Policy decision to absorb the loss</li>
     * </ul>
     *
     * <h3>Financial Impact</h3>
     * <pre>
     * DR  Bad Debt Expense (5040)                  K100  (increase expense)
     * CR  Chargeback Recovery Receivable (1023)   K100  (reduce asset)
     * </pre>
     *
     * <p>This directly impacts platform profitability.</p>
     *
     * <h3>Follow-up Actions</h3>
     * <ul>
     *   <li>Review organizer's standing</li>
     *   <li>Consider restricting organizer's future events</li>
     *   <li>Track for collection if organizer returns</li>
     * </ul>
     */
    WRITTEN_OFF;

    /**
     * Checks if recovery is still in progress.
     *
     * @return true if recovery attempt is ongoing
     */
    public boolean isInProgress() {
        return this == IN_PROGRESS;
    }

    /**
     * Checks if this is a terminal (final) state.
     *
     * @return true if no further recovery actions will occur
     */
    public boolean isTerminal() {
        return this == RECOVERED || this == WRITTEN_OFF;
    }

    /**
     * Checks if recovery was successful.
     *
     * @return true if full amount was recovered
     */
    public boolean isSuccessful() {
        return this == RECOVERED;
    }

    /**
     * Checks if recovery failed and resulted in a loss.
     *
     * @return true if amount was written off as bad debt
     */
    public boolean isWrittenOff() {
        return this == WRITTEN_OFF;
    }

    /**
     * Checks if recovery can still be attempted.
     *
     * <p>Recovery can only be started when NOT_STARTED.</p>
     *
     * @return true if recovery can be initiated
     */
    public boolean canStartRecovery() {
        return this == NOT_STARTED;
    }

    /**
     * Returns the financial impact description.
     *
     * @return Description of what this status means financially
     */
    public String getFinancialImpact() {
        return switch (this) {
            case NOT_STARTED -> "No financial impact yet; pending chargeback resolution";
            case IN_PROGRESS -> "Recovery in progress; partial amounts may be secured";
            case RECOVERED -> "No net loss; organizer bore the chargeback cost";
            case WRITTEN_OFF -> "Direct loss to platform; recorded as bad debt expense";
        };
    }
}
