package com.pml.booking.domain.enums;

/**
 * Chargeback Status Enum - Lifecycle State of a Chargeback Case
 *
 * This enum tracks the progress of a chargeback from receipt through
 * final resolution. Each chargeback goes through multiple stages
 * that require different actions.
 *
 * <h2>Chargeback Lifecycle</h2>
 * <pre>
 *                    ┌──────────────┐
 *                    │   RECEIVED   │  Chargeback notification from gateway
 *                    └──────┬───────┘
 *                           │
 *                           ▼
 *                    ┌──────────────┐
 *                    │ UNDER_REVIEW │  Gathering evidence, assessing case
 *                    └──────┬───────┘
 *                           │
 *              ┌────────────┴────────────┐
 *              │                         │
 *              ▼                         ▼
 *       ┌──────────┐              ┌───────────┐
 *       │ ACCEPTED │              │ DISPUTED  │  Evidence submitted
 *       │(no fight)│              │           │  to gateway/bank
 *       └──────────┘              └─────┬─────┘
 *                                       │
 *                          ┌────────────┴────────────┐
 *                          │                         │
 *                          ▼                         ▼
 *                    ┌───────────┐            ┌───────────┐
 *                    │    WON    │            │   LOST    │
 *                    │(funds back)│            │(final loss)│
 *                    └───────────┘            └───────────┘
 * </pre>
 *
 * <h2>Timeline Constraints</h2>
 * <ul>
 *   <li>Response deadline: Usually 7-14 days from receipt</li>
 *   <li>Final resolution: Can take 45-90 days</li>
 *   <li>Second chargeback (pre-arbitration): Possible in some card networks</li>
 * </ul>
 *
 * @see ChargebackReason
 * @see RecoveryStatus
 * @since 1.0.0
 */
public enum ChargebackStatus {

    /**
     * Received - Chargeback notification received from payment gateway.
     *
     * <p>This is the initial state when a chargeback is first recorded.</p>
     *
     * <h3>Immediate Actions Required</h3>
     * <ul>
     *   <li>Log chargeback details (amount, reason, deadline)</li>
     *   <li>Freeze organizer escrow to prevent payout</li>
     *   <li>Begin evidence collection</li>
     *   <li>Notify relevant parties (organizer, operations team)</li>
     * </ul>
     *
     * <h3>Timeframe</h3>
     * <p>Must transition to UNDER_REVIEW or ACCEPTED within 24-48 hours.</p>
     */
    RECEIVED,

    /**
     * Under Review - Case is being investigated internally.
     *
     * <p>During this state, the team is gathering evidence and deciding
     * whether to accept the chargeback or dispute it.</p>
     *
     * <h3>Activities</h3>
     * <ul>
     *   <li>Review transaction details and customer history</li>
     *   <li>Gather evidence based on chargeback reason</li>
     *   <li>Assess likelihood of winning dispute</li>
     *   <li>Consider cost/benefit of disputing vs. accepting</li>
     *   <li>Contact organizer for additional information</li>
     * </ul>
     *
     * <h3>Decision Factors</h3>
     * <ul>
     *   <li>Strength of evidence available</li>
     *   <li>Chargeback amount vs. dispute cost</li>
     *   <li>Customer's prior dispute history</li>
     *   <li>Time remaining before deadline</li>
     * </ul>
     */
    UNDER_REVIEW,

    /**
     * Accepted - Decision made not to dispute the chargeback.
     *
     * <p>This is a terminal state (unless customer later reverses their claim).</p>
     *
     * <h3>Reasons for Accepting</h3>
     * <ul>
     *   <li>Chargeback is legitimate (our fault)</li>
     *   <li>Evidence is insufficient to win dispute</li>
     *   <li>Amount is too small to justify dispute effort</li>
     *   <li>Response deadline has passed</li>
     * </ul>
     *
     * <h3>Consequences</h3>
     * <ul>
     *   <li>Chargeback amount is final loss</li>
     *   <li>Chargeback fee is final loss</li>
     *   <li>Recovery process begins (from organizer)</li>
     *   <li>Affects merchant chargeback ratio</li>
     * </ul>
     */
    ACCEPTED,

