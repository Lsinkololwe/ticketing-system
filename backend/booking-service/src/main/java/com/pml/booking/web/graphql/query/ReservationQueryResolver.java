package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Ticket Reservations.
 *
 * Provides read-only queries for reservation data with both offset
 * and cursor-based pagination options.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReservationQueryResolver {

    private final ReservationService reservationService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a reservation by ID.
     * Schema: reservation(id: ID!): TicketReservation
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> reservation(@InputArgument String id) {
        log.debug("GraphQL query: reservation(id={})", id);
        Objects.requireNonNull(id, "Reservation ID is required");
        return reservationService.findById(id);
    }

    /**
     * Get active reservations for a user.
     * Schema: myActiveReservations(userId: ID!): [TicketReservation!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated() and (#userId == authentication.principal.subject or hasAnyRole('ADMIN', 'FINANCE'))")
    public Flux<TicketReservation> myActiveReservations(@InputArgument String userId) {
        log.debug("GraphQL query: myActiveReservations(userId={})", userId);
        Objects.requireNonNull(userId, "User ID is required");
        return reservationService.findActiveByUserId(userId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get reservations by event with offset pagination.
     * Schema: reservationsByEventOffsetPagination(eventId: ID!, pagination: OffsetPaginationInput): ReservationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<ReservationOffsetPage> reservationsByEventOffsetPagination(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: reservationsByEventOffsetPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildOffsetPage(reservationService.findByEventId(eventId), pagination);
    }

    /**
     * Get expired reservations with offset pagination.
     * Schema: expiredReservationsOffsetPagination(eventId: ID, since: DateTime!, pagination: OffsetPaginationInput): ReservationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ReservationOffsetPage> expiredReservationsOffsetPagination(
            @InputArgument String eventId,
            @InputArgument OffsetDateTime since,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: expiredReservationsOffsetPagination(eventId={}, since={})", eventId, since);
        Objects.requireNonNull(since, "Since date is required");

        LocalDateTime sinceLocal = since.toLocalDateTime();
        Flux<TicketReservation> reservationFlux = eventId != null
                ? reservationService.findExpiredByEventId(eventId, sinceLocal)
                : reservationService.findExpiredSince(sinceLocal);

        return buildOffsetPage(reservationFlux, pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get reservations by event with cursor pagination.
     * Schema: reservationsByEventCursorPagination(eventId: ID!, pagination: CursorPaginationInput): ReservationConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<ReservationConnection> reservationsByEventCursorPagination(
            @InputArgument String eventId,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: reservationsByEventCursorPagination(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return buildCursorConnection(reservationService.findByEventId(eventId), pagination);
    }

    /**
     * Get expired reservations with cursor pagination.
     * Schema: expiredReservationsCursorPagination(eventId: ID, since: DateTime!, pagination: CursorPaginationInput): ReservationConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<ReservationConnection> expiredReservationsCursorPagination(
            @InputArgument String eventId,
            @InputArgument OffsetDateTime since,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: expiredReservationsCursorPagination(eventId={}, since={})", eventId, since);
        Objects.requireNonNull(since, "Since date is required");

        LocalDateTime sinceLocal = since.toLocalDateTime();
        Flux<TicketReservation> reservationFlux = eventId != null
                ? reservationService.findExpiredByEventId(eventId, sinceLocal)
                : reservationService.findExpiredSince(sinceLocal);

        return buildCursorConnection(reservationFlux, pagination);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<ReservationOffsetPage> buildOffsetPage(Flux<TicketReservation> reservationFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        return reservationFlux.collectList()
                .map(allReservations -> {
                    int totalCount = allReservations.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<TicketReservation> paginatedData = allReservations.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount, limit, p.page(), totalPages, hasNextPage, hasPreviousPage
                    );

                    return new ReservationOffsetPage(paginatedData, paginationInfo);
                });
    }

    private Mono<ReservationConnection> buildCursorConnection(Flux<TicketReservation> reservationFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : new CursorPaginationInput(20, null, null, null);
        int limit = p.getLimit();

        return reservationFlux.collectList()
                .map(allReservations -> {
                    int totalCount = allReservations.size();

                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allReservations.size(); i++) {
                            if (allReservations.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    List<TicketReservation> pageData = allReservations.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageData.isEmpty()) {
                        return ReservationConnection.empty();
                    }

                    List<ReservationEdge> edges = pageData.stream()
                            .map(ReservationEdge::of)
                            .toList();

                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.of(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new ReservationConnection(edges, pageInfo, totalCount);
                });
    }
}
