package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.OrganizerDashboardService;
import com.pml.booking.service.PayoutEligibilityService;
import com.pml.booking.web.graphql.dto.organizer.PayoutEligibilityResult;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.organizer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Organizer Dashboard.
 *
 * Provides self-service dashboard data for organizers:
 * - Dashboard statistics (revenue, tickets, events, attendees)
 * - Finance overview (balances, payouts, revenue breakdown)
 * - Recent activity feed
 * - Upcoming events
 * - Transaction history
 *
 * <h2>Security</h2>
 * All queries extract organizerId from JWT token (OWASP A01:2021 compliance).
 * Users can only access their own organization's data.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizerDashboardQueryResolver {

    private final OrganizerDashboardService organizerDashboardService;
    private final PayoutEligibilityService payoutEligibilityService;

    /**
     * Get main dashboard statistics for the current organizer.
     *
     * @return Dashboard statistics including revenue, tickets sold, events, and attendees
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerDashboardStats> myDashboardStats() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myDashboardStats for organizer {}", organizerId);
                    return organizerDashboardService.getDashboardStats(organizerId);
                });
    }

    /**
     * Get finance overview for the current organizer.
     *
     * @return Finance overview including balances, payouts, and revenue breakdown
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerFinanceOverview> myFinanceOverview() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myFinanceOverview for organizer {}", organizerId);
                    return organizerDashboardService.getFinanceOverview(organizerId);
                });
    }

    /**
     * Get recent activity for the current organizer's dashboard feed.
     *
     * @param limit Maximum number of activity items to return (default 10, max 50)
     * @return List of recent activity items
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Flux<OrganizerActivityItem> myRecentActivity(@InputArgument Integer limit) {
        return getCurrentUserId()
                .flatMapMany(organizerId -> {
                    log.debug("GraphQL query: myRecentActivity for organizer {}, limit {}", organizerId, limit);
                    return organizerDashboardService.getRecentActivity(organizerId, limit);
                });
    }

    /**
     * Get upcoming events for the current organizer's dashboard.
     *
     * @param limit Maximum number of events to return (default 5, max 20)
     * @return List of upcoming events with ticket sales progress
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Flux<OrganizerUpcomingEvent> myUpcomingEvents(@InputArgument Integer limit) {
        return getCurrentUserId()
                .flatMapMany(organizerId -> {
                    log.debug("GraphQL query: myUpcomingEvents for organizer {}, limit {}", organizerId, limit);
                    return organizerDashboardService.getUpcomingEvents(organizerId, limit);
                });
    }

    /**
     * Get paginated transaction history for the current organizer.
     *
     * @param filter Optional filter criteria (type, event, date range, amount)
     * @param pagination Pagination parameters (page, size, sort)
     * @return Paginated list of transactions
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerTransactionOffsetPage> myTransactionsOffsetPagination(
            @InputArgument OrganizerTransactionFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myTransactionsOffsetPagination for organizer {}", organizerId);
                    return organizerDashboardService.getTransactions(
                            organizerId,
                            filter != null ? filter : OrganizerTransactionFilterInput.empty(),
                            pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC)
                    );
                });
    }

    // ========================================================================
    // DASHBOARD ANALYTICS
    //
    // Back the dashboard's data-viz tiles. Contract:
    // frontend/web/docs/ORG_ADMIN_DASHBOARD_INFOGRAPHIC_SPEC.md
    // ========================================================================

    /**
     * Revenue per complete calendar month, oldest first.
     *
     * @param months Complete months to return (default 6, max 24)
     * @return Monthly revenue points, ascending by period
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Flux<OrganizerRevenuePoint> myRevenueSeries(@InputArgument Integer months) {
        return getCurrentUserId()
                .flatMapMany(organizerId -> {
                    log.debug("GraphQL query: myRevenueSeries for organizer {}, months {}", organizerId, months);
                    return organizerDashboardService.getRevenueSeries(organizerId, months);
                });
    }

    /**
     * Sold-ticket breakdown by tier, with the denominator the shares are
     * computed against.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerTicketMix> myTicketMix() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myTicketMix for organizer {}", organizerId);
                    return organizerDashboardService.getTicketMix(organizerId);
                });
    }

    /**
     * Gate attendance for the organizer's most recent event that has run.
     * Resolves to null when no event has run yet — the client renders an empty
     * state rather than a 0% rate, which would be a false finding.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerCheckInRate> myCheckInRate() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myCheckInRate for organizer {}", organizerId);
                    return organizerDashboardService.getCheckInRate(organizerId);
                });
    }

    /**
     * Withdrawable balance plus the escrow hold on the next tranche.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<OrganizerPayoutWindow> myPayoutWindow() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myPayoutWindow for organizer {}", organizerId);
                    return organizerDashboardService.getPayoutWindow(organizerId);
                });
    }

    /**
     * Escrow accounts this organizer can draw a payout from right now.
     *
     * <p>Every other escrow query is admin-scoped, so without this an organizer
     * has no way to obtain the {@code escrowAccountId} that
     * {@code createPayoutRequest} requires.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Flux<OrganizerPayoutSource> myPayoutSources() {
        return getCurrentUserId()
                .flatMapMany(organizerId -> {
                    log.debug("GraphQL query: myPayoutSources for organizer {}", organizerId);
                    return organizerDashboardService.getPayoutSources(organizerId);
                });
    }

    /**
     * Whether a payout can be requested for one event, and if not, why.
     *
     * <p>Complements {@code myPayoutSources}: that lists what is already
     * drawable, this answers for an event that is <em>not</em>, which is the
     * case an organizer actually asks about. "Why can't I withdraw yet" is not
     * answerable from a list that omits the event by construction.
     *
     * <p>The same evaluation runs again inside {@code createPayoutRequest}. This
     * one shapes the button; that one guards the money.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<PayoutEligibilityResult> payoutEligibility(@InputArgument String eventId) {
        return getCurrentUserId()
                .flatMap(organizerId -> payoutEligibilityService.evaluate(eventId, organizerId))
                .map(PayoutEligibilityResult::from);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Extract user ID from JWT token in security context.
     *
     * OWASP A01:2021 Compliance: Always use the authenticated user's ID
     * from the JWT rather than accepting it as a parameter.
     */
    private Mono<String> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt jwt) {
                        // Try 'sub' claim first (standard JWT claim)
                        String userId = jwt.getClaimAsString("sub");
                        if (userId != null && !userId.isBlank()) {
                            return Mono.just(userId);
                        }
                        // Fallback to 'preferred_username' for Keycloak
                        String username = jwt.getClaimAsString("preferred_username");
                        if (username != null && !username.isBlank()) {
                            return Mono.just(username);
                        }
                    }
                    return Mono.error(new SecurityException("Unable to extract user ID from authentication"));
                })
                .switchIfEmpty(Mono.error(new SecurityException("No authentication found")));
    }
}
