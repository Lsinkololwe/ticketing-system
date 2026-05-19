package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.BalanceDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Journal Line - Individual Line Item in a Journal Entry
 *
 * A JournalLine represents a single debit or credit entry within a
 * {@link JournalEntry}. Each line affects exactly one account with
 * either a debit or credit amount (never both).
 *
 * <h2>The Fundamental Rule</h2>
 * <p><b>XOR Constraint:</b> A journal line MUST have exactly one of:</p>
 * <ul>
 *   <li>A positive debit amount (credit is zero/null), OR</li>
 *   <li>A positive credit amount (debit is zero/null)</li>
 * </ul>
 *
 * <p>Both cannot be positive, and both cannot be zero.</p>
 *
 * <h2>Example Journal Entry Structure</h2>
 * <pre>
 * JE-2024-01-00001 - Ticket Sale
 * ┌────────────────────────────────────────────────────────────────┐
 * │ Account Code │ Account Name              │ Debit  │ Credit   │
 * ├──────────────┼───────────────────────────┼────────┼──────────┤
 * │ 1021         │ Gateway Settlement Recv.  │ K100   │          │ ← Line 1
 * │ 2010-0001    │ Event Escrow              │        │ K90      │ ← Line 2
 * │ 4010         │ Commission Revenue        │        │ K10      │ ← Line 3
 * ├──────────────┼───────────────────────────┼────────┼──────────┤
 * │              │ TOTALS                    │ K100   │ K100     │ ✓ Balanced
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Reference Tracking</h2>
 * <p>Each line can reference the business entity it relates to:</p>
 * <ul>
 *   <li>referenceType = "TICKET" → referenceId = ticket ID</li>
 *   <li>referenceType = "PAYMENT" → referenceId = payment intent ID</li>
 *   <li>referenceType = "REFUND" → referenceId = refund request ID</li>
 *   <li>referenceType = "PAYOUT" → referenceId = payout request ID</li>
 *   <li>referenceType = "CHARGEBACK" → referenceId = chargeback ID</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>Validation is performed at multiple levels:</p>
 * <ol>
 *   <li>Line-level: XOR constraint (debit XOR credit)</li>
 *   <li>Entry-level: Total debits = total credits</li>
 *   <li>Account-level: Account code exists and is active</li>
 * </ol>
 *
 * @see JournalEntry
 * @see BalanceDirection
 * @since 1.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class JournalLine {

    /**
     * Account code from the Chart of Accounts.
     *
     * <p>This MUST match an existing, active account in the
     * chart_of_accounts collection.</p>
     *
     * <p>Examples: "1021", "2010-0001", "4010", "5020"</p>
     */
    @NotBlank(message = "Account code is required")
    private String accountCode;

    /**
     * Account name for human readability.
     *
     * <p>Denormalized from ChartOfAccountsEntry for convenient display
     * without joins. This is the account name at the time of posting.</p>
     */
    private String accountName;

    /**
     * Debit amount (left side of the entry).
     *
     * <p>XOR with credit: If debit > 0, credit must be 0 or null.</p>
     *
     * <p>Debits:</p>
     * <ul>
     *   <li>INCREASE Asset accounts</li>
     *   <li>INCREASE Expense accounts</li>
     *   <li>DECREASE Liability accounts</li>
     *   <li>DECREASE Equity accounts</li>
     *   <li>DECREASE Revenue accounts</li>
     * </ul>
     */
    @NotNull(message = "Debit amount is required")
    @PositiveOrZero(message = "Debit cannot be negative")
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    /**
     * Credit amount (right side of the entry).
     *
     * <p>XOR with debit: If credit > 0, debit must be 0 or null.</p>
     *
     * <p>Credits:</p>
     * <ul>
     *   <li>DECREASE Asset accounts</li>
     *   <li>DECREASE Expense accounts</li>
     *   <li>INCREASE Liability accounts</li>
     *   <li>INCREASE Equity accounts</li>
     *   <li>INCREASE Revenue accounts</li>
     * </ul>
     */
    @NotNull(message = "Credit amount is required")
    @PositiveOrZero(message = "Credit cannot be negative")
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    /**
     * Description of this specific line item.
     *
     * <p>Optional but recommended for audit clarity. Provides context
     * beyond the entry-level description.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Ticket sale - VIP section"</li>
     *   <li>"Platform commission at 10%"</li>
     *   <li>"Chargeback fee from gateway"</li>
     * </ul>
     */
    private String description;

    /**
     * Type of business entity this line references.
     *
     * <p>Common values:</p>
     * <ul>
     *   <li>TICKET - References a ticket purchase</li>
     *   <li>PAYMENT - References a payment intent</li>
     *   <li>REFUND - References a refund request</li>
     *   <li>PAYOUT - References a payout request</li>
     *   <li>CHARGEBACK - References a chargeback record</li>
     *   <li>EVENT - References an event</li>
     *   <li>COMMISSION - References a commission record</li>
     * </ul>
     */
    private String referenceType;

    /**
     * ID of the referenced business entity.
     *
     * <p>Used with referenceType to create a link back to the
     * originating business transaction for audit and drill-down.</p>
     */
    private String referenceId;

    // ========================================================================
    // VALIDATION METHODS
    // ========================================================================

    /**
     * Validates the XOR constraint: exactly one of debit or credit must be positive.
     *
     * <p>A valid journal line has:</p>
     * <ul>
     *   <li>debit > 0 AND credit == 0, OR</li>
     *   <li>debit == 0 AND credit > 0</li>
     * </ul>
     *
     * <p>Invalid cases:</p>
     * <ul>
     *   <li>debit > 0 AND credit > 0 (both positive)</li>
     *   <li>debit == 0 AND credit == 0 (both zero)</li>
     * </ul>
     *
     * @return true if exactly one amount is positive
     */
    public boolean isValid() {
        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;
        return hasDebit ^ hasCredit; // XOR: exactly one must be true
    }

    /**
     * Returns a detailed validation error message if the line is invalid.
     *
     * @return Error message, or null if valid
     */
    public String getValidationError() {
        if (accountCode == null || accountCode.isBlank()) {
            return "Account code is required";
        }

        boolean hasDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit != null && credit.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            return "Line cannot have both debit and credit amounts";
        }
        if (!hasDebit && !hasCredit) {
            return "Line must have either a debit or credit amount";
        }

        return null; // Valid
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if this is a debit line.
     *
     * @return true if debit is positive
     */
    public boolean isDebit() {
        return debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if this is a credit line.
     *
     * @return true if credit is positive
     */
    public boolean isCredit() {
        return credit != null && credit.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns the balance direction of this line.
     *
     * @return DEBIT if this is a debit line, CREDIT if this is a credit line
     * @throws IllegalStateException if neither debit nor credit is positive
     */
    public BalanceDirection getDirection() {
        if (isDebit()) {
            return BalanceDirection.DEBIT;
        }
        if (isCredit()) {
            return BalanceDirection.CREDIT;
        }
        throw new IllegalStateException("Journal line has no amount");
    }

    /**
     * Returns the effective amount (whichever is positive).
     *
     * @return The debit amount if positive, otherwise the credit amount
     */
    public BigDecimal getAmount() {
        if (isDebit()) {
            return debit;
        }
        if (isCredit()) {
            return credit;
        }
        return BigDecimal.ZERO;
    }

    /**
     * Returns the signed amount for balance calculations.
     *
     * <p>Convention: Debits are positive, Credits are negative.</p>
     *
     * @return Positive for debit, negative for credit
     */
    public BigDecimal getSignedAmount() {
        if (isDebit()) {
            return debit;
        }
        if (isCredit()) {
            return credit.negate();
        }
        return BigDecimal.ZERO;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a debit line.
     *
     * @param accountCode Account to debit
     * @param amount Debit amount (must be positive)
     * @param description Line description
     * @return New JournalLine with debit amount
     */
    public static JournalLine debit(String accountCode, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        return JournalLine.builder()
                .accountCode(accountCode)
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build();
    }

    /**
     * Creates a debit line with account name.
     *
     * @param accountCode Account to debit
     * @param accountName Human-readable account name
     * @param amount Debit amount (must be positive)
     * @param description Line description
     * @return New JournalLine with debit amount and account name
     */
    public static JournalLine debit(String accountCode, String accountName, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        return JournalLine.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .description(description)
                .build();
    }

    /**
     * Creates a credit line.
     *
     * @param accountCode Account to credit
     * @param amount Credit amount (must be positive)
     * @param description Line description
     * @return New JournalLine with credit amount
     */
    public static JournalLine credit(String accountCode, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        return JournalLine.builder()
                .accountCode(accountCode)
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
    }

    /**
     * Creates a credit line with account name.
     *
     * @param accountCode Account to credit
     * @param accountName Human-readable account name
     * @param amount Credit amount (must be positive)
     * @param description Line description
     * @return New JournalLine with credit amount and account name
     */
    public static JournalLine credit(String accountCode, String accountName, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        return JournalLine.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .debit(BigDecimal.ZERO)
                .credit(amount)
                .description(description)
                .build();
    }

    /**
     * Creates a debit line with reference tracking.
     *
     * @param accountCode Account to debit
     * @param amount Debit amount
     * @param description Line description
     * @param referenceType Type of business entity referenced
     * @param referenceId ID of the referenced entity
     * @return New JournalLine with debit and reference
     */
    public static JournalLine debitWithReference(
            String accountCode,
            BigDecimal amount,
            String description,
            String referenceType,
            String referenceId
    ) {
        JournalLine line = debit(accountCode, amount, description);
        line.setReferenceType(referenceType);
        line.setReferenceId(referenceId);
        return line;
    }

    /**
     * Creates a credit line with reference tracking.
     *
     * @param accountCode Account to credit
     * @param amount Credit amount
     * @param description Line description
     * @param referenceType Type of business entity referenced
     * @param referenceId ID of the referenced entity
     * @return New JournalLine with credit and reference
     */
    public static JournalLine creditWithReference(
            String accountCode,
            BigDecimal amount,
            String description,
            String referenceType,
            String referenceId
    ) {
        JournalLine line = credit(accountCode, amount, description);
        line.setReferenceType(referenceType);
        line.setReferenceId(referenceId);
        return line;
    }

    /**
     * Creates a reversed copy of this line (debit becomes credit, vice versa).
     *
     * <p>Used when creating reversal journal entries.</p>
     *
     * @return New JournalLine with opposite direction
     */
    public JournalLine reversed() {
        return JournalLine.builder()
                .accountCode(this.accountCode)
                .accountName(this.accountName)
                .debit(this.credit)  // Swap: credit → debit
                .credit(this.debit)  // Swap: debit → credit
                .description("Reversal: " + (this.description != null ? this.description : ""))
                .referenceType(this.referenceType)
                .referenceId(this.referenceId)
                .build();
    }
}
