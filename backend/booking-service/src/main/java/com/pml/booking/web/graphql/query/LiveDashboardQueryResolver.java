package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.LiveDashboardService;
import com.pml.booking.web.graphql.dto.LiveDashboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Live Dashboard
 *
 * Business Intent: Provides real-time check-in statistics for organizers
 * during active events. Used for event day management dashboards.
 *
 * Query Mappings (from schema):
 * - eventLiveDashboard(eventId): Real-time check-in dashboard for organizers
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class LiveDashboardQueryResolver {

    private final LiveDashboardService liveDashboardService;

    /**
     * Get live check-in dashboard for an event.
     *
     * Used by organizers on event day to monitor:
     * - Real-time check-in counts and rates
     * - Tier-level breakdown of arrivals
     * - Recent check-in feed
     * - Peak activity detection
     *
     * @param eventId Event ID
     * @return Live dashboard data
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<LiveDashboard> eventLiveDashboard(@InputArgument String eventId) {
        log.debug("GraphQL query: eventLiveDashboard for event: {}", eventId);
        return liveDashboardService.getEventLiveDashboard(eventId);
    }
}
