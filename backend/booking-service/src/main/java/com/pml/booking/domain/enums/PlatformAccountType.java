package com.pml.booking.domain.enums;

/**
 * Platform Account Type Enum - Types of Platform-Owned Accounts
 *
 * This enum defines the different types of accounts that belong to the
 * platform itself (not organizer escrow or customer funds). These accounts
 * track platform money for different operational purposes.
 *
 * <h2>Platform Account Overview</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │                        PLATFORM ACCOUNTS                             │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  OPERATING ACCOUNT                                          │    │
 * │  │  Purpose: Day-to-day operations                             │    │
 * │  │  Inflows:  Commission revenue, fees                         │    │
 * │  │  Outflows: Operating expenses, salaries, infrastructure     │    │
 * │  │  Balance:  Variable based on operations                     │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  RESERVE ACCOUNT                                            │    │
 * │  │  Purpose: Risk buffer, emergency fund                       │    │
 * │  │  Inflows:  Monthly allocation from profits                  │    │
 * │  │  Outflows: Chargeback coverage, dispute losses              │    │
 * │  │  Target:   Maintain minimum balance (e.g., 3 months ops)    │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  TAX HOLDING ACCOUNT                                        │    │
 * │  │  Purpose: Hold taxes until remittance                       │    │
 * │  │  Inflows:  Withholding tax from organizer payouts           │    │
 * │  │  Outflows: Tax payments to ZRA (Zambia Revenue Authority)   │    │
 * │  │  Balance:  Should match tax liability                       │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Distinction from Escrow</h2>
 * <p>Platform accounts hold <b>platform-owned money</b>, while escrow
 * accounts hold <b>organizer money</b>. This distinction is critical
 * for regulatory compliance and financial reporting.</p>
 *
 * @since 1.0.0
 */
public enum PlatformAccountType {

    /**
     * Operating Account - Primary account for day-to-day business.
     *
     * <p>This is the platform's main "bank account" for operational cash flow.</p>
     *
     * <h3>Typical Inflows</h3>
     * <ul>
     *   <li>Commission revenue from ticket sales (earned)</li>
     *   <li>Payout processing fees charged to organizers</li>
     *   <li>Premium feature subscriptions</li>
     *   <li>Interest income (if any)</li>
     * </ul>
     *
     * <h3>Typical Outflows</h3>
     * <ul>
     *   <li>Employee salaries and benefits</li>
     *   <li>Infrastructure costs (hosting, services)</li>
     *   <li>Marketing and customer acquisition</li>
     *   <li>Payment gateway fees</li>
     *   <li>Office and administrative expenses</li>
     * </ul>
     *
     * <h3>Management</h3>
     * <ul>
     *   <li>Monitor daily for cash flow management</li>
     *   <li>Transfer excess to reserve/investment accounts</li>
     *   <li>Ensure sufficient balance for operating expenses</li>
     * </ul>
     */
    OPERATING,

    /**
     * Reserve Account - Emergency fund and risk buffer.
     *
     * <p>This account provides financial stability and protects against
     * unexpected losses or market downturns.</p>
     *
     * <h3>Purpose</h3>
     * <ul>
     *   <li>Cover unexpected chargebacks and disputes</li>
     *   <li>Buffer against revenue fluctuations</li>
     *   <li>Fund emergency situations</li>
     *   <li>Maintain regulatory requirements (if any)</li>
     * </ul>
     *
     * <h3>Funding Strategy</h3>
     * <ul>
     *   <li>Allocate fixed percentage of monthly profit</li>
     *   <li>Build up to target balance (e.g., 3-6 months of operating expenses)</li>
     *   <li>Replenish after usage within defined timeline</li>
     * </ul>
     *
     * <h3>Usage Rules</h3>
     * <ul>
     *   <li>Only use for approved purposes (chargebacks, emergencies)</li>
     *   <li>Require management approval for large withdrawals</li>
     *   <li>Document all uses with journal entries</li>
     *   <li>Trigger alert when balance falls below threshold</li>
     * </ul>
     *
     * <h3>Chargeback Recovery</h3>
     * <p>Used as PLATFORM_RESERVE source in chargeback waterfall when
     * organizer funds are insufficient. See {@link ChargebackFundSource}.</p>
     */
    RESERVE,

