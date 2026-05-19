package com.pml.booking.web.graphql.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Input DTO for ticket purchase GraphQL mutation.
 */
public record TicketPurchaseInput(
        String eventId,
        String ticketCategoryCode,
        Integer quantity,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        String paymentMethod,
        String paymentReference,
        String correlationId,
        BigDecimal amount,
        String currency,
        Map<String, Object> metadata
) {
    public TicketPurchaseInput {
        if (quantity == null) quantity = 1;
        if (currency == null) currency = "ZMW";
    }
}
