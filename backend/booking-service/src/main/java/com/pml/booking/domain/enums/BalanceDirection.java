package com.pml.booking.domain.enums;

/**
 * Balance Direction Enum - Debit or Credit Side of a Journal Entry
 *
 * In double-entry bookkeeping, every transaction affects at least two accounts,
 * with debits always equaling credits. This enum represents the two sides of
 * any journal entry line.
 *
 * <h2>The Golden Rules of Double-Entry Accounting</h2>
 * <ol>
 *   <li>Every transaction must have at least one debit AND one credit</li>
 *   <li>Total debits must ALWAYS equal total credits</li>
 *   <li>Debits are recorded on the LEFT side of T-accounts</li>
 *   <li>Credits are recorded on the RIGHT side of T-accounts</li>
 * </ol>
 *
 * <h2>Effect on Account Types</h2>
 * <table border="1">
 *   <tr><th>Account Type</th><th>DEBIT Effect</th><th>CREDIT Effect</th></tr>
 *   <tr><td>ASSET</td><td>Increase ↑</td><td>Decrease ↓</td></tr>
 *   <tr><td>LIABILITY</td><td>Decrease ↓</td><td>Increase ↑</td></tr>
 *   <tr><td>EQUITY</td><td>Decrease ↓</td><td>Increase ↑</td></tr>
 *   <tr><td>REVENUE</td><td>Decrease ↓</td><td>Increase ↑</td></tr>
 *   <tr><td>EXPENSE</td><td>Increase ↑</td><td>Decrease ↓</td></tr>
 * </table>
 *
 * <h2>Example: Recording a Ticket Sale</h2>
 * <pre>
 * Journal Entry JE-2024-01-00001
 * Date: 2024-01-15
 * Description: Ticket sale - Event "Lusaka Music Festival"
 *
 * Account                           | DEBIT   | CREDIT
 * ----------------------------------|---------|--------
 * 1021 Gateway Settlement Receivable| K100.00 |
 * 2010 Event Escrow Payable         |         | K90.00
 * 4010 Commission Revenue           |         | K10.00
 * ----------------------------------|---------|--------
 * TOTALS                            | K100.00 | K100.00 ✓
 * </pre>
 *
 * @see AccountType#getNormalBalance()
 * @since 1.0.0
 */
public enum BalanceDirection {

    /**
     * Debit - The left side of a journal entry.
     *
     * <p>Effects:</p>
     * <ul>
     *   <li>INCREASES Asset accounts</li>
     *   <li>INCREASES Expense accounts</li>
     *   <li>DECREASES Liability accounts</li>
     *   <li>DECREASES Equity accounts</li>
     *   <li>DECREASES Revenue accounts</li>
     * </ul>
     *
     * <p>Common debit transactions in ticketing:</p>
     * <ul>
     *   <li>Receiving payment from gateway (increase cash/receivable)</li>
     *   <li>Processing a refund (decrease escrow liability)</li>
     *   <li>Recording chargeback loss (increase expense)</li>
     * </ul>
     */
    DEBIT,

    /**
     * Credit - The right side of a journal entry.
     *
     * <p>Effects:</p>
     * <ul>
     *   <li>DECREASES Asset accounts</li>
     *   <li>DECREASES Expense accounts</li>
     *   <li>INCREASES Liability accounts</li>
     *   <li>INCREASES Equity accounts</li>
     *   <li>INCREASES Revenue accounts</li>
     * </ul>
     *
     * <p>Common credit transactions in ticketing:</p>
     * <ul>
     *   <li>Recording escrow obligation (increase liability)</li>
     *   <li>Earning commission (increase revenue)</li>
     *   <li>Paying out to organizer (decrease cash)</li>
     * </ul>
     */
    CREDIT;

    /**
     * Returns the opposite direction.
     *
     * <p>Useful for creating reversal entries where all debits become credits
     * and all credits become debits.</p>
     *
     * @return CREDIT if this is DEBIT, DEBIT if this is CREDIT
     */
    public BalanceDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }

    /**
     * Determines the effect of this direction on a given account type.
     *
     * @param accountType The type of account being affected
     * @return positive 1 if this direction increases the account, negative -1 if it decreases
     */
    public int effectOn(AccountType accountType) {
        boolean isNormalBalance = accountType.getNormalBalance() == this;
        return isNormalBalance ? 1 : -1;
    }
}
