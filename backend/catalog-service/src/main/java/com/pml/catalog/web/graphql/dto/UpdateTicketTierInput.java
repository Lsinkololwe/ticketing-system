package com.pml.catalog.web.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        BigDecimal originalPrice,
        Integer quantity,
        Integer maxPerOrder,
        Integer minPerOrder,
        List<String> benefits,
        Integer sortOrder,
        Boolean isActive,
        LocalDateTime salesStartAt,
        LocalDateTime salesEndAt,
        BigDecimal earlyBirdPrice,
        LocalDateTime earlyBirdEndsAt,
        Boolean isHidden,
        String accessCode
) {
}
