package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Standalone Escrow Transaction - Auditable Record of Escrow Money Movement
 *
 * This entity represents a single financial transaction within an escrow account.
 * Unlike the embedded {@link EscrowTransaction} inside {@link EventEscrowAccount},
 * this standalone version is stored in its own collection for:
 *
 * <ul>
 *   <li><b>Independent Querying</b>: Query transactions without loading entire escrow</li>
 *   <li><b>Reconciliation</b>: Compare transaction sums against stored balances</li>
 *   <li><b>Audit Trail</b>: Full history with immutable records</li>
 *   <li><b>Journal Entry Linking</b>: Connect to double-entry bookkeeping</li>
 *   <li><b>Performance</b>: Avoid document growth issues with embedded arrays</li>
 * </ul>
 *
 * <h2>Transaction Flow</h2>
 * <pre>
 * Ticket Sale:
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ 1. Customer pays K100 for ticket                           │
 *   │ 2. Platform takes K10 commission                           │
 *   │ 3. K90 credited to event escrow                            │
 *   │                                                             │
 *   │ Creates StandaloneEscrowTransaction:                        │
 *   │   type: CREDIT                                              │
 *   │   category: TICKET_SALE                                     │
 *   │   amount: K90                                               │
 *   │   ticketId: "ticket-123"                                    │
 *   │   paymentIntentId: "pay-456"                                │
 *   └─────────────────────────────────────────────────────────────┘
 *
 * Refund:
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ 1. Customer requests refund                                 │
 *   │ 2. K90 debited from event escrow                           │
 *   │ 3. Customer receives refund                                 │
 *   │                                                             │
 *   │ Creates StandaloneEscrowTransaction:                        │
 *   │   type: DEBIT                                               │
 *   │   category: REFUND                                          │
 *   │   amount: K90                                               │
 *   │   ticketId: "ticket-123"                                    │
 *   │   refundRequestId: "ref-789"                                │
 *   └─────────────────────────────────────────────────────────────┘
 *
 * Payout:
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ 1. Event completes + 7-day hold passes                     │
 *   │ 2. Organizer requests payout                                │
 *   │ 3. K500 debited from escrow → organizer wallet             │
 *   │                                                             │
 *   │ Creates StandaloneEscrowTransaction:                        │
 *   │   type: DEBIT                                               │
 *   │   category: PAYOUT                                          │
 *   │   amount: K500                                              │
 *   │   payoutRequestId: "out-012"                                │
 *   └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Reconciliation Usage</h2>
 * <p>Escrow balance reconciliation compares:</p>
 * <ul>
 *   <li>Stored: EventEscrowAccount.currentBalance</li>
 *   <li>Calculated: SUM(CREDIT transactions) - SUM(DEBIT transactions)</li>
 * </ul>
 *
 * <p>These should ALWAYS match. Any discrepancy indicates a bug or data corruption.</p>
 *
 * <h2>Relationship to Journal Entries</h2>
 * <p>Each escrow transaction is recorded in the double-entry ledger via journalEntryId.
 * This links the escrow-specific record to the full accounting entry.</p>
 *
 * @see EventEscrowAccount
 * @see EscrowTransaction (embedded version - deprecated for new code)
 * @see JournalEntry
 * @since 1.0.0
 */
@Document(collection = "escrow_transactions")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "escrow_type_idx", def = "{'escrowAccountId': 1, 'type': 1}"),
    @CompoundIndex(name = "escrow_category_idx", def = "{'escrowAccountId': 1, 'category': 1}"),
    @CompoundIndex(name = "escrow_timestamp_idx", def = "{'escrowAccountId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "ticket_idx", def = "{'ticketId': 1}"),
    @CompoundIndex(name = "payment_idx", def = "{'paymentIntentId': 1}")
})
public class StandaloneEscrowTransaction {

    /**
     * MongoDB document ID.
     * Auto-generated, used as primary key.
     */
    @Id
    private String id;

    /**
     * Reference to the parent escrow account.
     *
     * <p>This links the transaction to the EventEscrowAccount it belongs to.
     * All transactions for an account share this ID.</p>
     */
    @NotBlank(message = "Escrow account ID is required")
    @Indexed
    private String escrowAccountId;

    /**
     * Transaction type: CREDIT (money in) or DEBIT (money out).
     *
     * <ul>
     *   <li>CREDIT: Increases escrow balance (ticket sale)</li>
     *   <li>DEBIT: Decreases escrow balance (refund, payout)</li>
     * </ul>
     */
    @NotNull(message = "Transaction type is required")
    @Indexed
    private TransactionType type;

    /**
     * Business category describing the purpose of the transaction.
     *
     * <p>Standard categories:</p>
     * <ul>
     *   <li>TICKET_SALE - Credit from successful purchase</li>
     *   <li>REFUND - Debit for customer refund</li>
     *   <li>PAYOUT - Debit for organizer payout</li>
     *   <li>ADJUSTMENT - Manual correction (requires approval)</li>
     *   <li>CHARGEBACK - Debit for chargeback recovery</li>
     *   <li>FEE - Debit for processing fee (if applicable)</li>
     * </ul>
     */
    @NotBlank(message = "Category is required")
    @Indexed
    private String category;

