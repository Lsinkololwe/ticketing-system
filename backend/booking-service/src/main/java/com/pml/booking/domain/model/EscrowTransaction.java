package com.pml.booking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Escrow Transaction Record
 *
 * Business Intent: Represents a single financial transaction within an escrow account.
 * Each ticket sale credits the escrow, each refund or payout debits it. This provides
 * a complete audit trail of all money movements for regulatory compliance.
 *
 * Transaction Categories:
 * - TICKET_SALE: Credit from successful ticket purchase
 * - REFUND: Debit for customer refund
 * - PAYOUT: Debit for organizer payout
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowTransaction {

    /**
     * Unique identifier for this transaction.
     */
    private String id;

    /**
     * Transaction type: CREDIT (money in) or DEBIT (money out).
     */
    private TransactionType type;

    /**
     * Business category: TICKET_SALE, REFUND, or PAYOUT.
     */
    private String category;

    /**
     * Transaction amount (always positive).
     */
    private BigDecimal amount;

    /**
     * Escrow account balance after this transaction.
     */
    private BigDecimal balanceAfter;

    /**
     * Reference to the ticket for ticket sale/refund transactions.
     */
    private String ticketId;

    /**
     * Reference to the payment intent for ticket sales.
     */
    private String paymentIntentId;

    /**
     * Reference to the refund request for refund transactions.
     */
    private String refundRequestId;

    /**
     * Reference to the payout request for payout transactions.
     */
    private String payoutRequestId;

    /**
     * Human-readable description of the transaction.
     */
    private String description;

    /**
     * Timestamp when the transaction occurred.
     */
    private Instant timestamp;

    /**
     * Escrow transaction type.
     */
    public enum TransactionType {
        /**
         * Money credited to escrow (e.g., ticket sale).
         */
        CREDIT,

        /**
         * Money debited from escrow (e.g., refund, payout).
         */
        DEBIT
    }
}
