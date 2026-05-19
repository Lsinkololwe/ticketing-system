package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.web.graphql.dto.pagination.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Event Access Grant operations.
 * Handles event access-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventAccessQueryResolver {

    private final EventAccessService eventAccessService;
    private final OrganizationMemberService memberService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get an event access grant by ID.
     * Schema: eventAccessGrant(id: ID!): EventAccessGrant
     *
     * This query provides direct access to an EventAccessGrant by its ID.
     * It mirrors the entity fetcher pattern used by Apollo Router for federation.
     *
     * DUAL ENTRY PATTERN:
     * - EventAccessGrantEntityFetcher: eventAccessService.findById(id)
     * - This query resolver: eventAccessService.findById(id)
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> eventAccessGrant(@InputArgument String id) {
        log.debug("GraphQL query: eventAccessGrant(id={})", id);
        Objects.requireNonNull(id, "Event access grant ID is required");
        return eventAccessService.findById(id);
    }

    /**
     * Get user's access to a specific event.
     * Schema: userEventAccess(userId: ID!, eventId: ID!): EventAccessGrant
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> userEventAccess(
            @InputArgument String userId,
            @InputArgument String eventId
    ) {
        log.debug("GraphQL query: userEventAccess(userId={}, eventId={})", userId, eventId);
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(eventId, "Event ID is required");
        return eventAccessService.findByUserAndEvent(userId, eventId);
    }

    /**
     * Get my access to a specific event.
     * Schema: myEventAccess(eventId: ID!): EventAccessGrant
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> myEventAccess(
            @InputArgument String eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myEventAccess(eventId={}, userId={})", eventId, userId);
        Objects.requireNonNull(eventId, "Event ID is required");
        return eventAccessService.findByUserAndEvent(userId, eventId);
    }

    /**
     * Get all my event access grants.
     * Schema: myEventAccessGrants: [EventAccessGrant!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<EventAccessGrant> myEventAccessGrants(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myEventAccessGrants(userId={})", userId);
        return eventAccessService.findByUser(userId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get event access grants for an event with offset pagination.
     * Schema: eventAccessGrantsOffsetPagination(eventId: ID!, status: AccessGrantStatus, pagination: OffsetPaginationInput): EventAccessGrantOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrantOffsetPage> eventAccessGrantsOffsetPagination(
            @InputArgument String eventId,
            @InputArgument AccessGrantStatus status,
            @InputArgument OffsetPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(EventAccessGrantOffsetPage.empty());
        }

        log.debug("GraphQL query: eventAccessGrantsOffsetPagination(eventId={}, status={})", eventId, status);
        Objects.requireNonNull(eventId, "Event ID is required");

        Flux<EventAccessGrant> grantFlux = eventAccessService.findByEvent(eventId)
                .filter(grant -> {
                    if (status != null && grant.getStatus() != status) {
                        return false;
                    }
                    return true;
                });

        return buildOffsetPage(grantFlux, pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get event access grants with cursor pagination (mobile/infinite scroll).
     * Schema: eventAccessGrantsCursorPagination(eventId: ID!, status: AccessGrantStatus, pagination: CursorPaginationInput): EventAccessGrantConnection!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrantConnection> eventAccessGrantsCursorPagination(
            @InputArgument String eventId,
            @InputArgument AccessGrantStatus status,
            @InputArgument CursorPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(EventAccessGrantConnection.empty());
        }

        log.debug("GraphQL query: eventAccessGrantsCursorPagination(eventId={}, status={})", eventId, status);
        Objects.requireNonNull(eventId, "Event ID is required");

        Flux<EventAccessGrant> grantFlux = eventAccessService.findByEvent(eventId)
                .filter(grant -> {
                    if (status != null && grant.getStatus() != status) {
                        return false;
                    }
                    return true;
                });

        return buildCursorConnection(grantFlux, pagination);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build EventAccessGrantOffsetPage from a Flux of grants.
     */
    private Mono<EventAccessGrantOffsetPage> buildOffsetPage(Flux<EventAccessGrant> grantFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return grantFlux.collectList()
                .map(allGrants -> {
                    int totalCount = allGrants.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<EventAccessGrant> paginatedGrants = allGrants.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    PageInfo pageInfo = PageInfo.forOffset(
                            totalCount,
                            limit,
                            p.page(),
                            totalPages,
                            hasNextPage,
                            hasPreviousPage
                    );

                    return new EventAccessGrantOffsetPage(paginatedGrants, pageInfo);
                });
    }

    /**
     * Build EventAccessGrantConnection from a Flux of grants.
     */
    private Mono<EventAccessGrantConnection> buildCursorConnection(Flux<EventAccessGrant> grantFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return grantFlux.collectList()
                .map(allGrants -> {
                    int totalCount = allGrants.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allGrants.size(); i++) {
                            if (allGrants.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of grants
                    List<EventAccessGrant> pageGrants = allGrants.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageGrants.isEmpty()) {
                        return EventAccessGrantConnection.empty();
                    }

                    // Build edges
                    List<EventAccessGrantEdge> edges = pageGrants.stream()
                            .map(EventAccessGrantEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new EventAccessGrantConnection(edges, pageInfo, totalCount);
                });
    }
}