    /**
     * Disputed - Evidence package submitted to challenge the chargeback.
     *
     * <p>We are contesting the chargeback with supporting evidence.</p>
     *
     * <h3>What Happens</h3>
     * <ul>
     *   <li>Evidence package submitted to gateway</li>
     *   <li>Gateway forwards to card network/bank</li>
     *   <li>Bank reviews evidence</li>
     *   <li>Decision made (WON or LOST)</li>
     * </ul>
     *
     * <h3>Typical Evidence Package</h3>
     * <ul>
     *   <li>Transaction details and authorization</li>
     *   <li>Customer verification records</li>
     *   <li>Delivery/usage proof</li>
     *   <li>Terms of service acceptance</li>
     *   <li>Prior communication with customer</li>
     *   <li>Merchant rebuttal letter</li>
     * </ul>
     *
     * <h3>Timeline</h3>
     * <p>Resolution typically takes 30-60 days from submission.</p>
     */
    DISPUTED,

    /**
     * Won - Dispute decided in our favor.
     *
     * <p>The bank/card network ruled that the chargeback was invalid.
     * This is a terminal state.</p>
     *
     * <h3>Outcome</h3>
     * <ul>
     *   <li>Original payment amount is restored to us</li>
     *   <li>Chargeback fee may or may not be refunded</li>
     *   <li>Customer cannot re-file same chargeback (usually)</li>
     *   <li>Does not count against chargeback ratio</li>
     * </ul>
     *
     * <h3>Post-Win Actions</h3>
     * <ul>
     *   <li>Release any frozen organizer escrow</li>
     *   <li>Update financial records</li>
     *   <li>Document case for future reference</li>
     * </ul>
     */
    WON,

    /**
     * Lost - Dispute decided against us.
     *
     * <p>The bank/card network ruled that the chargeback was valid.
     * This is a terminal state.</p>
     *
     * <h3>Outcome</h3>
     * <ul>
     *   <li>Chargeback amount is final loss</li>
     *   <li>Chargeback fee is final loss</li>
     *   <li>Counts against merchant chargeback ratio</li>
     *   <li>High ratio can lead to account termination</li>
     * </ul>
     *
     * <h3>Post-Loss Actions</h3>
     * <ul>
     *   <li>Execute recovery from organizer (if applicable)</li>
     *   <li>Record financial loss journal entry</li>
     *   <li>Analyze case for fraud pattern detection</li>
     *   <li>Update customer risk profile</li>
     * </ul>
     */
    LOST;

    /**
     * Checks if this status indicates the chargeback is still being processed.
     *
     * @return true if the chargeback is not yet resolved
     */
    public boolean isInProgress() {
        return this == RECEIVED || this == UNDER_REVIEW || this == DISPUTED;
    }

    /**
     * Checks if this status indicates a final resolution.
     *
     * @return true if the chargeback case is closed
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == WON || this == LOST;
    }

    /**
     * Checks if this status indicates a financial loss to the platform.
     *
     * <p>Both ACCEPTED and LOST result in losing the chargeback amount.</p>
     *
     * @return true if the chargeback amount is a confirmed loss
     */
    public boolean isLoss() {
        return this == ACCEPTED || this == LOST;
    }

    /**
     * Checks if this status indicates the dispute was won.
     *
     * @return true if funds were recovered
     */
    public boolean isWon() {
        return this == WON;
    }

    /**
     * Checks if evidence can still be submitted in this status.
     *
     * <p>Evidence can only be submitted during RECEIVED or UNDER_REVIEW.</p>
     *
     * @return true if evidence submission is still possible
     */
    public boolean canSubmitEvidence() {
        return this == RECEIVED || this == UNDER_REVIEW;
    }

    /**
     * Returns the recovery priority based on status.
     *
     * <p>Recovery should begin immediately after ACCEPTED or LOST.</p>
     *
     * @return Recovery priority: NONE, PENDING, or IMMEDIATE
     */
    public String getRecoveryPriority() {
        return switch (this) {
            case RECEIVED, UNDER_REVIEW, DISPUTED -> "PENDING";
            case ACCEPTED, LOST -> "IMMEDIATE";
            case WON -> "NONE";
        };
    }

    /**
     * Returns valid next statuses from the current status.
     *
     * @return Array of valid transition targets
     */
    public ChargebackStatus[] getValidTransitions() {
        return switch (this) {
            case RECEIVED -> new ChargebackStatus[]{UNDER_REVIEW, ACCEPTED};
            case UNDER_REVIEW -> new ChargebackStatus[]{ACCEPTED, DISPUTED};
            case DISPUTED -> new ChargebackStatus[]{WON, LOST};
            case ACCEPTED, WON, LOST -> new ChargebackStatus[]{}; // Terminal
        };
    }
}
