package com.pml.identity.web.graphql.query;

import com.pml.identity.security.IdentityTenantReads;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.web.graphql.dto.pagination.*;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Event Access Grant operations.
 * Handles event access-related queries with offset pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventAccessQueryResolver {

    private final EventAccessService eventAccessService;
    private final IdentityTenantReads reads;

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
        return reads.grantForCaller(id);
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
        return reads.userGrantForCaller(userId, eventId);
    }

    /**
     * Get my access to a specific event.
     * Schema: myEventAccess(eventId: ID!): EventAccessGrant
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrant> myEventAccess(
            @InputArgument String eventId
    ) {
        Objects.requireNonNull(eventId, "Event ID is required");
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myEventAccess(eventId={}, userId={})", eventId, userId))
                .flatMap(userId -> eventAccessService.findByUserAndEvent(userId, eventId));
    }

    /**
     * Get all my event access grants.
     * Schema: myEventAccessGrants: [EventAccessGrant!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<EventAccessGrant> myEventAccessGrants() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myEventAccessGrants(userId={})", userId))
                .flatMapMany(eventAccessService::findByUser);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get event access grants for an event with offset pagination.
     * Schema: eventAccessGrants(eventId: ID!, status: AccessGrantStatus, pagination: OffsetPaginationInput): EventAccessGrantOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrantOffsetPage> eventAccessGrants(
            @InputArgument String eventId,
            @InputArgument AccessGrantStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        Objects.requireNonNull(eventId, "Event ID is required");
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: eventAccessGrants(eventId={}, status={})", eventId, status))
                .flatMap(userId -> {
                    Flux<EventAccessGrant> grantFlux = reads.grantsForEvent(eventId)
                            .filter(grant -> {
                                if (status != null && grant.getStatus() != status) {
                                    return false;
                                }
                                return true;
                            });

                    return buildOffsetPage(grantFlux, pagination);
                })
                .defaultIfEmpty(EventAccessGrantOffsetPage.empty());
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

}
