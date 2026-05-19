package com.pml.booking.domain.enums;

/**
 * Account Sub-Type Enum - Detailed Classification for Chart of Accounts
 *
 * This enum provides granular categorization of accounts within the main
 * {@link AccountType} categories. Each sub-type belongs to exactly one
 * parent account type.
 *
 * <h2>Chart of Accounts Structure</h2>
 * <pre>
 * 1000 - ASSETS
 * ├── 1011 - Primary Operating Bank Account (BANK_ACCOUNT)
 * ├── 1012 - Escrow Bank Account (BANK_ACCOUNT)
 * ├── 1021 - Gateway Settlement Receivable (GATEWAY_RECEIVABLE)
 * ├── 1022 - Commission Receivable (COMMISSION_RECEIVABLE)
 * └── 1023 - Chargeback Recovery Receivable (CHARGEBACK_RECOVERY_RECEIVABLE)
 *
 * 2000 - LIABILITIES
 * ├── 2010 - Event Escrow Base (ESCROW_PAYABLE)
 * │   └── 2010-XXXX - Per-Event Escrow Accounts
 * ├── 2021 - Organizer Payouts Payable (PAYOUT_PAYABLE)
 * ├── 2022 - Customer Refunds Payable (REFUND_PAYABLE)
 * ├── 2023 - Tax Withholding Payable (TAX_PAYABLE)
 * └── 2031 - Deferred Commission Revenue (DEFERRED_REVENUE)
 *
 * 3000 - EQUITY
 * └── 3010 - Retained Earnings (RETAINED_EARNINGS)
 *
 * 4000 - REVENUE
 * ├── 4010 - Commission Revenue (COMMISSION_REVENUE)
 * └── 4020 - Payout Processing Fee Revenue (FEE_REVENUE)
 *
 * 5000 - EXPENSES
 * ├── 5010 - Payment Gateway Fees (GATEWAY_FEE_EXPENSE)
 * ├── 5020 - Chargeback Losses (CHARGEBACK_EXPENSE)
 * └── 5040 - Bad Debt Expense (BAD_DEBT_EXPENSE)
 * </pre>
 *
 * @see AccountType
 * @since 1.0.0
 */
public enum AccountSubType {

    // ========================================================================
    // ASSET SUB-TYPES (Normal Balance: DEBIT)
    // ========================================================================

    /**
     * Bank Account - Cash held in financial institutions.
     *
     * <p>Parent: {@link AccountType#ASSET}</p>
     * <p>Examples: Operating account, Escrow bank account</p>
     */
    BANK_ACCOUNT(AccountType.ASSET, "1010"),

    /**
     * Gateway Receivable - Money owed by payment gateway (e.g., PawaPay).
     *
     * <p>Parent: {@link AccountType#ASSET}</p>
     * <p>This represents funds collected but not yet settled to our bank.</p>
     */
    GATEWAY_RECEIVABLE(AccountType.ASSET, "1020"),

    /**
     * Commission Receivable - Pending commissions not yet earned.
     *
     * <p>Parent: {@link AccountType#ASSET}</p>
     * <p>Used in two-stage commission model before event completion.</p>
     */
    COMMISSION_RECEIVABLE(AccountType.ASSET, "1022"),

    /**
     * Chargeback Recovery Receivable - Amounts expected to be recovered from organizers.
     *
     * <p>Parent: {@link AccountType#ASSET}</p>
     * <p>Created when chargebacks are debited from organizer escrow/future payouts.</p>
     */
    CHARGEBACK_RECOVERY_RECEIVABLE(AccountType.ASSET, "1023"),

    /**
     * Chargeback Receivable - Alias for CHARGEBACK_RECOVERY_RECEIVABLE.
     *
     * <p>Parent: {@link AccountType#ASSET}</p>
     */
    CHARGEBACK_RECEIVABLE(AccountType.ASSET, "1023"),

    // ========================================================================
    // LIABILITY SUB-TYPES (Normal Balance: CREDIT)
    // ========================================================================

    /**
     * Escrow Payable - Funds held in escrow for organizers.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     * <p>This is the platform's obligation to pay organizers after events complete.</p>
     * <p>Dynamic sub-accounts created per event (e.g., 2010-0001, 2010-0002)</p>
     */
    ESCROW_PAYABLE(AccountType.LIABILITY, "2010"),

    /**
     * Payout Payable - Approved payouts pending disbursement.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     * <p>Created when payout is approved but not yet sent via mobile money.</p>
     */
    PAYOUT_PAYABLE(AccountType.LIABILITY, "2021"),

    /**
     * Refund Payable - Refunds approved but not yet processed.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     * <p>Created when refund is approved but awaiting gateway processing.</p>
     */
    REFUND_PAYABLE(AccountType.LIABILITY, "2022"),

    /**
     * Tax Payable - Taxes withheld pending remittance to authorities.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     * <p>For withholding tax on organizer payouts (if applicable).</p>
     */
    TAX_PAYABLE(AccountType.LIABILITY, "2023"),

    /**
     * Gateway Fees Payable - Payment gateway fees pending deduction.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     */
    FEES_PAYABLE(AccountType.LIABILITY, "2024"),

