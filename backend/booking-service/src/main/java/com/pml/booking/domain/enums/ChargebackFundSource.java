package com.pml.booking.domain.enums;

/**
 * Chargeback Fund Source Enum - Sources for Recovering Chargeback Losses
 *
 * When a chargeback is accepted or lost, the platform needs to recover
 * the funds. This enum defines the sources from which recovery can
 * be attempted, in priority order.
 *
 * <h2>Recovery Waterfall (Priority Order)</h2>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │                   CHARGEBACK RECOVERY                       │
 *     │                   Amount: K500                              │
 *     └─────────────────────────┬───────────────────────────────────┘
 *                               │
 *                               ▼
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │ STEP 1: ORGANIZER_ESCROW                                    │
 *     │ Check: Event escrow balance for this event                  │
 *     │ Balance: K300                                               │
 *     │ Action: Debit K300 → Remaining: K200                        │
 *     └─────────────────────────┬───────────────────────────────────┘
 *                               │
 *                               ▼
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │ STEP 2: ORGANIZER_FUTURE                                    │
 *     │ Check: Pending payouts for this organizer                   │
 *     │ Pending: K150                                               │
 *     │ Action: Reduce future payout by K150 → Remaining: K50       │
 *     └─────────────────────────┬───────────────────────────────────┘
 *                               │
 *                               ▼
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │ STEP 3: PLATFORM_RESERVE                                    │
 *     │ Check: Platform reserve account                             │
 *     │ Reserve: K1000                                              │
 *     │ Action: Debit K50 → Remaining: K0 ✓ RECOVERED               │
 *     └─────────────────────────────────────────────────────────────┘
 *
 *     If all sources exhausted and amount remains:
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │ STEP 4: WRITE_OFF                                           │
 *     │ Action: Record as Bad Debt Expense                          │
 *     │ Impact: Direct P&L loss                                     │
 *     └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @see RecoveryStatus
 * @see ChargebackStatus
 * @since 1.0.0
 */
public enum ChargebackFundSource {

    /**
     * Organizer Escrow - Recover from the event's escrow account.
     *
     * <p><b>Priority: 1 (Highest)</b></p>
     *
     * <p>This is the first and most appropriate source because:</p>
     * <ul>
     *   <li>The chargeback is for this specific event</li>
     *   <li>Funds are already held by the platform</li>
     *   <li>Organizer agreed to this in terms of service</li>
     *   <li>No additional collection effort needed</li>
     * </ul>
     *
     * <h3>Limitations</h3>
     * <ul>
     *   <li>Escrow may be empty (event completed, paid out)</li>
     *   <li>Balance may be insufficient</li>
     *   <li>May affect organizer's expected payout</li>
     * </ul>
     *
     * <h3>Journal Entry</h3>
     * <pre>
     * DR  Chargeback Expense (5020)          K100  (for the fee portion)
     * DR  Event Escrow (2010-XXXX)           K100  (reduce liability)
     * CR  Chargeback Recovery Receivable     K100  (clear receivable)
     * </pre>
     */
    ORGANIZER_ESCROW,

    /**
     * Organizer Future Payouts - Recover from pending/future payouts.
     *
     * <p><b>Priority: 2</b></p>
     *
     * <p>If escrow is insufficient, deduct from the organizer's other
     * pending payouts (from other events).</p>
     *
     * <h3>How It Works</h3>
     * <ul>
     *   <li>Find all PENDING payout requests for this organizer</li>
     *   <li>Reduce payout amounts until debt is covered</li>
     *   <li>Create adjustment records on affected payouts</li>
     * </ul>
     *
     * <h3>Limitations</h3>
     * <ul>
     *   <li>Organizer may have no other events</li>
     *   <li>Payouts may already be processing</li>
     *   <li>May create negative organizer relationship</li>
     * </ul>
     *
     * <h3>Journal Entry</h3>
     * <pre>
     * DR  Organizer Payout Payable (2021)    K100  (reduce liability)
     * CR  Chargeback Recovery Receivable     K100  (clear receivable)
     * </pre>
     */
    ORGANIZER_FUTURE,

