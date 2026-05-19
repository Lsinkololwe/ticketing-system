package com.pml.booking.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Statistics for tickets grouped by category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCategoryStats {
    private String category;
    private int count;
    private double percentage;
    private BigDecimal totalRevenue;
}
