package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.PermissionResolutionService;
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
 * GraphQL Query Resolver for Organization operations.
 * Handles organization-related queries with both offset and cursor pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationQueryResolver {

    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;
    private final PermissionResolutionService permissionService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get organization by ID.
     * Schema: organization(id: ID!): Organization
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> organization(@InputArgument String id) {
        log.debug("GraphQL query: organization(id={})", id);
        Objects.requireNonNull(id, "Organization ID is required");
        return organizationService.findById(id);
    }

    /**
     * Get organization by slug.
     * Schema: organizationBySlug(slug: String!): Organization
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> organizationBySlug(@InputArgument String slug) {
        log.debug("GraphQL query: organizationBySlug(slug={})", slug);
        Objects.requireNonNull(slug, "Slug is required");
        return organizationService.findBySlug(slug);
    }

    /**
     * Get organization by owner ID.
     * Schema: organizationByOwnerId(ownerId: ID!): Organization
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> organizationByOwnerId(@InputArgument String ownerId) {
        log.debug("GraphQL query: organizationByOwnerId(ownerId={})", ownerId);
        Objects.requireNonNull(ownerId, "Owner ID is required");
        return organizationService.findByOwnerId(ownerId);
    }

    /**
     * Get organizations I belong to.
     * Schema: myOrganizations: [Organization!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<Organization> myOrganizations() {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myOrganizations (userId={})", userId))
                .flatMapMany(userId -> memberService.findByUser(userId)
                        .flatMap(member -> organizationService.findById(member.getOrganizationId())));
    }

    /**
     * Get organization I own.
     * Schema: myOwnedOrganization: Organization
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Organization> myOwnedOrganization() {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myOwnedOrganization (userId={})", userId))
                .flatMap(organizationService::findByOwnerId);
    }

    // ========================================================================
    // ORGANIZATION APPLICATIONS QUERIES (Approval Queue)
    // ========================================================================

    /**
     * Get organization applications with offset pagination (admin only).
     * For the organization approval queue - filters organizations in approval workflow statuses.
     * Schema: organizationApplicationsOffsetPagination(status: OrganizationStatus, pagination: OffsetPaginationInput): OrganizationApplicationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationApplicationOffsetPage> organizationApplicationsOffsetPagination(
            @InputArgument OrganizationStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizationApplicationsOffsetPagination(status={})", status);

        // Get organizations in approval workflow
        Flux<Organization> orgFlux = status != null
                ? organizationService.findByStatus(status)
                : organizationService.findInApprovalWorkflow();

        return buildApplicationOffsetPage(orgFlux, pagination);
    }

    /**
     * Get organization applications with cursor pagination (admin only).
     * For mobile/infinite scroll in the approval queue.
     * Schema: organizationApplicationsCursorPagination(status: OrganizationStatus, pagination: CursorPaginationInput): OrganizationApplicationConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationApplicationConnection> organizationApplicationsCursorPagination(
            @InputArgument OrganizationStatus status,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizationApplicationsCursorPagination(status={})", status);

        // Get organizations in approval workflow
        Flux<Organization> orgFlux = status != null
                ? organizationService.findByStatus(status)
                : organizationService.findInApprovalWorkflow();

        return buildApplicationCursorConnection(orgFlux, pagination);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search organizations with offset pagination (admin only).
     * Schema: organizationsOffsetPagination(search: String, status: OrganizationStatus, verified: Boolean, pagination: OffsetPaginationInput): OrganizationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationOffsetPage> organizationsOffsetPagination(
            @InputArgument String search,
            @InputArgument OrganizationStatus status,
            @InputArgument Boolean verified,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizationsOffsetPagination(search={}, status={}, verified={})",
                search, status, verified);

        Flux<Organization> orgFlux = applyFilters(organizationService.findAll(), search, status, verified);
        return buildOffsetPage(orgFlux, pagination);
    }

    // ========================================================================
    // CURSOR PAGINATION QUERIES (Mobile/Infinite Scroll)
    // ========================================================================

    /**
     * Search organizations with cursor pagination (admin only, mobile/infinite scroll).
     * Schema: organizationsCursorPagination(search: String, status: OrganizationStatus, verified: Boolean, pagination: CursorPaginationInput): OrganizationConnection!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationConnection> organizationsCursorPagination(
            @InputArgument String search,
            @InputArgument OrganizationStatus status,
            @InputArgument Boolean verified,
            @InputArgument CursorPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizationsCursorPagination(search={}, status={}, verified={})",
                search, status, verified);

        Flux<Organization> orgFlux = applyFilters(organizationService.findAll(), search, status, verified);
        return buildCursorConnection(orgFlux, pagination);
    }

    // ========================================================================
    // PERMISSION QUERIES
    // ========================================================================

    /**
     * Get current user's effective permissions for an organization.
     * Schema: myOrganizationPermissions(organizationId: ID!, eventId: ID): EffectivePermissions!
     * Note: This is now in PermissionQueryResolver
     */

    /**
     * Check if slug is available.
     * Schema: isSlugAvailable(slug: String!): Boolean!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> isSlugAvailable(@InputArgument String slug) {
        log.debug("GraphQL query: isSlugAvailable(slug={})", slug);
        Objects.requireNonNull(slug, "Slug is required");
        return organizationService.isSlugAvailable(slug);
    }

    /**
     * Count organizations by status (admin only).
     * Schema: organizationCount(status: OrganizationStatus): Long!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Long> organizationCount(@InputArgument OrganizationStatus status) {
        log.debug("GraphQL query: organizationCount(status={})", status);
        return organizationService.countByStatus(status);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Apply filters to a Flux of organizations.
     */
    private Flux<Organization> applyFilters(Flux<Organization> organizations, String search,
                                             OrganizationStatus status, Boolean verified) {
        return organizations.filter(org -> {
            // Filter by search term (name or slug)
            if (search != null && !search.isBlank()) {
                String searchLower = search.toLowerCase();
                boolean matchesSearch = (org.getName() != null && org.getName().toLowerCase().contains(searchLower))
                        || (org.getSlug() != null && org.getSlug().toLowerCase().contains(searchLower));
                if (!matchesSearch) {
                    return false;
                }
            }

            // Filter by status
            if (status != null && org.getStatus() != status) {
                return false;
            }

            // Filter by verified
            if (verified != null && org.isVerified() != verified) {
                return false;
            }

            return true;
        });
    }

    /**
     * Build OrganizationOffsetPage from a Flux of organizations.
     */
    private Mono<OrganizationOffsetPage> buildOffsetPage(Flux<Organization> orgFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return orgFlux.collectList()
                .map(allOrgs -> {
                    int totalCount = allOrgs.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<Organization> paginatedOrgs = allOrgs.stream()
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

                    return new OrganizationOffsetPage(paginatedOrgs, pageInfo);
                });
    }

    /**
     * Build OrganizationConnection from a Flux of organizations.
     */
    private Mono<OrganizationConnection> buildCursorConnection(Flux<Organization> orgFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return orgFlux.collectList()
                .map(allOrgs -> {
                    int totalCount = allOrgs.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allOrgs.size(); i++) {
                            if (allOrgs.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of organizations
                    List<Organization> pageOrgs = allOrgs.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageOrgs.isEmpty()) {
                        return OrganizationConnection.empty();
                    }

                    // Build edges
                    List<OrganizationEdge> edges = pageOrgs.stream()
                            .map(OrganizationEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new OrganizationConnection(edges, pageInfo, totalCount);
                });
    }

    /**
     * Build OrganizationApplicationOffsetPage from a Flux of organizations.
     */
    private Mono<OrganizationApplicationOffsetPage> buildApplicationOffsetPage(Flux<Organization> orgFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return orgFlux.collectList()
                .map(allOrgs -> {
                    int totalCount = allOrgs.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 0;

                    List<Organization> paginatedOrgs = allOrgs.stream()
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

                    return new OrganizationApplicationOffsetPage(paginatedOrgs, pageInfo);
                });
    }

    /**
     * Build OrganizationApplicationConnection from a Flux of organizations.
     */
    private Mono<OrganizationApplicationConnection> buildApplicationCursorConnection(Flux<Organization> orgFlux, CursorPaginationInput pagination) {
        CursorPaginationInput p = pagination != null ? pagination : CursorPaginationInput.defaults();
        int limit = p.getLimit();

        return orgFlux.collectList()
                .map(allOrgs -> {
                    int totalCount = allOrgs.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (p.after() != null) {
                        for (int i = 0; i < allOrgs.size(); i++) {
                            if (allOrgs.get(i).getId().equals(p.after())) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of organizations
                    List<Organization> pageOrgs = allOrgs.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageOrgs.isEmpty()) {
                        return OrganizationApplicationConnection.empty();
                    }

                    // Build edges
                    List<OrganizationApplicationEdge> edges = pageOrgs.stream()
                            .map(OrganizationApplicationEdge::of)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).cursor();
                    String endCursor = edges.get(edges.size() - 1).cursor();

                    PageInfo pageInfo = PageInfo.forCursor(hasNextPage, hasPreviousPage, startCursor, endCursor, totalCount);

                    return new OrganizationApplicationConnection(edges, pageInfo, totalCount);
                });
    }
}
