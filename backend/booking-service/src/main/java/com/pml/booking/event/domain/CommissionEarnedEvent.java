package com.pml.booking.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when pending commission becomes earned revenue.
 * This happens 7 days after event completion.
 *
 * Internal Listeners:
 * - LedgerEventListener: Moves commission from PENDING to EARNED account
 *
 * External Listeners (via Azure Service Bus):
 * - Identity Service: Updates organizer dashboard metrics
 */
@Externalized("payment-events::CommissionEarned")
public record CommissionEarnedEvent(
        String eventId,
        String organizerId,
        String eventTitle,
        BigDecimal totalCommissionEarned,
        int ticketCount,
        BigDecimal grossRevenue,
        Instant occurredAt
) {
    public CommissionEarnedEvent(
            String eventId,
            String organizerId,
            String eventTitle,
            BigDecimal totalCommissionEarned,
            int ticketCount,
            BigDecimal grossRevenue
    ) {
        this(eventId, organizerId, eventTitle, totalCommissionEarned,
                ticketCount, grossRevenue, Instant.now());
    }
}
