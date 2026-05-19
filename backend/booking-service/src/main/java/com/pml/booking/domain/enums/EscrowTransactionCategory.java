package com.pml.booking.domain.enums;

/**
 * Escrow Transaction Category
 *
 * <p>Categorizes the business purpose of an escrow transaction.
 * Each category indicates the source or destination of funds.</p>
 *
 * <h2>Credit Categories (Funds In)</h2>
 * <ul>
 *   <li><b>TICKET_SALE</b>: Credit from a ticket purchase</li>
 * </ul>
 *
 * <h2>Debit Categories (Funds Out)</h2>
 * <ul>
 *   <li><b>REFUND</b>: Debit for customer refund</li>
 *   <li><b>PAYOUT</b>: Debit for organizer payout</li>
 *   <li><b>CHARGEBACK</b>: Debit for chargeback recovery</li>
 * </ul>
 *
 * <h2>Adjustment Categories</h2>
 * <ul>
 *   <li><b>ADJUSTMENT</b>: Manual adjustment (credit or debit)</li>
 *   <li><b>REVERSAL</b>: Reversal of a previous transaction</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.StandaloneEscrowTransaction
 * @since 1.0.0
 */
public enum EscrowTransactionCategory {

    // ==========================================
    // Credit Categories (Funds In)
    // ==========================================

    /**
     * Credit from a successful ticket sale.
     * Amount represents net proceeds after commission deduction.
     */
    TICKET_SALE("Ticket sale proceeds", EscrowTransactionType.CREDIT),

    // ==========================================
    // Debit Categories (Funds Out)
    // ==========================================

    /**
     * Debit for customer refund.
     * Full ticket price or partial refund amount.
     */
    REFUND("Customer refund", EscrowTransactionType.DEBIT),

    /**
     * Debit for organizer payout.
     * Funds transferred to organizer's bank account.
     */
    PAYOUT("Organizer payout", EscrowTransactionType.DEBIT),

    /**
     * Debit for chargeback recovery.
     * Funds recovered to cover a chargeback.
     */
    CHARGEBACK("Chargeback recovery", EscrowTransactionType.DEBIT),

    // ==========================================
    // Adjustment Categories
    // ==========================================

    /**
     * Manual adjustment entry.
     * Can be credit or debit, used for corrections.
     */
    ADJUSTMENT("Manual adjustment", null),

    /**
     * Reversal of a previous transaction.
     * Opposite type of the original transaction.
     */
    REVERSAL("Transaction reversal", null);

    private final String description;
    private final EscrowTransactionType defaultType;

    EscrowTransactionCategory(String description, EscrowTransactionType defaultType) {
        this.description = description;
        this.defaultType = defaultType;
    }

    /**
     * Gets a human-readable description of this category.
     *
     * @return Category description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the default transaction type for this category.
     *
     * @return Default type, or null if type varies (adjustments/reversals)
     */
    public EscrowTransactionType getDefaultType() {
        return defaultType;
    }

    /**
     * Checks if this category typically results in a credit.
     *
     * @return true if typically a credit
     */
    public boolean isTypicallyCredit() {
        return defaultType == EscrowTransactionType.CREDIT;
    }

    /**
     * Checks if this category typically results in a debit.
     *
     * @return true if typically a debit
     */
    public boolean isTypicallyDebit() {
        return defaultType == EscrowTransactionType.DEBIT;
    }

    /**
     * Checks if this is an adjustment category (type can vary).
     *
     * @return true if adjustment or reversal
     */
    public boolean isAdjustment() {
        return defaultType == null;
    }
}
