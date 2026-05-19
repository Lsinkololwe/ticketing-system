package com.pml.catalog.web.graphql.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Create Ticket Tier Input
 *
 * Input DTO for creating a new ticket pricing tier.
 */
public record CreateTicketTierInput(
        String code,
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        int quantity,
        Integer maxPerOrder,
        Integer minPerOrder,
        List<String> benefits,
        Integer sortOrder,
        LocalDateTime salesStartAt,
        LocalDateTime salesEndAt,
        BigDecimal earlyBirdPrice,
        LocalDateTime earlyBirdEndsAt,
        Boolean isHidden,
        String accessCode
) {
}
