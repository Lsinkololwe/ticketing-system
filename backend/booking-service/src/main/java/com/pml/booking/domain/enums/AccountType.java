package com.pml.booking.domain.enums;

/**
 * Account Type Enum - Fundamental Classification for Double-Entry Bookkeeping
 *
 * This enum represents the five primary categories of accounts in the Chart of Accounts,
 * following the fundamental accounting equation: Assets = Liabilities + Equity
 *
 * <h2>Account Type Overview</h2>
 * <ul>
 *   <li><b>ASSET</b> - Resources owned by the company (e.g., cash, receivables)</li>
 *   <li><b>LIABILITY</b> - Obligations owed to others (e.g., escrow payable, refunds payable)</li>
 *   <li><b>EQUITY</b> - Owner's residual interest (e.g., retained earnings)</li>
 *   <li><b>REVENUE</b> - Income from operations (e.g., commission revenue)</li>
 *   <li><b>EXPENSE</b> - Costs of operations (e.g., payment gateway fees)</li>
 * </ul>
 *
 * <h2>Normal Balance (Debit vs Credit)</h2>
 * <p>Each account type has a "normal balance" - the side (debit or credit) that increases the account:</p>
 * <ul>
 *   <li><b>ASSET</b> - Normal balance is DEBIT (debits increase, credits decrease)</li>
 *   <li><b>EXPENSE</b> - Normal balance is DEBIT (debits increase, credits decrease)</li>
 *   <li><b>LIABILITY</b> - Normal balance is CREDIT (credits increase, debits decrease)</li>
 *   <li><b>EQUITY</b> - Normal balance is CREDIT (credits increase, debits decrease)</li>
 *   <li><b>REVENUE</b> - Normal balance is CREDIT (credits increase, debits decrease)</li>
 * </ul>
 *
 * <h2>Usage in Journal Entries</h2>
 * <p>When recording financial transactions:</p>
 * <pre>
 * Ticket Sale Example:
 *   DEBIT  1021 Gateway Settlement Receivable (ASSET ↑)     K100
 *   CREDIT 2010 Event Escrow Payable (LIABILITY ↑)          K90
 *   CREDIT 4010 Commission Revenue (REVENUE ↑)              K10
 * </pre>
 *
 * @see BalanceDirection
 * @see AccountSubType
 * @since 1.0.0
 */
public enum AccountType {

    /**
     * Assets - Resources owned by the company that have future economic value.
     *
     * <p>Examples in ticketing system:</p>
     * <ul>
     *   <li>Bank accounts (cash)</li>
     *   <li>Gateway settlement receivables (money owed by payment provider)</li>
     *   <li>Commission receivable (pending commissions)</li>
     *   <li>Chargeback recovery receivable</li>
     * </ul>
     *
     * <p>Normal balance: DEBIT (debits increase, credits decrease)</p>
     */
    ASSET,

    /**
     * Liabilities - Obligations owed to external parties.
     *
     * <p>Examples in ticketing system:</p>
     * <ul>
     *   <li>Event escrow payable (money owed to organizers)</li>
     *   <li>Customer refunds payable</li>
     *   <li>Tax withholding payable</li>
     *   <li>Deferred commission revenue (not yet earned)</li>
     * </ul>
     *
     * <p>Normal balance: CREDIT (credits increase, debits decrease)</p>
     */
    LIABILITY,

    /**
     * Equity - Owner's residual interest after liabilities are deducted from assets.
     *
     * <p>Examples in ticketing system:</p>
     * <ul>
     *   <li>Retained earnings (accumulated profits)</li>
     *   <li>Initial capital</li>
     * </ul>
     *
     * <p>Normal balance: CREDIT (credits increase, debits decrease)</p>
     */
    EQUITY,

    /**
     * Revenue - Income generated from business operations.
     *
     * <p>Examples in ticketing system:</p>
     * <ul>
     *   <li>Commission revenue (platform fee from ticket sales)</li>
     *   <li>Payout processing fee revenue</li>
     *   <li>Premium feature revenue</li>
     * </ul>
     *
     * <p>Normal balance: CREDIT (credits increase, debits decrease)</p>
     */
    REVENUE,

    /**
     * Expenses - Costs incurred in generating revenue.
     *
     * <p>Examples in ticketing system:</p>
     * <ul>
     *   <li>Payment gateway fees (PawaPay, etc.)</li>
     *   <li>Chargeback losses</li>
     *   <li>Bad debt expense (written-off chargebacks)</li>
     *   <li>Refund processing costs</li>
     * </ul>
     *
     * <p>Normal balance: DEBIT (debits increase, credits decrease)</p>
     */
    EXPENSE;

    /**
     * Returns the normal balance direction for this account type.
     *
     * <p>The normal balance is the side (debit or credit) that increases the account balance.</p>
     *
     * @return DEBIT for Asset/Expense accounts, CREDIT for Liability/Equity/Revenue accounts
     */
    public BalanceDirection getNormalBalance() {
        return switch (this) {
            case ASSET, EXPENSE -> BalanceDirection.DEBIT;
            case LIABILITY, EQUITY, REVENUE -> BalanceDirection.CREDIT;
        };
    }

    /**
     * Checks if this is a balance sheet account (permanent account).
     *
     * <p>Balance sheet accounts (Assets, Liabilities, Equity) carry forward their balances
     * from one accounting period to the next.</p>
     *
     * @return true if this is a balance sheet account type
     */
    public boolean isBalanceSheetAccount() {
        return this == ASSET || this == LIABILITY || this == EQUITY;
    }

    /**
     * Checks if this is an income statement account (temporary account).
     *
     * <p>Income statement accounts (Revenue, Expenses) are closed to retained earnings
     * at the end of each accounting period.</p>
     *
     * @return true if this is an income statement account type
     */
    public boolean isIncomeStatementAccount() {
        return this == REVENUE || this == EXPENSE;
    }
}