    /**
     * Deferred Revenue - Revenue received but not yet earned.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     * <p>Commission becomes deferred until event completes (two-stage model).</p>
     */
    DEFERRED_REVENUE(AccountType.LIABILITY, "2031"),

    /**
     * Payouts Payable - Organizer payouts approved but not yet disbursed.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     */
    PAYOUTS_PAYABLE(AccountType.LIABILITY, "2021"),

    /**
     * Refunds Payable - Customer refunds approved but not yet disbursed.
     *
     * <p>Parent: {@link AccountType#LIABILITY}</p>
     */
    REFUNDS_PAYABLE(AccountType.LIABILITY, "2022"),

    // ========================================================================
    // EQUITY SUB-TYPES (Normal Balance: CREDIT)
    // ========================================================================

    /**
     * Retained Earnings - Accumulated profits not distributed.
     *
     * <p>Parent: {@link AccountType#EQUITY}</p>
     * <p>Revenue - Expenses = Net Income → Retained Earnings</p>
     */
    RETAINED_EARNINGS(AccountType.EQUITY, "3010"),

    /**
     * Platform Reserve - Reserve fund for chargeback protection.
     *
     * <p>Parent: {@link AccountType#EQUITY}</p>
     */
    RESERVE(AccountType.EQUITY, "3020"),

    // ========================================================================
    // REVENUE SUB-TYPES (Normal Balance: CREDIT)
    // ========================================================================

    /**
     * Commission Revenue - Platform's earned commission from ticket sales.
     *
     * <p>Parent: {@link AccountType#REVENUE}</p>
     * <p>Recognized when: (1) Event completed OR (2) Post-event hold period passed</p>
     */
    COMMISSION_REVENUE(AccountType.REVENUE, "4010"),

    /**
     * Fee Revenue - Revenue from processing fees.
     *
     * <p>Parent: {@link AccountType#REVENUE}</p>
     * <p>Examples: Payout processing fee, premium feature fees</p>
     */
    FEE_REVENUE(AccountType.REVENUE, "4020"),

    /**
     * Other Income - Miscellaneous income items.
     *
     * <p>Parent: {@link AccountType#REVENUE}</p>
     * <p>Examples: Reconciliation variance gains, interest income</p>
     */
    OTHER_INCOME(AccountType.REVENUE, "4099"),

    // ========================================================================
    // EXPENSE SUB-TYPES (Normal Balance: DEBIT)
    // ========================================================================

    /**
     * Gateway Fee Expense - Fees paid to payment gateways.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     * <p>Examples: PawaPay transaction fees, mobile money fees</p>
     */
    GATEWAY_FEE_EXPENSE(AccountType.EXPENSE, "5010"),

    /**
     * Chargeback Expense - Losses from chargebacks.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     * <p>Includes chargeback amount + gateway chargeback fees ($15-25)</p>
     */
    CHARGEBACK_EXPENSE(AccountType.EXPENSE, "5020"),

    /**
     * Chargeback Loss - Unrecovered chargeback amounts.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     */
    CHARGEBACK_LOSS(AccountType.EXPENSE, "5020"),

    /**
     * Chargeback Fees - Fees charged by gateway per chargeback.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     */
    CHARGEBACK_FEES(AccountType.EXPENSE, "5030"),

    /**
     * Gateway Fees - Payment gateway processing fees.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     */
    GATEWAY_FEES(AccountType.EXPENSE, "5010"),

    /**
     * Bad Debt Expense - Written-off irrecoverable amounts.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     * <p>Used when chargebacks cannot be recovered from organizers.</p>
     */
    BAD_DEBT_EXPENSE(AccountType.EXPENSE, "5040"),

    /**
     * Bad Debt - Alias for BAD_DEBT_EXPENSE.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     */
    BAD_DEBT(AccountType.EXPENSE, "5040"),

    /**
     * Other Expense - Miscellaneous expense items.
     *
     * <p>Parent: {@link AccountType#EXPENSE}</p>
     * <p>Examples: Reconciliation variance losses, bank fees</p>
     */
    OTHER_EXPENSE(AccountType.EXPENSE, "5099");

    // ========================================================================
    // Instance Fields & Constructor
    // ========================================================================

    private final AccountType parentType;
    private final String defaultAccountCodePrefix;

    AccountSubType(AccountType parentType, String defaultAccountCodePrefix) {
        this.parentType = parentType;
        this.defaultAccountCodePrefix = defaultAccountCodePrefix;
    }

    /**
     * Returns the parent account type for this sub-type.
     *
     * @return The parent {@link AccountType}
     */
    public AccountType getParentType() {
        return parentType;
    }

    /**
     * Returns the default account code prefix for this sub-type.
     *
     * <p>This prefix is used when auto-generating account codes in the
     * Chart of Accounts.</p>
     *
     * @return Account code prefix (e.g., "1010" for BANK_ACCOUNT)
     */
    public String getDefaultAccountCodePrefix() {
        return defaultAccountCodePrefix;
    }

    /**
     * Returns the normal balance direction inherited from parent type.
     *
     * @return DEBIT for Asset/Expense sub-types, CREDIT for others
     */
    public BalanceDirection getNormalBalance() {
        return parentType.getNormalBalance();
    }
}
