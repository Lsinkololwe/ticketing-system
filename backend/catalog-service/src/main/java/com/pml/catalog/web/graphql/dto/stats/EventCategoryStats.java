package com.pml.catalog.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Statistics for events grouped by category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategoryStats {
    private String categoryId;
    private String category;
    private int count;
    private double percentage;
    private int totalCapacity;
    private int totalSoldTickets;
    private BigDecimal totalRevenue;
}
