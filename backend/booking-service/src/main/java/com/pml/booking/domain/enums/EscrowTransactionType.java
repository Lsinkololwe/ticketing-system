package com.pml.booking.domain.enums;

/**
 * Escrow Transaction Type
 *
 * <p>Indicates the direction of fund movement in an escrow account.</p>
 *
 * <h2>Transaction Types</h2>
 * <ul>
 *   <li><b>CREDIT</b>: Funds flowing INTO the escrow (increases balance)</li>
 *   <li><b>DEBIT</b>: Funds flowing OUT of the escrow (decreases balance)</li>
 * </ul>
 *
 * <h2>Balance Impact</h2>
 * <pre>
 * New Balance = Current Balance + Credits - Debits
 * </pre>
 *
 * @see com.pml.booking.domain.model.StandaloneEscrowTransaction
 * @since 1.0.0
 */
public enum EscrowTransactionType {

    /**
     * Funds flowing into the escrow account.
     * Increases the escrow balance.
     *
     * Examples:
     * - Ticket sale proceeds (net of commission)
     * - Manual adjustment credits
     */
    CREDIT,

    /**
     * Funds flowing out of the escrow account.
     * Decreases the escrow balance.
     *
     * Examples:
     * - Refund disbursements
     * - Organizer payouts
     * - Chargeback recoveries
     */
    DEBIT;

    /**
     * Returns the opposite transaction type.
     *
     * @return DEBIT if this is CREDIT, CREDIT if this is DEBIT
     */
    public EscrowTransactionType opposite() {
        return this == CREDIT ? DEBIT : CREDIT;
    }

    /**
     * Calculates the signed amount for balance calculations.
     *
     * @param amount The absolute amount
     * @return Positive for CREDIT, negative for DEBIT
     */
    public java.math.BigDecimal signedAmount(java.math.BigDecimal amount) {
        return this == CREDIT ? amount : amount.negate();
    }
}
