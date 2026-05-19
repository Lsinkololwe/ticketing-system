package com.pml.catalog.web.graphql.dto.stats;

import com.pml.catalog.domain.model.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Main container for event statistics.
 * Matches the EventStats GraphQL type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStats {
    private int totalEvents;
    private int publishedEvents;
    private int approvedNotPublishedEvents;
    private int draftEvents;
    private int pendingApprovalEvents;
    private int cancelledEvents;
    private int completedEvents;
    private int rejectedEvents;
    private int totalCapacity;
    private int totalSoldTickets;
    private BigDecimal totalRevenue;
    private List<EventCategoryStats> eventsByCategory;
    private List<EventStatusStats> eventsByStatus;
    private List<EventOrganizerStats> eventsByOrganizer;
    private List<Event> recentEvents;
}