    /**
     * Transaction amount (always positive).
     *
     * <p>The sign is determined by the type field (CREDIT/DEBIT), not the amount.</p>
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Escrow account balance after this transaction.
     *
     * <p>This running balance is calculated and stored at transaction time:</p>
     * <ul>
     *   <li>CREDIT: previousBalance + amount</li>
     *   <li>DEBIT: previousBalance - amount</li>
     * </ul>
     *
     * <p>Used for quick balance queries and reconciliation validation.</p>
     */
    @NotNull(message = "Balance after is required")
    private BigDecimal balanceAfter;

    /**
     * Currency code for this transaction.
     */
    @Builder.Default
    private String currency = "ZMW";

    // ========================================================================
    // REFERENCE FIELDS (Business Entity Links)
    // ========================================================================

    /**
     * Reference to the ticket for ticket sale/refund transactions.
     *
     * <p>Links to the Ticket document in the tickets collection.</p>
     */
    @Indexed
    private String ticketId;

    /**
     * Reference to the payment intent for ticket sales.
     *
     * <p>Links to the PaymentIntent document tracking the payment flow.</p>
     */
    @Indexed
    private String paymentIntentId;

    /**
     * Reference to the refund request for refund transactions.
     *
     * <p>Links to the RefundRequest document.</p>
     */
    @Indexed
    private String refundRequestId;

    /**
     * Reference to the payout request for payout transactions.
     *
     * <p>Links to the PayoutRequest document.</p>
     */
    @Indexed
    private String payoutRequestId;

    /**
     * Reference to the chargeback record (if applicable).
     */
    @Indexed
    private String chargebackId;

    // ========================================================================
    // DOUBLE-ENTRY BOOKKEEPING LINK
    // ========================================================================

    /**
     * Reference to the journal entry recording this transaction.
     *
     * <p>Links this escrow-specific record to the full double-entry
     * bookkeeping entry in the journal_entries collection.</p>
     *
     * <p>Example: A ticket sale creates:</p>
     * <ul>
     *   <li>This escrow transaction (K90 credit to escrow)</li>
     *   <li>A journal entry with lines for gateway receivable, escrow, and commission</li>
     * </ul>
     */
    @Indexed
    private String journalEntryId;

    // ========================================================================
    // DESCRIPTIVE FIELDS
    // ========================================================================

    /**
     * Human-readable description of the transaction.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Ticket sale - VIP Section - Order #12345"</li>
     *   <li>"Refund - Customer request - Ticket #67890"</li>
     *   <li>"Payout - Final settlement - Batch #111"</li>
     * </ul>
     */
    private String description;

    // ========================================================================
    // TIMESTAMPS & VERSIONING
    // ========================================================================

    /**
     * Timestamp when the transaction occurred.
     *
     * <p>This is the business timestamp of the transaction, which may differ
     * from createdAt if there's processing delay.</p>
     */
    @NotNull(message = "Timestamp is required")
    @Indexed
    private Instant timestamp;

    /**
     * Timestamp when the record was created in the database.
     * Auto-populated by Spring Data MongoDB.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Version for optimistic locking.
     *
     * <p>Escrow transactions should be immutable once created,
     * but version helps detect any unexpected modifications.</p>
     */
    @Version
    private Long version;

    // ========================================================================
    // TRANSACTION TYPE ENUM
    // ========================================================================

    /**
     * Escrow transaction type (direction of money flow).
     */
    public enum TransactionType {
        /**
         * Money credited to escrow (increases balance).
         *
         * <p>Examples: Ticket sale, manual adjustment (add funds)</p>
         */
        CREDIT,

        /**
         * Money debited from escrow (decreases balance).
         *
         * <p>Examples: Refund, payout, chargeback recovery</p>
         */
        DEBIT
    }

    // ========================================================================
    // CATEGORY CONSTANTS
    // ========================================================================

    /**
     * Standard transaction categories.
     * Using constants for type-safety and consistency.
     */
    public static final String CATEGORY_TICKET_SALE = "TICKET_SALE";
    public static final String CATEGORY_REFUND = "REFUND";
    public static final String CATEGORY_PAYOUT = "PAYOUT";
    public static final String CATEGORY_ADJUSTMENT = "ADJUSTMENT";
    public static final String CATEGORY_CHARGEBACK = "CHARGEBACK";
    public static final String CATEGORY_FEE = "FEE";

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if this is a credit transaction (money in).
     *
     * @return true if this increases the escrow balance
     */
    public boolean isCredit() {
        return type == TransactionType.CREDIT;
    }

    /**
     * Checks if this is a debit transaction (money out).
     *
     * @return true if this decreases the escrow balance
     */
    public boolean isDebit() {
        return type == TransactionType.DEBIT;
    }

    /**
     * Returns the signed amount for balance calculations.
     *
     * @return Positive for credit, negative for debit
     */
    public BigDecimal getSignedAmount() {
        return isCredit() ? amount : amount.negate();
    }

