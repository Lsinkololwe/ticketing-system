package com.pml.identity.web.graphql.query;

import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.identity.web.graphql.dto.pagination.*;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Organization Member operations.
 * Handles member-related queries with offset pagination.
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
     * One member of an organization the caller belongs to.
     * OWASP A01:2021 · CWE-639.
     *
     * <h2>Why {@code TenantScope} and not a subject comparison</h2>
     * Both arguments come from the client, and the question is not "is this row yours" — it is
     * "are you in the organization this row belongs to". A caller may legitimately read a
     * colleague's membership; they may not read a stranger's. That is a membership question about
     * the <em>organization named in the argument</em>, which is exactly what {@code TenantScope}
     * answers, and it is why the subject-comparison used for reservations and transfers is the
     * wrong instrument here.
     *
     * <p>{@code organizationId} is a scope selector, not a grant: it can only narrow to an
     * organization the token already established. Naming one the caller does not belong to
     * refuses, and refuses the same way an organization that was never issued does.
     *
     * <h2>What it protected</h2>
     * Unscoped, this read returned any user's role in any organization to any signed-in caller,
     * and by enumeration the whole team roster. The paged sibling
     * {@code organizationMembers(organizationId, …)} resolves the caller first; this is the one
     * that did not.
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

        return CurrentTenantScope.get()
                .flatMap(scope -> PlatformWideAccess
                        .isPlatformWide(scope, PlatformWideAccess.Reason.ORGANIZATION_MEMBER_READ)
                        .flatMap(platformWide -> platformWide || scope.permits(organizationId)
                                ? memberService.findByUserAndOrganization(userId, organizationId)
                                : Mono.<OrganizationMember>error(TenantBoundary.refuse(ErrorCode.ORGANIZATION_UNKNOWN,
                                        "organization " + organizationId + " requested by " + scope))));
    }

    /**
     * Get my membership in an organization.
     * Schema: myOrganizationMembership(organizationId: ID!): OrganizationMember
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMember> myOrganizationMembership(@InputArgument String organizationId) {
        Objects.requireNonNull(organizationId, "Organization ID is required");
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myOrganizationMembership(orgId={}, userId={})", organizationId, userId))
                .flatMap(userId -> memberService.findByUserAndOrganization(userId, organizationId));
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get organization members with offset pagination.
     * Schema: organizationMembers(organizationId: ID!, role: OrganizationRole, status: MemberStatus, pagination: OffsetPaginationInput): OrganizationMemberOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OrganizationMemberOffsetPage> organizationMembers(
            @InputArgument String organizationId,
            @InputArgument OrganizationRole role,
            @InputArgument MemberStatus status,
            @InputArgument OffsetPaginationInput pagination
    ) {
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: organizationMembers(orgId={}, role={}, status={})",
                        organizationId, role, status))
                // A platform administrator reads any organization's team (the admin console's Team tab)
                // without being a member of it; everyone else needs TEAM_VIEW inside it.
                .flatMap(userId -> CurrentTenantScope.get()
                        .flatMap(scope -> PlatformWideAccess
                                .isPlatformWide(scope, PlatformWideAccess.Reason.ORGANIZATION_TEAM_READ)
                                .flatMap(platformWide -> platformWide
                                        ? Mono.<Void>empty()
                                        : memberService.requirePermission(userId, organizationId, Permission.TEAM_VIEW)))
                        .then(Mono.defer(() -> {
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
                        })));
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

}
