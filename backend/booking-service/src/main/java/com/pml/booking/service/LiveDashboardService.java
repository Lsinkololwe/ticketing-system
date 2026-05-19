package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.LiveDashboard;
import reactor.core.publisher.Mono;

/**
 * Live Dashboard Service Interface
 *
 * Business Intent: Provides real-time check-in statistics for organizer dashboards
 * during active events. Supports event day management and entry monitoring.
 *
 * Key Features:
 * - Real-time check-in counts and rates
 * - Tier-level breakdown of arrivals
 * - Recent check-in feed for live monitoring
 * - Peak activity detection
 */
public interface LiveDashboardService {

    /**
     * Get real-time dashboard statistics for an event.
     *
     * Uses MongoDB aggregation for optimal performance with large ticket counts.
     * Aggregates:
     * - Total sold tickets and check-in count
     * - Check-in rate calculations
     * - Tier-level breakdown
     * - Recent check-ins (last 10)
     * - Peak check-in time detection
     *
     * @param eventId Event ID
     * @return Live dashboard data
     * @throws IllegalArgumentException if event not found
     */
    Mono<LiveDashboard> getEventLiveDashboard(String eventId);
}
