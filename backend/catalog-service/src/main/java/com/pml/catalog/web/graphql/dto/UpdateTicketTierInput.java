package com.pml.catalog.web.graphql.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Update Ticket Tier Input
 *
 * Input DTO for updating an existing ticket tier. All fields are nullable.
 */
public record UpdateTicketTierInput(
        String name,
        String description,
        BigDecimal price,
        Integer quantity,
        Integer maxPerOrder,
        Integer minPerOrder,
        List<String> benefits,
        Integer sortOrder,
        Boolean isActive,
        Instant salesStartAt,
        Instant salesEndAt,
        BigDecimal earlyBirdPrice,
        Instant earlyBirdEndsAt,
        Boolean isHidden,
        String accessCode,
        com.pml.shared.constants.TicketCategory category
) {
}
