package com.pml.booking.domain.enums;

/**
 * Chargeback Reason Enum - Reasons for Payment Chargebacks
 *
 * This enum categorizes the reasons why a customer or their bank initiates
 * a chargeback (forced refund) against a payment. Understanding the reason
 * is crucial for:
 * <ul>
 *   <li>Building an evidence package for disputes</li>
 *   <li>Identifying fraud patterns</li>
 *   <li>Improving event/ticket operations</li>
 * </ul>
 *
 * <h2>Chargeback vs. Refund</h2>
 * <table border="1">
 *   <tr><th>Aspect</th><th>Refund</th><th>Chargeback</th></tr>
 *   <tr><td>Initiated by</td><td>Merchant (us)</td><td>Customer via bank</td></tr>
 *   <tr><td>Control</td><td>Voluntary</td><td>Involuntary</td></tr>
 *   <tr><td>Fees</td><td>Usually none</td><td>$15-25 per case</td></tr>
 *   <tr><td>Timeline</td><td>Immediate</td><td>Can be disputed (45-90 days)</td></tr>
 *   <tr><td>Reputation</td><td>No impact</td><td>Affects merchant standing</td></tr>
 * </table>
 *
 * <h2>Mobile Money Chargebacks in Zambia</h2>
 * <p>Mobile money (MTN, Airtel, Zamtel) has different chargeback rules than cards:</p>
 * <ul>
 *   <li>Less common due to OTP verification</li>
 *   <li>Fraud cases may involve SIM swaps</li>
 *   <li>Resolution often involves mobile operator</li>
 * </ul>
 *
 * @see ChargebackStatus
 * @see ChargebackFundSource
 * @since 1.0.0
 */
public enum ChargebackReason {

    /**
     * Fraud - Unauthorized transaction claimed by the account holder.
     *
     * <p>The customer claims they did not authorize this payment.
     * This is one of the most serious and common chargeback reasons.</p>
     *
     * <h3>Common Scenarios</h3>
     * <ul>
     *   <li>Stolen card/phone used for purchase</li>
     *   <li>SIM swap fraud (mobile money)</li>
     *   <li>"Friendly fraud" - customer made purchase but denies it</li>
     *   <li>Family member made unauthorized purchase</li>
     * </ul>
     *
     * <h3>Evidence to Gather</h3>
     * <ul>
     *   <li>IP address and device fingerprint at purchase time</li>
     *   <li>OTP/verification records</li>
     *   <li>Ticket delivery confirmation (email, app notification)</li>
     *   <li>Event attendance records (if applicable)</li>
     *   <li>Prior purchase history from same account</li>
     * </ul>
     */
    FRAUD,

    /**
     * Not Received - Customer claims they never received the ticket/service.
     *
     * <p>The customer paid but claims they never got what they paid for.</p>
     *
     * <h3>Common Scenarios</h3>
     * <ul>
     *   <li>Email with ticket went to spam</li>
     *   <li>App notification was missed</li>
     *   <li>Event was cancelled but customer wasn't notified</li>
     *   <li>Customer couldn't access venue (entry denied)</li>
     * </ul>
     *
     * <h3>Evidence to Gather</h3>
     * <ul>
     *   <li>Email delivery logs (sent, opened)</li>
     *   <li>Push notification delivery confirmation</li>
     *   <li>Ticket download/view timestamps</li>
     *   <li>QR code scan records at venue</li>
     *   <li>Customer support communication logs</li>
     * </ul>
     */
    NOT_RECEIVED,

    /**
     * Not As Described - Product/service differed significantly from description.
     *
     * <p>The customer received the ticket but claims the event was materially
     * different from what was advertised.</p>
     *
     * <h3>Common Scenarios</h3>
     * <ul>
     *   <li>Headliner didn't perform (different artist)</li>
     *   <li>VIP section didn't exist or was inadequate</li>
     *   <li>Event time/date changed significantly</li>
     *   <li>Venue changed to inferior location</li>
     *   <li>Event was significantly shorter than advertised</li>
     * </ul>
     *
     * <h3>Evidence to Gather</h3>
     * <ul>
     *   <li>Original event listing at time of purchase</li>
     *   <li>Any change notifications sent to customer</li>
     *   <li>Refund policy shown at purchase time</li>
     *   <li>Photos/videos of actual event</li>
     *   <li>Social media posts about the event</li>
     * </ul>
     */
    NOT_AS_DESCRIBED,

