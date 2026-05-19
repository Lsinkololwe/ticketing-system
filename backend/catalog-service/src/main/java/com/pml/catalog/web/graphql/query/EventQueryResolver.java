package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.web.graphql.dto.stats.EventStats;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventService;
import com.pml.catalog.service.EventStatsService;
import com.pml.catalog.util.CursorUtils;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Event queries.
 * Implements both offset pagination (admin tables) and cursor pagination (mobile/infinite scroll).
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventQueryResolver {

    private final EventService eventService;
    private final EventStatsService eventStatsService;

    // ==========================================
    // Single Event Query
    // ==========================================

    @DgsQuery
    public Mono<Event> event(@InputArgument String id) {
        log.debug("GraphQL query: event(id={})", id);
        Objects.requireNonNull(id, "Event ID is required");
        return eventService.findById(id);
    }

    // ==========================================
    // Event Discovery Query
    // ==========================================

    @DgsQuery
    public Mono<EventConnection> discoverEvents(
            @InputArgument EventDiscoveryFilterInput filter,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: discoverEvents");
        Objects.requireNonNull(filter, "Filter is required");
        // For now, delegate to published events. In full implementation,
        // this would filter based on all criteria in the filter.
        return buildCursorConnection(
                eventService.findPublishedEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile/Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<EventConnection> publishedEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: publishedEventsCursorPagination");
        return buildCursorConnection(
                eventService.findPublishedEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> searchEventsCursorPagination(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchEventsCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                eventService.searchEvents(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> upcomingEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: upcomingEventsCursorPagination");
        return buildCursorConnection(
                eventService.findUpcomingEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByCategoryCursorPagination(
            @InputArgument String categoryId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCategoryCursorPagination(categoryId={})", categoryId);
        Objects.requireNonNull(categoryId, "Category ID is required");
        return buildCursorConnection(
                eventService.findEventsByCategory(categoryId),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByCityCursorPagination(
            @InputArgument String city,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCityCursorPagination(city={})", city);
        Objects.requireNonNull(city, "City is required");
        return buildCursorConnection(
                eventService.findEventsByCity(city),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByDateRangeCursorPagination(
            @InputArgument String startDate,
            @InputArgument String endDate,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByDateRangeCursorPagination(startDate={}, endDate={})", startDate, endDate);
        Objects.requireNonNull(startDate, "Start date is required");
        Objects.requireNonNull(endDate, "End date is required");
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        return buildCursorConnection(
                eventService.findEventsByDateRange(start, end),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByPriceRangeCursorPagination(
            @InputArgument BigDecimal minPrice,
            @InputArgument BigDecimal maxPrice,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByPriceRangeCursorPagination(minPrice={}, maxPrice={})", minPrice, maxPrice);
        return buildCursorConnection(
                eventService.findEventsByPriceRange(minPrice, maxPrice),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> featuredEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: featuredEventsCursorPagination");
        return buildCursorConnection(
                eventService.findFeaturedEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> freeEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: freeEventsCursorPagination");
        return buildCursorConnection(
                eventService.findFreeEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    /**
     * Get events by organizer with cursor pagination.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<EventConnection> eventsByOrganizerCursorPagination(
            @InputArgument String organizerId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByOrganizerCursorPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return buildCursorConnection(
                eventService.findEventsByOrganizer(organizerId),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    @DgsQuery
    public Mono<EventOffsetPage> publishedEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: publishedEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findPublishedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> searchEventsOffsetPagination(
            @InputArgument String query,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: searchEventsOffsetPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildOffsetPage(
                eventService.searchEvents(query),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> upcomingEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: upcomingEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findUpcomingEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> eventsByCategoryOffsetPagination(
            @InputArgument String categoryId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCategoryOffsetPagination(categoryId={})", categoryId);
        Objects.requireNonNull(categoryId, "Category ID is required");
        return buildOffsetPage(
                eventService.findEventsByCategory(categoryId),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> eventsByCityOffsetPagination(
            @InputArgument String city,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCityOffsetPagination(city={})", city);
        Objects.requireNonNull(city, "City is required");
        return buildOffsetPage(
                eventService.findEventsByCity(city),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> eventsByDateRangeOffsetPagination(
            @InputArgument String startDate,
            @InputArgument String endDate,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByDateRangeOffsetPagination(startDate={}, endDate={})", startDate, endDate);
        Objects.requireNonNull(startDate, "Start date is required");
        Objects.requireNonNull(endDate, "End date is required");
        LocalDateTime start = parseDateTime(startDate);
        LocalDateTime end = parseDateTime(endDate);
        return buildOffsetPage(
                eventService.findEventsByDateRange(start, end),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> eventsByPriceRangeOffsetPagination(
            @InputArgument BigDecimal minPrice,
            @InputArgument BigDecimal maxPrice,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByPriceRangeOffsetPagination(minPrice={}, maxPrice={})", minPrice, maxPrice);
        return buildOffsetPage(
                eventService.findEventsByPriceRange(minPrice, maxPrice),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> featuredEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: featuredEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findFeaturedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    public Mono<EventOffsetPage> freeEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: freeEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findFreeEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    /**
     * Get events by organizer with offset pagination.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<EventOffsetPage> eventsByOrganizerOffsetPagination(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByOrganizerOffsetPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return buildOffsetPage(
                eventService.findEventsByOrganizer(organizerId),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    // ==========================================
    // Admin Event Queries - Offset Pagination
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> eventsOffsetPagination(
            @InputArgument EventFilterInput filter,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsOffsetPagination");
        return buildOffsetPage(
                eventService.findAllEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    /**
     * Get draft events for an organizer with offset pagination.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<EventOffsetPage> draftEventsOffsetPagination(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: draftEventsOffsetPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return buildOffsetPage(
                eventService.findDraftEventsByOrganizer(organizerId),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> pendingApprovalEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findPendingApprovalEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> overdueApprovalEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findOverdueApprovalEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> approvedNotPublishedEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvedNotPublishedEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findApprovedNotPublishedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> eventsByStatusOffsetPagination(
            @InputArgument EventStatus status,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByStatusOffsetPagination(status={})", status);
        Objects.requireNonNull(status, "Status is required");
        return buildOffsetPage(
                eventService.findEventsByStatus(status),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> cancelledEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: cancelledEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findCancelledEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> completedEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: completedEventsOffsetPagination");
        return buildOffsetPage(
                eventService.findCompletedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    // ==========================================
    // Admin Event Queries - Cursor Pagination
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> eventsCursorPagination(
            @InputArgument EventFilterInput filter,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsCursorPagination");
        return buildCursorConnection(
                eventService.findAllEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    /**
     * Get draft events for an organizer with cursor pagination.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<EventConnection> draftEventsCursorPagination(
            @InputArgument String organizerId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: draftEventsCursorPagination(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return buildCursorConnection(
                eventService.findDraftEventsByOrganizer(organizerId),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> pendingApprovalEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalEventsCursorPagination");
        return buildCursorConnection(
                eventService.findPendingApprovalEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> overdueApprovalEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalEventsCursorPagination");
        return buildCursorConnection(
                eventService.findOverdueApprovalEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> approvedNotPublishedEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: approvedNotPublishedEventsCursorPagination");
        return buildCursorConnection(
                eventService.findApprovedNotPublishedEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> eventsByStatusCursorPagination(
            @InputArgument EventStatus status,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByStatusCursorPagination(status={})", status);
        Objects.requireNonNull(status, "Status is required");
        return buildCursorConnection(
                eventService.findEventsByStatus(status),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> cancelledEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: cancelledEventsCursorPagination");
        return buildCursorConnection(
                eventService.findCancelledEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventConnection> completedEventsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: completedEventsCursorPagination");
        return buildCursorConnection(
                eventService.findCompletedEvents(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Count Queries
    // ==========================================

    @DgsQuery
    public Mono<Integer> eventCount() {
        log.debug("GraphQL query: eventCount");
        return eventService.countAll().map(Long::intValue);
    }

    /**
     * Get event count by organizer.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Mono<Integer> eventCountByOrganizer(@InputArgument String organizerId) {
        log.debug("GraphQL query: eventCountByOrganizer(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return eventService.countByOrganizer(organizerId).map(Long::intValue);
    }

    @DgsQuery
    public Mono<Integer> eventCountByCategory(@InputArgument String categoryId) {
        log.debug("GraphQL query: eventCountByCategory(categoryId={})", categoryId);
        Objects.requireNonNull(categoryId, "Category ID is required");
        return eventService.countByCategory(categoryId).map(Long::intValue);
    }

    @DgsQuery
    public Mono<Integer> eventCountByCity(@InputArgument String city) {
        log.debug("GraphQL query: eventCountByCity(city={})", city);
        Objects.requireNonNull(city, "City is required");
        return eventService.countByCity(city).map(Long::intValue);
    }

    @DgsQuery
    public Mono<Integer> eventCountByStatus(@InputArgument EventStatus status) {
        log.debug("GraphQL query: eventCountByStatus(status={})", status);
        Objects.requireNonNull(status, "Status is required");
        return eventService.countByStatus(status).map(Long::intValue);
    }

    // ==========================================
    // Statistics Queries
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<com.pml.catalog.web.graphql.dto.stats.EventTicketStatistics> eventStatistics(
            @InputArgument String eventId) {
        log.debug("GraphQL query: eventStatistics(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");
        // This would typically be resolved by the Booking Service via federation.
        // For now, return null as a placeholder.
        return Mono.empty();
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<com.pml.catalog.web.graphql.dto.stats.TicketTierStats> ticketTierStatistics(
            @InputArgument String eventId,
            @InputArgument String tierId) {
        log.debug("GraphQL query: ticketTierStatistics(eventId={}, tierId={})", eventId, tierId);
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(tierId, "Tier ID is required");
        // This would typically be resolved by the Booking Service via federation.
        // For now, return null as a placeholder.
        return Mono.empty();
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventStats> eventStats() {
        log.debug("GraphQL query: eventStats");
        return eventStatsService.getEventStats();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Build EventOffsetPage from a Flux of events.
     */
    private Mono<EventOffsetPage> buildOffsetPage(Flux<Event> eventFlux, OffsetPaginationInput pagination) {
        int limit = pagination.getLimit();
        int offset = pagination.getOffset();

        return eventFlux.collectList()
                .map(allEvents -> {
                    int totalCount = allEvents.size();
                    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = pagination.page() > 0;

                    List<Event> paginatedEvents = allEvents.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    return new EventOffsetPage(
                            paginatedEvents,
                            pagination.page(),
                            limit,
                            totalCount,
                            totalPages,
                            hasNextPage,
                            hasPreviousPage
                    );
                });
    }

    /**
     * Build EventConnection from a Flux of events.
     */
    private Mono<EventConnection> buildCursorConnection(Flux<Event> eventFlux, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();

        return eventFlux.collectList()
                .map(allEvents -> {
                    int totalCount = allEvents.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (pagination.getAfter() != null && !pagination.getAfter().isBlank()) {
                        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
                        for (int i = 0; i < allEvents.size(); i++) {
                            if (allEvents.get(i).getId().equals(afterId)) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of events
                    List<Event> pageEvents = allEvents.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageEvents.isEmpty()) {
                        return EventConnection.empty();
                    }

                    // Build edges
                    List<EventEdge> edges = pageEvents.stream()
                            .map(EventEdge::from)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).getCursor();
                    String endCursor = edges.get(edges.size() - 1).getCursor();

                    PageInfo pageInfo = PageInfo.builder()
                            .hasNextPage(hasNextPage)
                            .hasPreviousPage(hasPreviousPage)
                            .startCursor(startCursor)
                            .endCursor(endCursor)
                            .build();

                    return EventConnection.builder()
                            .edges(edges)
                            .pageInfo(pageInfo)
                            .build();
                });
    }

    /**
     * Parse date string to LocalDateTime.
     * Supports ISO 8601 format (e.g., "2024-01-15T00:00:00")
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            // Try ISO_DATE_TIME first (e.g., "2024-01-15T10:30:00")
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e1) {
            try {
                // Try ISO_LOCAL_DATE and add time (e.g., "2024-01-15")
                return LocalDateTime.parse(dateStr + "T00:00:00", DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Failed to parse date: {}", dateStr);
                return LocalDateTime.now();
            }
        }
    }
}
