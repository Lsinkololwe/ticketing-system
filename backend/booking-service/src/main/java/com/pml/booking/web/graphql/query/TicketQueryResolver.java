package com.pml.booking.web.graphql.query;

import com.pml.booking.service.TicketSearch;
import org.springframework.data.mongodb.core.query.Criteria;
import com.pml.booking.security.TenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.TicketService;
import com.pml.booking.service.TicketStatsService;
import com.pml.booking.web.graphql.dto.*;
import com.pml.booking.web.graphql.dto.stats.TicketStats;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Ticket Operations.
 *
 * <p>Provides read-only queries for ticket data retrieval with both offset
 * and cursor-based pagination options for different client use cases:</p>
 * <ul>
 *   <li>Offset pagination: Admin tables, dashboards</li>
 *   <li>Cursor pagination: Mobile apps, infinite scroll</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: Uses OrganizationSecurityService for multi-tenant isolation</li>
 *   <li>Organization membership validated via Identity Service, not simple userId equality</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TicketQueryResolver {

    private final TicketService ticketService;
    private final TenantReads tenantReads;
    private final TicketStatsService ticketStatsService;
    private final TicketSearch ticketSearch;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a ticket by ID.
     * Schema: ticket(id: ID!): Ticket
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER', 'FINANCE') or @ticketSecurityService.isTicketOwner(#id, authentication)")
    public Mono<Ticket> ticket(@InputArgument String id) {
        log.debug("GraphQL query: ticket(id={})", id);
        Objects.requireNonNull(id, "Ticket ID is required");
        return tenantReads.ticketForCaller(id);
    }

    /**
     * Get a ticket by ticket number.
     * Schema: ticketByNumber(ticketNumber: String!): Ticket
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER', 'FINANCE') or @ticketSecurityService.isTicketOwnerByNumber(#ticketNumber, authentication)")
    public Mono<Ticket> ticketByNumber(@InputArgument String ticketNumber) {
        log.debug("GraphQL query: ticketByNumber({})", ticketNumber);
        Objects.requireNonNull(ticketNumber, "Ticket number is required");
        return tenantReads.ticketByNumberForCaller(ticketNumber);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get tickets by event with offset pagination.
     * Schema: ticketsByEvent(eventId: String!, pagination: OffsetPaginationInput): TicketOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<TicketOffsetPage> ticketsByEvent(
            @InputArgument String eventId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: ticketsByEvent(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");
        return buildOffsetPage(ticketService.findByEventId(eventId), pagination);
    }

    /**
     * Get tickets by buyer with offset pagination.
     * Schema: ticketsByBuyerOffsetPagination(buyerId: String!, status: TicketStatus, pagination: OffsetPaginationInput): TicketOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or #buyerId == authentication.principal.subject")
    public Mono<TicketOffsetPage> ticketsByBuyerOffsetPagination(
            @InputArgument String buyerId,
            @InputArgument TicketStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: ticketsByBuyerOffsetPagination(buyerId={}, status={})", buyerId, status);
        Objects.requireNonNull(buyerId, "Buyer ID is required");

        Flux<Ticket> ticketFlux = status != null
                ? ticketService.findByBuyerIdAndStatus(buyerId, status)
                : ticketService.findByBuyerId(buyerId);

        return buildOffsetPage(ticketFlux, pagination);
    }

    /**
     * Get tickets by organizer with offset pagination.
     * Schema: ticketsByOrganizer(organizerId: String!, filter: TicketFilterInput, pagination: OffsetPaginationInput): TicketOffsetPage!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("@organizationSecurityService.rolesOrTeamMember(authentication, 'ADMIN,FINANCE', #organizerId)")
    public Mono<TicketOffsetPage> ticketsByOrganizer(
            @InputArgument String organizerId,
            @InputArgument TicketFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: ticketsByOrganizer(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        // The organizer in the path is the scope; a filter naming another organizer narrows to nothing.
        return ticketSearch.page(Criteria.where("organizerId").is(organizerId), filter, pagination)
                .map(page -> new TicketOffsetPage(page.tickets(), page.pagination()));
    }

    /**
     * Search tickets with offset pagination.
     * Schema: searchTickets(filter: TicketFilterInput!, pagination: OffsetPaginationInput): TicketOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<TicketOffsetPage> searchTickets(
            @InputArgument TicketFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: searchTickets");
        Objects.requireNonNull(filter, "Filter is required for search");

        return ticketSearch.page(new Criteria(), filter, pagination)
                .map(page -> new TicketOffsetPage(page.tickets(), page.pagination()));
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get tickets by buyer with cursor pagination.
     * Schema: ticketsByBuyerCursorPagination(buyerId: String!, status: TicketStatus, pagination: CursorPaginationInput): TicketConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or #buyerId == authentication.principal.subject")
    public Mono<TicketConnection> ticketsByBuyerCursorPagination(
            @InputArgument String buyerId,
            @InputArgument TicketStatus status,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: ticketsByBuyerCursorPagination(buyerId={}, status={})", buyerId, status);
        Objects.requireNonNull(buyerId, "Buyer ID is required");

        Flux<Ticket> ticketFlux = status != null
                ? ticketService.findByBuyerIdAndStatus(buyerId, status)
                : ticketService.findByBuyerId(buyerId);

        return buildCursorConnection(ticketFlux, pagination);
    }

    // ========================================================================
    // COUNT QUERIES
    // ========================================================================

    /**
     * Get ticket count by event.
     * Schema: ticketCountByEvent(eventId: String!): Int!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<Integer> ticketCountByEvent(@InputArgument String eventId) {
        log.debug("GraphQL query: ticketCountByEvent({})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");
        return ticketService.countByEventId(eventId).map(Long::intValue);
    }

    /**
     * Get ticket count by buyer.
     * Schema: ticketCountByBuyer(buyerId: String!): Int!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or #buyerId == authentication.principal.subject")
    public Mono<Integer> ticketCountByBuyer(@InputArgument String buyerId) {
        log.debug("GraphQL query: ticketCountByBuyer({})", buyerId);
        Objects.requireNonNull(buyerId, "Buyer ID is required");
        return ticketService.countByBuyerId(buyerId).map(Long::intValue);
    }

    // ========================================================================
    // STATISTICS QUERIES
    // ========================================================================

    /**
     * Get ticket statistics with optional event filter.
     * Schema: ticketStats(eventId: ID): TicketStats!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or (@eventSecurityService.isEventOrganizer(#eventId, authentication) and #eventId != null)")
    public Mono<TicketStats> ticketStats(@InputArgument String eventId) {
        log.debug("GraphQL query: ticketStats(eventId={})", eventId);
        return eventId != null
                ? ticketStatsService.getTicketStatsByEvent(eventId)
                : ticketStatsService.getTicketStats();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build TicketOffsetPage from a Flux of tickets.
     */
    private Mono<TicketOffsetPage> buildOffsetPage(Flux<Ticket> ticketFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return ticketFlux.collectList()
                .map(allTickets -> {
                    int totalCount = allTickets.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<Ticket> paginatedTickets = allTickets.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PaginationInfo paginationInfo = new PaginationInfo(
                            totalCount,
                            limit,
                            p.page(),
                            totalPages,
                            hasNextPage,
                            hasPreviousPage
                    );

                    return new TicketOffsetPage(paginatedTickets, paginationInfo);
                });
    }

    /**
     * Build TicketConnection from a Flux of tickets.
     */
    private Mono<TicketConnection> buildCursorConnection(Flux<Ticket> ticketFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : new CursorPaginationInput(20, null, null, null);
        int limit = p.getLimit();

        return ticketFlux.collectList()
                .map(allTickets -> {
                    int totalCount = allTickets.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allTickets.size(); i++) {
                            if (allTickets.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of tickets
                    List<Ticket> pageTickets = allTickets.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageTickets.isEmpty()) {
                        return TicketConnection.empty();
                    }

                    // Build edges
                    List<TicketEdge> edges = pageTickets.stream()
                            .map(TicketEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.of(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new TicketConnection(edges, pageInfo, totalCount);
                });
    }
}
