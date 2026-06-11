package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction record for organizer's transaction history.
 *
 * Represents financial transactions including:
 * - Ticket sales
 * - Refunds
 * - Payouts
 * - Platform fees
 * - Adjustments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerTransaction {

    /**
     * Transaction ID
     */
    private String id;

    /**
     * Type of transaction
     */
    private OrganizerTransactionType type;

    /**
     * Human-readable description
     */
    private String description;

    /**
     * Transaction amount (positive for income, negative for deductions)
     */
    private BigDecimal amount;

    /**
     * Currency
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Transaction status (completed, pending, failed)
     */
    private String status;

    /**
     * When the transaction occurred
     */
    private LocalDateTime timestamp;

    /**
     * Associated event ID (if applicable)
     */
    private String eventId;

    /**
     * Associated event title (if applicable)
     */
    private String eventTitle;

    /**
     * Associated ticket ID (for ticket sales/refunds)
     */
    private String ticketId;

    /**
     * Associated payout request ID (for payouts)
     */
    private String payoutRequestId;

    /**
     * External reference number
     */
    private String reference;

    /**
     * Transaction type enum matching GraphQL schema
     */
    public enum OrganizerTransactionType {
        TICKET_SALE,
        REFUND,
        PAYOUT,
        PLATFORM_FEE,
        ADJUSTMENT
    }
}
