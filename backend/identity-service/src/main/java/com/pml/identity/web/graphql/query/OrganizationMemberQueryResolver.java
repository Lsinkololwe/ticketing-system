package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
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
 * GraphQL Query Resolver for Organization Member operations.
 * Handles member-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationMemberQueryResolver {

    private final OrganizationMemberService memberService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get specific member by organization and user ID.
     * Schema: organizationMember(organizationId: ID!, userId: ID!): OrganizationMember
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> organizationMember(
            @InputArgument String organizationId,
            @InputArgument String userId
    ) {
        log.debug("GraphQL query: organizationMember(organizationId={}, userId={})", organizationId, userId);
        Objects.requireNonNull(organizationId, "Organization ID is required");
        Objects.requireNonNull(userId, "User ID is required");
        return memberService.findByUserAndOrganization(userId, organizationId);
    }

    /**
     * Get my membership in an organization.
     * Schema: myOrganizationMembership(organizationId: ID!): OrganizationMember
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> myOrganizationMembership(
            @InputArgument String organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.empty();
        }
        String userId = jwt.getSubject();
        log.debug("GraphQL query: myOrganizationMembership(orgId={}, userId={})", organizationId, userId);
        Objects.requireNonNull(organizationId, "Organization ID is required");
        return memberService.findByUserAndOrganization(userId, organizationId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get organization members with offset pagination.
     * Schema: organizationMembersOffsetPagination(organizationId: ID!, role: OrganizationRole, status: MemberStatus, pagination: OffsetPaginationInput): OrganizationMemberOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMemberOffsetPage> organizationMembersOffsetPagination(
            @InputArgument String organizationId,
            @InputArgument OrganizationRole role,
            @InputArgument MemberStatus status,
            @InputArgument OffsetPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(OrganizationMemberOffsetPage.empty());
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: organizationMembersOffsetPagination(orgId={}, role={}, status={})",
                organizationId, role, status);
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return memberService.hasPermission(userId, organizationId, "ORG_VIEW_MEMBERS")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied"));
                    }

                    Flux<OrganizationMember> memberFlux = memberService.findByOrganization(organizationId)
                            .filter(member -> {
                                if (role != null && member.getRole() != role) {
                                    return false;
                                }
                                if (status != null && member.getStatus() != status) {
                                    return false;
                                }
                                return true;
                            });

                    return buildOffsetPage(memberFlux, pagination);
                });
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Get organization members with cursor pagination (mobile/infinite scroll).
     * Schema: organizationMembersCursorPagination(organizationId: ID!, role: OrganizationRole, status: MemberStatus, pagination: CursorPaginationInput): OrganizationMemberConnection!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMemberConnection> organizationMembersCursorPagination(
            @InputArgument String organizationId,
            @InputArgument OrganizationRole role,
            @InputArgument MemberStatus status,
            @InputArgument CursorPaginationInput pagination,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (jwt == null) {
            return Mono.just(OrganizationMemberConnection.empty());
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: organizationMembersCursorPagination(orgId={}, role={}, status={})",
                organizationId, role, status);
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return memberService.hasPermission(userId, organizationId, "ORG_VIEW_MEMBERS")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied"));
                    }

                    Flux<OrganizationMember> memberFlux = memberService.findByOrganization(organizationId)
                            .filter(member -> {
                                if (role != null && member.getRole() != role) {
                                    return false;
                                }
                                if (status != null && member.getStatus() != status) {
                                    return false;
                                }
                                return true;
                            });

                    return buildCursorConnection(memberFlux, pagination);
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build OrganizationMemberOffsetPage from a Flux of members.
     */
    private Mono<OrganizationMemberOffsetPage> buildOffsetPage(Flux<OrganizationMember> memberFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return memberFlux.collectList()
                .map(allMembers -> {
                    int totalCount = allMembers.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<OrganizationMember> paginatedMembers = allMembers.stream()
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

                    return new OrganizationMemberOffsetPage(paginatedMembers, pageInfo);
                });
    }

    /**
     * Build OrganizationMemberConnection from a Flux of members.
     */
    private Mono<OrganizationMemberConnection> buildCursorConnection(Flux<OrganizationMember> memberFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return memberFlux.collectList()
                .map(allMembers -> {
                    int totalCount = allMembers.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allMembers.size(); i++) {
                            if (allMembers.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of members
                    List<OrganizationMember> pageMembers = allMembers.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageMembers.isEmpty()) {
                        return OrganizationMemberConnection.empty();
                    }

                    // Build edges
                    List<OrganizationMemberEdge> edges = pageMembers.stream()
                            .map(OrganizationMemberEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new OrganizationMemberConnection(edges, pageInfo, totalCount);
                });
    }
}
