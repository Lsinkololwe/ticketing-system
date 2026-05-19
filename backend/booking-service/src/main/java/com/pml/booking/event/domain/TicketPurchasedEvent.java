package com.pml.booking.event.domain;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published after successful payment confirmation.
 *
 * Internal Listeners (@ApplicationModuleListener):
 * - EscrowEventListener: Credits escrow account with net amount
 * - CommissionEventListener: Records pending commission (not earned yet!)
 * - QRCodeEventListener: Generates QR code for ticket
 *
 * External Listeners (via Azure Service Bus):
 * - Catalog Service: Decrements inventory permanently
 * - Identity Service: Sends confirmation SMS/email/push
 *
 * Two-Stage Commission Model:
 * - Commission is recorded as PENDING at purchase time
 * - Commission becomes EARNED only after event + 7-day hold
 * - If refunded before event, commission is simply CANCELLED (no clawback needed)
 */
@Externalized("ticket-events::TicketPurchased")
public record TicketPurchasedEvent(
        String ticketId,
        String ticketNumber,
        String eventId,
        String eventTitle,
        String buyerId,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        String organizerId,
        String ticketCategoryCode,
        String ticketCategoryName,
        int quantity,
        BigDecimal ticketPrice,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal commissionRate,
        BigDecimal escrowAmount,
        String currency,
        String paymentProvider,
        String paymentReference,
        String transactionRef,
        Instant occurredAt
) {
    public TicketPurchasedEvent(
            String ticketId,
            String ticketNumber,
            String eventId,
            String eventTitle,
            String buyerId,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            String organizerId,
            String ticketCategoryCode,
            String ticketCategoryName,
            int quantity,
            BigDecimal ticketPrice,
            BigDecimal grossAmount,
            BigDecimal commissionAmount,
            BigDecimal commissionRate,
            BigDecimal escrowAmount,
            String currency,
            String paymentProvider,
            String paymentReference,
            String transactionRef
    ) {
        this(ticketId, ticketNumber, eventId, eventTitle, buyerId, buyerName,
                buyerEmail, buyerPhone, organizerId, ticketCategoryCode, ticketCategoryName,
                quantity, ticketPrice, grossAmount, commissionAmount, commissionRate,
                escrowAmount, currency, paymentProvider, paymentReference, transactionRef,
                Instant.now());
    }
}