    /**
     * Tax Holding Account - Holds taxes pending remittance to authorities.
     *
     * <p>When the platform withholds taxes from organizer payouts, those
     * funds are held in this account until paid to the tax authority.</p>
     *
     * <h3>Zambia Tax Context</h3>
     * <ul>
     *   <li><b>Withholding Tax (WHT)</b>: May apply to service payments</li>
     *   <li><b>VAT</b>: If platform is VAT-registered, collect on fees</li>
     *   <li><b>Remittance</b>: Usually monthly to ZRA</li>
     * </ul>
     *
     * <h3>Account Balance Reconciliation</h3>
     * <p>The balance in this account should always equal:</p>
     * <pre>
     * Tax Withheld From Organizers
     * - Tax Already Remitted to ZRA
     * = Current Balance
     * </pre>
     *
     * <h3>Journal Entry: Tax Withholding</h3>
     * <pre>
     * When withholding 15% WHT from K1000 payout:
     * DR  Event Escrow (2010-XXXX)           K1000  (reduce organizer liability)
     * CR  Tax Holding (2023)                  K150  (increase tax liability)
     * CR  Bank - Operating (1011)             K850  (record payout)
     * </pre>
     *
     * <h3>Journal Entry: Tax Remittance</h3>
     * <pre>
     * DR  Tax Holding (2023)                  K150  (reduce liability)
     * CR  Bank - Operating (1011)             K150  (pay ZRA)
     * </pre>
     *
     * <h3>Compliance</h3>
     * <ul>
     *   <li>Regular reconciliation to tax liability</li>
     *   <li>Timely remittance to avoid penalties</li>
     *   <li>Audit-ready records of all withholding</li>
     * </ul>
     */
    TAX_HOLDING;

    /**
     * Returns the default account code for this platform account type.
     *
     * <p>These codes align with the Chart of Accounts structure.</p>
     *
     * @return Account code in Chart of Accounts
     */
    public String getDefaultAccountCode() {
        return switch (this) {
            case OPERATING -> "1011";
            case RESERVE -> "1012";
            case TAX_HOLDING -> "2023";
        };
    }

    /**
     * Returns the accounting treatment for this account type.
     *
     * @return ASSET for bank accounts, LIABILITY for tax holding
     */
    public AccountType getAccountType() {
        return switch (this) {
            case OPERATING, RESERVE -> AccountType.ASSET;
            case TAX_HOLDING -> AccountType.LIABILITY;
        };
    }

    /**
     * Returns the minimum recommended balance for this account type.
     *
     * <p>These are policy recommendations, not hard limits.</p>
     *
     * @return Recommended minimum balance description
     */
    public String getMinimumBalancePolicy() {
        return switch (this) {
            case OPERATING -> "Cover 1-2 weeks of operating expenses";
            case RESERVE -> "Maintain 3-6 months of operating expenses";
            case TAX_HOLDING -> "Always equal to outstanding tax liability";
        };
    }

    /**
     * Checks if withdrawals from this account require approval.
     *
     * @return true if additional approval is needed for withdrawals
     */
    public boolean requiresWithdrawalApproval() {
        return switch (this) {
            case OPERATING -> false;     // Normal operational use
            case RESERVE -> true;        // Protect the emergency fund
            case TAX_HOLDING -> true;    // Only for tax remittance
        };
    }

    /**
     * Returns the alert threshold percentage.
     *
     * <p>When balance drops below this percentage of target, alert is triggered.</p>
     *
     * @return Alert threshold as percentage (0-100)
     */
    public int getAlertThresholdPercent() {
        return switch (this) {
            case OPERATING -> 25;   // Alert at 25% of normal balance
            case RESERVE -> 50;     // Alert at 50% of target reserve
            case TAX_HOLDING -> 0;  // Always should equal liability
        };
    }
}
