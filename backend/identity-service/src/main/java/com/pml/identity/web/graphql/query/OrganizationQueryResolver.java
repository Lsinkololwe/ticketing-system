package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
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
 * Handles organization-related queries with offset pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationQueryResolver {

    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;

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
    @PreAuthorize("isAuthenticated()") // application stage: any signed-in account, own application only (ORGANIZER is granted on approval)
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
     * Schema: organizationApplications(status: OrganizationStatus, pagination: OffsetPaginationInput): OrganizationApplicationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationApplicationOffsetPage> organizationApplications(
            @InputArgument OrganizationStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizationApplications(status={})", status);

        // Get organizations in approval workflow
        Flux<Organization> orgFlux = status != null
                ? organizationService.findByStatus(status)
                : organizationService.findInApprovalWorkflow();

        return buildApplicationOffsetPage(orgFlux, pagination);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Search organizations with offset pagination (admin only).
     * Schema: organizations(search: String, status: OrganizationStatus, verified: Boolean, pagination: OffsetPaginationInput): OrganizationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<OrganizationOffsetPage> organizations(
            @InputArgument String search,
            @InputArgument OrganizationStatus status,
            @InputArgument Boolean verified,
            @InputArgument com.pml.identity.domain.enums.KybStatus kybStatus,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: organizations(search={}, status={}, verified={}, kybStatus={})",
                search, status, verified, kybStatus);

        Flux<Organization> orgFlux = applyFilters(organizationService.findAll(), search, status, verified)
                .filter(org -> kybStatus == null || org.getKybStatus() == kybStatus);
        return buildOffsetPage(orgFlux, pagination);
    }

    // ========================================================================
    // PERMISSION QUERIES
    // ========================================================================

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

}
