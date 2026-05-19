package com.pml.catalog.web.graphql.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Statistics for events grouped by organizer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventOrganizerStats {
    private String organizerId;
    private String organizerName;
    private int eventCount;
    private int totalCapacity;
    private int totalSoldTickets;
    private BigDecimal totalRevenue;
}
