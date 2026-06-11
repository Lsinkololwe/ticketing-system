package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.organizer.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for organizer self-service dashboard functionality.
 *
 * <h2>Business Context</h2>
 * Provides aggregated statistics and data for the organizer dashboard,
 * finance overview, activity feed, and transaction history.
 *
 * <h2>Primary Users</h2>
 * <ul>
 *   <li><b>Organizers</b> - View their business metrics, events, and finances</li>
 * </ul>
 *
 * <h2>Data Sources</h2>
 * Aggregates data from:
 * <ul>
 *   <li>Tickets - Sales and check-in data</li>
 *   <li>Events (via catalog-service federation)</li>
 *   <li>Payout Requests - Pending and completed payouts</li>
 *   <li>Escrow Accounts - Balance information</li>
 *   <li>Journal Entries - Transaction history</li>
 * </ul>
 *
 * @author Booking Service Team
 * @since 1.0
 */
public interface OrganizerDashboardService {

    // ========================================================================
    // DASHBOARD STATISTICS
    // ========================================================================

    /**
     * Get main dashboard statistics for an organizer.
     * Aggregates revenue, ticket sales, active events, and attendee metrics.
     *
     * @param organizerId The organizer's user ID
     * @return Mono containing the dashboard statistics
     */
    Mono<OrganizerDashboardStats> getDashboardStats(String organizerId);

    // ========================================================================
    // FINANCE OVERVIEW
    // ========================================================================

    /**
     * Get finance overview for an organizer.
     * Includes balance breakdown, payout info, and revenue metrics.
     *
     * @param organizerId The organizer's user ID
     * @return Mono containing the finance overview
     */
    Mono<OrganizerFinanceOverview> getFinanceOverview(String organizerId);

    // ========================================================================
    // ACTIVITY FEED
    // ========================================================================

    /**
     * Get recent activity items for the organizer's dashboard feed.
     *
     * @param organizerId The organizer's user ID
     * @param limit Maximum number of items to return (default 10)
     * @return Flux of recent activity items
     */
    Flux<OrganizerActivityItem> getRecentActivity(String organizerId, Integer limit);

    // ========================================================================
    // UPCOMING EVENTS
    // ========================================================================

    /**
     * Get upcoming events with ticket sales progress.
     *
     * @param organizerId The organizer's user ID
     * @param limit Maximum number of events to return (default 5)
     * @return Flux of upcoming events with stats
     */
    Flux<OrganizerUpcomingEvent> getUpcomingEvents(String organizerId, Integer limit);

    // ========================================================================
    // TRANSACTION HISTORY
    // ========================================================================

    /**
     * Get paginated transaction history for the organizer.
     *
     * @param organizerId The organizer's user ID
     * @param filter Optional filter criteria
     * @param pagination Pagination parameters
     * @return Mono containing the paginated transactions
     */
    Mono<OrganizerTransactionOffsetPage> getTransactions(
            String organizerId,
            OrganizerTransactionFilterInput filter,
            OffsetPaginationInput pagination
    );
}
