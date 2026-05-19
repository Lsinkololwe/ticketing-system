package com.pml.booking.domain.enums;

/**
 * Journal Entry Type Enum - Classification of Journal Entry Purpose
 *
 * This enum categorizes journal entries by their purpose in the accounting system.
 * Different types have different validation rules and audit requirements.
 *
 * <h2>Entry Type Hierarchy</h2>
 * <ul>
 *   <li><b>STANDARD</b> - Normal business transactions (ticket sales, payouts, etc.)</li>
 *   <li><b>ADJUSTMENT</b> - Corrections or period-end adjustments (reclassifications, accruals)</li>
 *   <li><b>REVERSAL</b> - Entries that completely reverse a previous entry (refunds, cancellations)</li>
 * </ul>
 *
 * <h2>Example Entries by Type</h2>
 *
 * <h3>STANDARD Entry - Ticket Sale</h3>
 * <pre>
 * JE-2024-01-00001 | 2024-01-15 | STANDARD
 * Description: Ticket sale - Event "Lusaka Jazz Night"
 *
 *   DR 1021 Gateway Receivable         K100.00
 *   CR 2010-0001 Event Escrow             K90.00
 *   CR 4010 Commission Revenue            K10.00
 * </pre>
 *
 * <h3>ADJUSTMENT Entry - Commission Reclassification</h3>
 * <pre>
 * JE-2024-01-00050 | 2024-01-31 | ADJUSTMENT
 * Description: Reclassify pending commission to earned (Event completed)
 *
 *   DR 2031 Deferred Commission Revenue  K500.00
 *   CR 4010 Commission Revenue           K500.00
 * </pre>
 *
 * <h3>REVERSAL Entry - Full Refund</h3>
 * <pre>
 * JE-2024-01-00075 | 2024-01-20 | REVERSAL
 * Description: Reversal of JE-2024-01-00001 - Full refund requested
 * Reverses: JE-2024-01-00001
 *
 *   CR 1021 Gateway Receivable         K100.00  (opposite of original)
 *   DR 2010-0001 Event Escrow             K90.00  (opposite of original)
 *   DR 4010 Commission Revenue            K10.00  (opposite of original)
 * </pre>
 *
 * @see JournalEntryStatus
 * @since 1.0.0
 */
public enum JournalEntryType {

    /**
     * Standard Entry - Normal business transaction.
     *
     * <p>Standard entries record everyday business operations:</p>
     * <ul>
     *   <li>Ticket sales</li>
     *   <li>Commission recognition</li>
     *   <li>Payout disbursements</li>
     *   <li>Fee collections</li>
     * </ul>
     *
     * <p>Validation: Must balance (debits = credits), requires description</p>
     */
    STANDARD,

    /**
     * Adjustment Entry - Correction or period-end adjustment.
     *
     * <p>Adjustment entries are used for:</p>
     * <ul>
     *   <li>Reclassifying accounts (e.g., pending → earned commission)</li>
     *   <li>Accruing expenses not yet billed</li>
     *   <li>Correcting errors in previous entries (without reversal)</li>
     *   <li>Period-end adjustments (depreciation, amortization)</li>
     * </ul>
     *
     * <p>Validation: Must balance, requires justification in description,
     * may require approval for large amounts</p>
     */
    ADJUSTMENT,

    /**
     * Reversal Entry - Complete reversal of a previous entry.
     *
     * <p>Reversal entries are created when:</p>
     * <ul>
     *   <li>Full refund is processed (reverses ticket sale entry)</li>
     *   <li>Chargeback received (reverses payment entry)</li>
     *   <li>Entry was posted in error (reverses incorrect entry)</li>
     * </ul>
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Every line is the OPPOSITE of the original entry</li>
     *   <li>MUST reference the original entry ID (reversedByEntryId)</li>
     *   <li>Original entry is marked as REVERSED</li>
     *   <li>Net effect of both entries = zero</li>
     * </ul>
     *
     * <p>Validation: Must reference valid original entry, amounts must match</p>
     */
    REVERSAL;

    /**
     * Checks if this entry type requires a reference to another entry.
     *
     * <p>Reversal entries MUST reference the original entry being reversed.</p>
     *
     * @return true if this type requires a reference entry
     */
    public boolean requiresReferenceEntry() {
        return this == REVERSAL;
    }

    /**
     * Checks if this entry type can modify account balances in normal direction.
     *
     * <p>Reversals are special - they always go opposite to normal direction.</p>
     *
     * @return true if this entry follows normal debit/credit rules
     */
    public boolean isNormalDirection() {
        return this != REVERSAL;
    }
}
