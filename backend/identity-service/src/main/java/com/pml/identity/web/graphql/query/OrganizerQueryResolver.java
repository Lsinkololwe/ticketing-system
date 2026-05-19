package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.OrganizerStatus;
import com.pml.identity.domain.model.OrganizerProfile;
import com.pml.identity.service.OrganizerProfileService;
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
 * GraphQL Query Resolver for Organizer Profile operations.
 * Handles organizer-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizerQueryResolver {

    private final OrganizerProfileService organizerProfileService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get my organizer profile.
     * Schema: myOrganizerProfile: OrganizerProfile
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizerProfile> myOrganizerProfile(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.empty();
        }
        String userId = jwt.getSubject();
        log.debug("GraphQL query: myOrganizerProfile (userId={})", userId);
        return organizerProfileService.findByUserId(userId);
    }

    /**
     * Get organizer profile by ID.
     * Schema: organizerProfile(id: ID!): OrganizerProfile
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizerProfile> organizerProfile(@InputArgument String id) {
        log.debug("GraphQL query: organizerProfile(id={})", id);
        Objects.requireNonNull(id, "Organizer profile ID is required");
        return organizerProfileService.findById(id);
    }

    /**
     * Get organizer profile by user ID.
     * Schema: organizerProfileByUserId(userId: ID!): OrganizerProfile
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerProfile> organizerProfileByUserId(@InputArgument String userId) {
        log.debug("GraphQL query: organizerProfileByUserId(userId={})", userId);
        Objects.requireNonNull(userId, "User ID is required");
        return organizerProfileService.findByUserId(userId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get organizer applications with offset pagination (admin only).
     * Schema: organizerApplicationsOffsetPagination(status: OrganizerStatus, pagination: OffsetPaginationInput): OrganizerApplicationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerApplicationOffsetPage> organizerApplicationsOffsetPagination(
            @InputArgument OrganizerStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizerApplicationsOffsetPagination(status={})", status);

        Flux<OrganizerProfile> profileFlux = status != null
                ? organizerProfileService.findByStatus(status)
                : organizerProfileService.findAll();

        return buildOffsetPage(profileFlux, pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get organizer applications with cursor pagination (admin only, mobile/infinite scroll).
     * Schema: organizerApplicationsCursorPagination(status: OrganizerStatus, pagination: CursorPaginationInput): OrganizerApplicationConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizerApplicationConnection> organizerApplicationsCursorPagination(
            @InputArgument OrganizerStatus status,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizerApplicationsCursorPagination(status={})", status);

        Flux<OrganizerProfile> profileFlux = status != null
                ? organizerProfileService.findByStatus(status)
                : organizerProfileService.findAll();

        return buildCursorConnection(profileFlux, pagination);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build OrganizerApplicationOffsetPage from a Flux of profiles.
     */
    private Mono<OrganizerApplicationOffsetPage> buildOffsetPage(Flux<OrganizerProfile> profileFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return profileFlux.collectList()
                .map(allProfiles -> {
                    int totalCount = allProfiles.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<OrganizerProfile> paginatedProfiles = allProfiles.stream()
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

                    return new OrganizerApplicationOffsetPage(paginatedProfiles, pageInfo);
                });
    }

    /**
     * Build OrganizerApplicationConnection from a Flux of profiles.
     */
    private Mono<OrganizerApplicationConnection> buildCursorConnection(Flux<OrganizerProfile> profileFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return profileFlux.collectList()
                .map(allProfiles -> {
                    int totalCount = allProfiles.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allProfiles.size(); i++) {
                            if (allProfiles.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of profiles
                    List<OrganizerProfile> pageProfiles = allProfiles.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageProfiles.isEmpty()) {
                        return OrganizerApplicationConnection.empty();
                    }

                    // Build edges
                    List<OrganizerApplicationEdge> edges = pageProfiles.stream()
                            .map(OrganizerApplicationEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new OrganizerApplicationConnection(edges, pageInfo, totalCount);
                });
    }
}