    /**
     * Platform Reserve - Use platform's reserve account for coverage.
     *
     * <p><b>Priority: 3</b></p>
     *
     * <p>If organizer funds are insufficient, the platform's reserve
     * account absorbs the remaining loss.</p>
     *
     * <h3>Purpose of Reserve Account</h3>
     * <ul>
     *   <li>Cover unexpected losses (chargebacks, fraud)</li>
     *   <li>Maintain operational liquidity</li>
     *   <li>Buffer against chargeback spikes</li>
     * </ul>
     *
     * <h3>When to Use</h3>
     * <ul>
     *   <li>Organizer has no remaining funds</li>
     *   <li>Quick resolution needed (small amounts)</li>
     *   <li>Policy decision to protect organizer relationship</li>
     * </ul>
     *
     * <h3>Journal Entry</h3>
     * <pre>
     * DR  Chargeback Expense (5020)          K100  (platform expense)
     * CR  Platform Reserve Account           K100  (reduce reserve)
     * </pre>
     *
     * <p>Note: May still pursue collection from organizer later.</p>
     */
    PLATFORM_RESERVE,

    /**
     * Write Off - Record as uncollectible bad debt.
     *
     * <p><b>Priority: 4 (Last Resort)</b></p>
     *
     * <p>When all other sources are exhausted, the remaining amount
     * is written off as bad debt expense.</p>
     *
     * <h3>When This Happens</h3>
     * <ul>
     *   <li>Organizer escrow is zero</li>
     *   <li>No pending payouts exist</li>
     *   <li>Reserve account depleted or policy limit reached</li>
     *   <li>Collection deemed uneconomical</li>
     * </ul>
     *
     * <h3>Financial Impact</h3>
     * <ul>
     *   <li>Direct hit to platform profitability</li>
     *   <li>Increases Bad Debt Expense (5040)</li>
     *   <li>May trigger review of organizer relationship</li>
     *   <li>Should be tracked for trend analysis</li>
     * </ul>
     *
     * <h3>Journal Entry</h3>
     * <pre>
     * DR  Bad Debt Expense (5040)            K100  (P&L impact)
     * CR  Chargeback Recovery Receivable     K100  (clear receivable)
     * </pre>
     */
    WRITE_OFF;

    /**
     * Returns the priority of this fund source (1 = highest).
     *
     * <p>Lower priority sources should only be used when higher
     * priority sources are exhausted.</p>
     *
     * @return Priority number (1-4)
     */
    public int getPriority() {
        return switch (this) {
            case ORGANIZER_ESCROW -> 1;
            case ORGANIZER_FUTURE -> 2;
            case PLATFORM_RESERVE -> 3;
            case WRITE_OFF -> 4;
        };
    }

    /**
     * Checks if this source recovers funds from the organizer.
     *
     * <p>Organizer-sourced recovery doesn't impact platform P&L
     * (as per terms of service).</p>
     *
     * @return true if funds come from organizer
     */
    public boolean isOrganizerSourced() {
        return this == ORGANIZER_ESCROW || this == ORGANIZER_FUTURE;
    }

    /**
     * Checks if this source impacts platform P&L directly.
     *
     * @return true if this source reduces platform profit
     */
    public boolean impactsPlatformPnL() {
        return this == PLATFORM_RESERVE || this == WRITE_OFF;
    }

    /**
     * Checks if this is a true recovery (vs. expense recognition).
     *
     * <p>Write-off is not really "recovery" - it's acknowledging a loss.</p>
     *
     * @return true if this source provides actual fund recovery
     */
    public boolean isActualRecovery() {
        return this != WRITE_OFF;
    }

    /**
     * Returns the account code affected by this recovery source.
     *
     * @return Primary account code to debit for this source
     */
    public String getAccountCodeToDebit() {
        return switch (this) {
            case ORGANIZER_ESCROW -> "2010";  // Event Escrow (per-event)
            case ORGANIZER_FUTURE -> "2021";  // Payout Payable
            case PLATFORM_RESERVE -> "1012";  // Platform Reserve Bank Account
            case WRITE_OFF -> "5040";         // Bad Debt Expense
        };
    }

    /**
     * Returns the next source to try if this one is insufficient.
     *
     * @return Next fund source in waterfall, or null if last
     */
    public ChargebackFundSource getNextInWaterfall() {
        return switch (this) {
            case ORGANIZER_ESCROW -> ORGANIZER_FUTURE;
            case ORGANIZER_FUTURE -> PLATFORM_RESERVE;
            case PLATFORM_RESERVE -> WRITE_OFF;
            case WRITE_OFF -> null;  // End of waterfall
        };
    }
}