    /**
     * Duplicate - Customer claims they were charged twice.
     *
     * <p>The customer claims they paid multiple times for the same purchase.</p>
     *
     * <h3>Common Scenarios</h3>
     * <ul>
     *   <li>Network timeout caused retry, both went through</li>
     *   <li>Customer clicked "Pay" multiple times</li>
     *   <li>Technical issue created duplicate payment records</li>
     *   <li>Customer purchased twice accidentally (different sessions)</li>
     * </ul>
     *
     * <h3>Evidence to Gather</h3>
     * <ul>
     *   <li>Timestamp comparison of both transactions</li>
     *   <li>Session/device information for both</li>
     *   <li>Number of tickets delivered vs. number of charges</li>
     *   <li>System logs showing retry behavior</li>
     * </ul>
     *
     * <p>Resolution: If duplicate is confirmed, proactively refund one charge
     * to avoid chargeback on the other.</p>
     */
    DUPLICATE,

    /**
     * Other - Reason not categorized above.
     *
     * <p>Catch-all for reasons that don't fit the standard categories.</p>
     *
     * <h3>Examples</h3>
     * <ul>
     *   <li>Customer claims they cancelled before event</li>
     *   <li>Bank error or system issue</li>
     *   <li>Dispute about terms and conditions</li>
     *   <li>Currency conversion disputes</li>
     * </ul>
     *
     * <p>When using OTHER, always document the specific reason in the
     * chargeback record's notes field.</p>
     */
    OTHER;

    /**
     * Returns the typical difficulty of winning a dispute for this reason.
     *
     * <p>Based on industry chargeback dispute statistics:</p>
     * <ul>
     *   <li>FRAUD: Hard to win unless strong evidence of customer action</li>
     *   <li>NOT_RECEIVED: Medium - delivery proof usually helps</li>
     *   <li>DUPLICATE: Easy - clear records show it's a mistake or legitimate</li>
     *   <li>NOT_AS_DESCRIBED: Hard - subjective customer expectations</li>
     * </ul>
     *
     * @return Difficulty level: EASY, MEDIUM, or HARD
     */
    public String getDisputeDifficulty() {
        return switch (this) {
            case FRAUD -> "HARD";
            case NOT_RECEIVED -> "MEDIUM";
            case NOT_AS_DESCRIBED -> "HARD";
            case DUPLICATE -> "EASY";
            case OTHER -> "VARIES";
        };
    }

    /**
     * Returns the average win rate for disputes of this type.
     *
     * <p>Industry averages for digital goods/services merchants.</p>
     *
     * @return Approximate win rate percentage (0-100)
     */
    public int getAverageWinRate() {
        return switch (this) {
            case FRAUD -> 25;           // Hard to prove, especially friendly fraud
            case NOT_RECEIVED -> 60;    // Good if we have delivery proof
            case NOT_AS_DESCRIBED -> 30; // Subjective, hard to prove
            case DUPLICATE -> 75;       // Easy to verify with records
            case OTHER -> 40;           // Highly variable
        };
    }

    /**
     * Returns the key evidence types needed to dispute this reason.
     *
     * @return Array of evidence types to gather
     */
    public String[] getRequiredEvidence() {
        return switch (this) {
            case FRAUD -> new String[]{
                    "IP address and device info",
                    "OTP verification records",
                    "Ticket delivery confirmation",
                    "Prior purchase history",
                    "Event attendance records"
            };
            case NOT_RECEIVED -> new String[]{
                    "Email delivery logs",
                    "Push notification confirmations",
                    "Ticket download timestamps",
                    "QR code scan records",
                    "Customer support logs"
            };
            case NOT_AS_DESCRIBED -> new String[]{
                    "Original event listing snapshot",
                    "Change notification records",
                    "Refund policy at purchase",
                    "Event photos/videos",
                    "Terms acceptance logs"
            };
            case DUPLICATE -> new String[]{
                    "Transaction timestamps",
                    "Session/device comparison",
                    "Number of tickets delivered",
                    "System retry logs"
            };
            case OTHER -> new String[]{
                    "Transaction details",
                    "Customer communication logs",
                    "Terms and conditions",
                    "Relevant policy documentation"
            };
        };
    }

    /**
     * Returns a human-readable display name for the chargeback reason.
     *
     * @return Display name suitable for UI and notifications
     */
    public String getDisplayName() {
        return switch (this) {
            case FRAUD -> "Fraud/Unauthorized Transaction";
            case NOT_RECEIVED -> "Product/Service Not Received";
            case NOT_AS_DESCRIBED -> "Not As Described";
            case DUPLICATE -> "Duplicate Transaction";
            case OTHER -> "Other";
        };
    }
}
