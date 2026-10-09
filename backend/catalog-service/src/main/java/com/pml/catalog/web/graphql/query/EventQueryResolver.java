package com.pml.catalog.web.graphql.query;

import com.pml.catalog.service.EventDiscovery;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.constants.PlatformTime;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.web.graphql.dto.stats.CatalogPendingCounts;
import com.pml.catalog.web.graphql.dto.stats.EventStats;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventService;
import com.pml.catalog.service.EventStatsService;
import com.pml.catalog.service.PendingApprovalStatsService;
import com.pml.catalog.util.CursorUtils;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
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

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final EventStatsService eventStatsService;
    private final PendingApprovalStatsService pendingApprovalStatsService;
    private final EventDiscovery discovery;

    // ==========================================
    // Single Event Query
    // ==========================================

    /**
     * A single event, as this caller may see it.
     *
     * <p>Deliberately {@code findVisibleById} and not {@code findById}. The
     * schema calls this query PUBLIC and it has no {@code @auth} directive, so the
     * visibility filter every other public query carries has to live below it.
     */
    @DgsQuery
    public Mono<Event> event(@InputArgument String id) {
        log.debug("GraphQL query: event(id={})", id);
        Objects.requireNonNull(id, "Event ID is required");
        return eventService.findVisibleById(id);
    }

    // ==========================================
    // Event Discovery Query
    // ==========================================

    @DgsQuery
    public Mono<EventConnection> discoverEvents(
            @InputArgument EventDiscoveryFilterInput filter,
            @InputArgument CursorPaginationInput pagination,
            @InputArgument com.pml.catalog.domain.enums.EventDiscoverySort sort) {
        CursorPaginationInput page = pagination != null ? pagination : new CursorPaginationInput();
        if (page.isBackward()) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("pagination.before",
                    "the discovery feed pages forward only"))));
        }
        return discovery.find(filter, sort, page.getLimit(), page.getAfter()).map(EventQueryResolver::connection);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile/Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<EventConnection> searchEvents(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchEventsCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                eventService.searchEvents(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByCategory(
            @InputArgument String categoryId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCategoryCursorPagination(categoryId={})", categoryId);
        Objects.requireNonNull(categoryId, "Category ID is required");
        return buildCursorConnection(
                eventService.findEventsByCategory(categoryId),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventConnection> eventsByCity(
            @InputArgument String city,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventsByCityCursorPagination(city={})", city);
        Objects.requireNonNull(city, "City is required");
        return buildCursorConnection(
                eventService.findEventsByCity(city),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    // ==========================================
    // Admin Event Queries - Offset Pagination
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> events(
            @InputArgument EventFilterInput filter,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: events");
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
    @PreAuthorize("@organizationSecurityService.rolesOrTeamMember(authentication, 'ADMIN', #organizerId)")
    public Mono<EventOffsetPage> draftEvents(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: draftEvents(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");
        return buildOffsetPage(
                eventService.findDraftEventsByOrganizer(organizerId),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> pendingApprovalEvents(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalEvents");
        return buildOffsetPage(
                eventService.findPendingApprovalEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> overdueApprovalEvents(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalEvents");
        return buildOffsetPage(
                eventService.findOverdueApprovalEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> approvedNotPublishedEvents(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvedNotPublishedEvents");
        return buildOffsetPage(
                eventService.findApprovedNotPublishedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> eventsByStatus(
            @InputArgument EventStatus status,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventsByStatus(status={})", status);
        Objects.requireNonNull(status, "Status is required");
        return buildOffsetPage(
                eventService.findEventsByStatus(status),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> cancelledEvents(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: cancelledEvents");
        return buildOffsetPage(
                eventService.findCancelledEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventOffsetPage> completedEvents(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: completedEvents");
        return buildOffsetPage(
                eventService.findCompletedEvents(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
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
    @PreAuthorize("@organizationSecurityService.rolesOrTeamMember(authentication, 'ADMIN', #organizerId)")
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

    /**
     * Catalog-owned pending approval-queue count (events awaiting review) for
     * the admin action center.
     * Schema: catalogPendingCounts: CatalogPendingCounts
     *
     * One of three federated root fields the Apollo Router composes into the
     * frontend's single PendingCounts query.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CatalogPendingCounts> catalogPendingCounts() {
        log.debug("GraphQL query: catalogPendingCounts");
        return pendingApprovalStatsService.getPendingCounts();
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
    /** A discovery page as a Relay connection; each edge's cursor resumes the feed after it. */
    private static EventConnection connection(EventDiscovery.Page page) {
        if (page.events().isEmpty()) {
            return EventConnection.empty();
        }
        List<EventEdge> edges = new java.util.ArrayList<>();
        for (int i = 0; i < page.events().size(); i++) {
            edges.add(new EventEdge(EventDiscovery.cursor(page.start() + i + 1), page.events().get(i)));
        }
        EventConnection connection = new EventConnection();
        connection.setEdges(edges);
        connection.setPageInfo(PageInfo.builder()
                .hasNextPage(page.hasNext())
                .hasPreviousPage(page.start() > 0)
                .startCursor(edges.get(0).getCursor())
                .endCursor(edges.get(edges.size() - 1).getCursor())
                .build());
        return connection;
    }

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
     * Parse date string to Instant.
     * Supports ISO 8601 format (e.g., "2024-01-15T00:00:00")
     */
    private Instant parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return clock.instant();
        }

        try {
            // Try ISO_DATE_TIME first (e.g., "2024-01-15T10:30:00")
            return PlatformTime.parseLocal(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e1) {
            try {
                // Try ISO_LOCAL_DATE and add time (e.g., "2024-01-15")
                return PlatformTime.parseLocal(dateStr + "T00:00:00", DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Failed to parse date: {}", dateStr);
                return clock.instant();
            }
        }
    }
}
