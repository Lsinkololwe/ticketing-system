package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventService;
import com.pml.catalog.util.CursorUtils;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GraphQL Query Resolver for Organizer Self-Service Event queries.
 *
 * All queries extract organizerId from JWT token (OWASP A01:2021 compliance).
 * Organizers can only see their own events through these queries.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizerEventQueryResolver {

    private final EventService eventService;

    /**
     * Get current organizer's events with offset pagination.
     *
     * @param filter Optional filter criteria
     * @param pagination Pagination parameters
     * @return Paginated events owned by the current organizer
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<EventOffsetPage> myEventsOffsetPagination(
            @InputArgument OrganizerEventFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myEventsOffsetPagination for organizer {}", organizerId);

                    Flux<Event> eventFlux = eventService.findEventsByOrganizer(organizerId);

                    // Apply filters if provided
                    if (filter != null) {
                        eventFlux = applyFilters(eventFlux, filter);
                    }

                    return buildOffsetPage(eventFlux,
                            pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
                });
    }

    /**
     * Get current organizer's draft events with offset pagination.
     *
     * @param pagination Pagination parameters
     * @return Paginated draft events owned by the current organizer
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<EventOffsetPage> myDraftEventsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myDraftEventsOffsetPagination for organizer {}", organizerId);

                    return buildOffsetPage(
                            eventService.findDraftEventsByOrganizer(organizerId),
                            pagination != null ? pagination : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC));
                });
    }

    /**
     * Get current organizer's total event count.
     *
     * @return Total number of events owned by the current organizer
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Integer> myEventCount() {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myEventCount for organizer {}", organizerId);
                    return eventService.countByOrganizer(organizerId).map(Long::intValue);
                });
    }

    /**
     * Get current organizer's event count by status.
     *
     * @param status Event status to filter by
     * @return Number of events with the specified status
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Integer> myEventCountByStatus(@InputArgument EventStatus status) {
        return getCurrentUserId()
                .flatMap(organizerId -> {
                    log.debug("GraphQL query: myEventCountByStatus for organizer {}, status {}", organizerId, status);
                    return eventService.findEventsByOrganizer(organizerId)
                            .filter(e -> e.getStatus() == status)
                            .count()
                            .map(Long::intValue);
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Extract user ID from JWT token in security context.
     */
    private Mono<String> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof Jwt jwt) {
                        String userId = jwt.getClaimAsString("sub");
                        if (userId != null && !userId.isBlank()) {
                            return Mono.just(userId);
                        }
                        String username = jwt.getClaimAsString("preferred_username");
                        if (username != null && !username.isBlank()) {
                            return Mono.just(username);
                        }
                    }
                    return Mono.error(new SecurityException("Unable to extract user ID from authentication"));
                })
                .switchIfEmpty(Mono.error(new SecurityException("No authentication found")));
    }

    /**
     * Apply filters to event flux.
     */
    private Flux<Event> applyFilters(Flux<Event> events, OrganizerEventFilterInput filter) {
        Flux<Event> filtered = events;

        if (filter.status() != null) {
            filtered = filtered.filter(e -> e.getStatus() == filter.status());
        }

        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            filtered = filtered.filter(e -> filter.statuses().contains(e.getStatus()));
        }

        if (filter.searchQuery() != null && !filter.searchQuery().isBlank()) {
            String query = filter.searchQuery().toLowerCase();
            filtered = filtered.filter(e ->
                    (e.getTitle() != null && e.getTitle().toLowerCase().contains(query)) ||
                    (e.getDescription() != null && e.getDescription().toLowerCase().contains(query)) ||
                    (e.getLocationName() != null && e.getLocationName().toLowerCase().contains(query))
            );
        }

        if (filter.eventDateAfter() != null) {
            filtered = filtered.filter(e ->
                    e.getEventDateTime() != null && e.getEventDateTime().isAfter(filter.eventDateAfter()));
        }

        if (filter.eventDateBefore() != null) {
            filtered = filtered.filter(e ->
                    e.getEventDateTime() != null && e.getEventDateTime().isBefore(filter.eventDateBefore()));
        }

        return filtered;
    }

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
     * Filter input record for organizer event queries.
     */
    public record OrganizerEventFilterInput(
            EventStatus status,
            List<EventStatus> statuses,
            String searchQuery,
            LocalDateTime eventDateAfter,
            LocalDateTime eventDateBefore
    ) {}
}
