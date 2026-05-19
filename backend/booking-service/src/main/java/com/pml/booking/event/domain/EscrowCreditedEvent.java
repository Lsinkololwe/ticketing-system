package com.pml.booking.event.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when funds are credited to an escrow account.
 * This is an internal event - not externalized.
 *
 * Used for ledger tracking and audit trail.
 */
public record EscrowCreditedEvent(
        String escrowAccountId,
        String eventId,
        String organizerId,
        String ticketId,
        String transactionRef,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String category,
        Instant occurredAt
) {
    public EscrowCreditedEvent(
            String escrowAccountId,
            String eventId,
            String organizerId,
            String ticketId,
            String transactionRef,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String currency,
            String category
    ) {
        this(escrowAccountId, eventId, organizerId, ticketId, transactionRef,
                amount, balanceAfter, currency, category, Instant.now());
    }
}
