package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
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
 * GraphQL Query Resolver for Team Invitation operations.
 * Handles invitation-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TeamInvitationQueryResolver {

    private final TeamInvitationService invitationService;
    private final OrganizationMemberService memberService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get invitation by token (for acceptance page).
     * Schema: invitationByToken(token: String!): TeamInvitation
     */
    @DgsQuery
    public Mono<TeamInvitation> invitationByToken(@InputArgument String token) {
        log.debug("GraphQL query: invitationByToken");
        Objects.requireNonNull(token, "Token is required");
        return invitationService.findByToken(token);
    }

    /**
     * Get my pending invitations.
     * Schema: myPendingInvitations: [TeamInvitation!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<TeamInvitation> myPendingInvitations(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.empty();
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return Flux.empty();
        }

        log.debug("GraphQL query: myPendingInvitations(email={})", email);
        return invitationService.findPendingByEmail(email);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get pending invitations for organization with offset pagination.
     * Schema: pendingInvitationsOffsetPagination(organizationId: ID!, pagination: OffsetPaginationInput): TeamInvitationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitationOffsetPage> pendingInvitationsOffsetPagination(
            @InputArgument String organizationId,
            @InputArgument OffsetPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(TeamInvitationOffsetPage.empty());
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: pendingInvitationsOffsetPagination(orgId={})", organizationId);
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return memberService.hasPermission(userId, organizationId, "ORG_VIEW_MEMBERS")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied"));
                    }
                    return buildOffsetPage(invitationService.findPendingByOrganization(organizationId), pagination);
                });
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get pending invitations with cursor pagination (mobile/infinite scroll).
     * Schema: pendingInvitationsCursorPagination(organizationId: ID!, pagination: CursorPaginationInput): TeamInvitationConnection!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitationConnection> pendingInvitationsCursorPagination(
            @InputArgument String organizationId,
            @InputArgument CursorPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(TeamInvitationConnection.empty());
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: pendingInvitationsCursorPagination(orgId={})", organizationId);
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return memberService.hasPermission(userId, organizationId, "ORG_VIEW_MEMBERS")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied"));
                    }
                    return buildCursorConnection(invitationService.findPendingByOrganization(organizationId), pagination);
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build TeamInvitationOffsetPage from a Flux of invitations.
     */
    private Mono<TeamInvitationOffsetPage> buildOffsetPage(Flux<TeamInvitation> invitationFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return invitationFlux.collectList()
                .map(allInvitations -> {
                    int totalCount = allInvitations.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<TeamInvitation> paginatedInvitations = allInvitations.stream()
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

                    return new TeamInvitationOffsetPage(paginatedInvitations, pageInfo);
                });
    }

    /**
     * Build TeamInvitationConnection from a Flux of invitations.
     */
    private Mono<TeamInvitationConnection> buildCursorConnection(Flux<TeamInvitation> invitationFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return invitationFlux.collectList()
                .map(allInvitations -> {
                    int totalCount = allInvitations.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allInvitations.size(); i++) {
                            if (allInvitations.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of invitations
                    List<TeamInvitation> pageInvitations = allInvitations.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageInvitations.isEmpty()) {
                        return TeamInvitationConnection.empty();
                    }

                    // Build edges
                    List<TeamInvitationEdge> edges = pageInvitations.stream()
                            .map(TeamInvitationEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new TeamInvitationConnection(edges, pageInfo, totalCount);
                });
    }
}