    /**
     * Checks if this transaction is for a ticket sale.
     *
     * @return true if category is TICKET_SALE
     */
    public boolean isTicketSale() {
        return CATEGORY_TICKET_SALE.equals(category);
    }

    /**
     * Checks if this transaction is for a refund.
     *
     * @return true if category is REFUND
     */
    public boolean isRefund() {
        return CATEGORY_REFUND.equals(category);
    }

    /**
     * Checks if this transaction is for a payout.
     *
     * @return true if category is PAYOUT
     */
    public boolean isPayout() {
        return CATEGORY_PAYOUT.equals(category);
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a ticket sale credit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to credit
     * @param balanceAfter Balance after this credit
     * @param ticketId The ticket ID
     * @param paymentIntentId The payment intent ID
     * @param description Transaction description
     * @return New credit transaction
     */
    public static StandaloneEscrowTransaction creditForTicketSale(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String ticketId,
            String paymentIntentId,
            String description
    ) {
        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.CREDIT)
                .category(CATEGORY_TICKET_SALE)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .ticketId(ticketId)
                .paymentIntentId(paymentIntentId)
                .description(description)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a refund debit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to debit
     * @param balanceAfter Balance after this debit
     * @param ticketId The ticket ID
     * @param refundRequestId The refund request ID
     * @param description Transaction description
     * @return New debit transaction
     */
    public static StandaloneEscrowTransaction debitForRefund(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String ticketId,
            String refundRequestId,
            String description
    ) {
        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.DEBIT)
                .category(CATEGORY_REFUND)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .ticketId(ticketId)
                .refundRequestId(refundRequestId)
                .description(description)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a payout debit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to debit
     * @param balanceAfter Balance after this debit
     * @param payoutRequestId The payout request ID
     * @param description Transaction description
     * @return New debit transaction
     */
    public static StandaloneEscrowTransaction debitForPayout(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String payoutRequestId,
            String description
    ) {
        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.DEBIT)
                .category(CATEGORY_PAYOUT)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .payoutRequestId(payoutRequestId)
                .description(description)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a chargeback debit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to debit
     * @param balanceAfter Balance after this debit
     * @param chargebackId The chargeback record ID
     * @param description Transaction description
     * @return New debit transaction
     */
    public static StandaloneEscrowTransaction debitForChargeback(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String chargebackId,
            String description
    ) {
        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.DEBIT)
                .category(CATEGORY_CHARGEBACK)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .chargebackId(chargebackId)
                .description(description)
                .timestamp(Instant.now())
                .build();
    }

    // ========================================================================
    // GENERIC FACTORY METHODS (for service layer)
    // ========================================================================

    /**
     * Creates a generic credit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to credit
     * @param balanceAfter Balance after this credit
     * @param category Transaction category
     * @param ticketId Optional ticket ID
     * @param paymentIntentId Optional payment intent ID
     * @param description Transaction description
     * @return New credit transaction
     */
    public static StandaloneEscrowTransaction credit(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            com.pml.booking.domain.enums.EscrowTransactionCategory category,
            String ticketId,
            String paymentIntentId,
            String description
    ) {
        return StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.CREDIT)
                .category(category.name())
                .amount(amount)
                .balanceAfter(balanceAfter)
                .ticketId(ticketId)
                .paymentIntentId(paymentIntentId)
                .description(description)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a generic debit transaction.
     *
     * @param escrowAccountId The escrow account ID
     * @param amount Amount to debit
     * @param balanceAfter Balance after this debit
     * @param category Transaction category
     * @param referenceId Reference ID (refund, payout, or chargeback)
     * @param description Transaction description
     * @return New debit transaction
     */
    public static StandaloneEscrowTransaction debit(
            String escrowAccountId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            com.pml.booking.domain.enums.EscrowTransactionCategory category,
            String referenceId,
            String description
    ) {
        StandaloneEscrowTransactionBuilder builder = StandaloneEscrowTransaction.builder()
                .escrowAccountId(escrowAccountId)
                .type(TransactionType.DEBIT)
                .category(category.name())
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description(description)
                .timestamp(Instant.now());

        // Set the appropriate reference field based on category
        switch (category) {
            case REFUND:
                builder.refundRequestId(referenceId);
                break;
            case PAYOUT:
                builder.payoutRequestId(referenceId);
                break;
            case CHARGEBACK:
                builder.chargebackId(referenceId);
                break;
            default:
                // For other categories, we don't set a specific reference field
                break;
        }

        return builder.build();
    }

    /**
     * Converts internal TransactionType to domain enum.
     *
     * @return Domain EscrowTransactionType enum
     */
    public com.pml.booking.domain.enums.EscrowTransactionType getTypeAsEnum() {
        return type == TransactionType.CREDIT
                ? com.pml.booking.domain.enums.EscrowTransactionType.CREDIT
                : com.pml.booking.domain.enums.EscrowTransactionType.DEBIT;
    }
}
