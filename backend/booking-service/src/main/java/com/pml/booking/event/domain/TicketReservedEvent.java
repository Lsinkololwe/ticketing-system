package com.pml.booking.event.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when tickets are reserved during checkout.
 * This is an internal event - not externalized to Service Bus.
 *
 * Internal Listeners:
 * - InventoryReservationListener: Decrements available quantity temporarily
 */
public record TicketReservedEvent(
        String ticketId,
        String eventId,
        String ticketCategoryCode,
        int quantity,
        BigDecimal amount,
        String buyerId,
        String sessionId,
        Instant expiresAt,
        Instant occurredAt
) {
    public TicketReservedEvent(
            String ticketId,
            String eventId,
            String ticketCategoryCode,
            int quantity,
            BigDecimal amount,
            String buyerId,
            String sessionId,
            Instant expiresAt
    ) {
        this(ticketId, eventId, ticketCategoryCode, quantity, amount,
                buyerId, sessionId, expiresAt, Instant.now());
    }
}
