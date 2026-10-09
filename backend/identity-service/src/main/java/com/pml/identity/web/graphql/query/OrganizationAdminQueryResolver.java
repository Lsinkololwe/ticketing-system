package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.security.IdentityTenantReads;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.PayoutAccountQueryService;
import com.pml.identity.web.graphql.dto.pagination.EventAccessGrantOffsetPage;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.OffsetSlice;
import com.pml.identity.web.graphql.dto.platform.PayoutAccountFilterInput;
import com.pml.identity.web.graphql.dto.platform.PayoutAccountRecordOffsetPage;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.Comparator;

/**
 * Organization reads added for the organizer and admin apps: the caller's own organization by
 * membership, an organization's event-access grants, and the admin payout-account review table.
 */
@DgsComponent
@RequiredArgsConstructor
public class OrganizationAdminQueryResolver {

    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;
    private final IdentityTenantReads reads;
    private final PayoutAccountQueryService payoutAccounts;

    /**
     * The organization the caller belongs to, by active membership, so a manager or marketer, who
     * does not own anything, gets the same answer an owner does. When the caller belongs to several
     * the one they own comes first, then the longest-standing membership.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> myOrganization() {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> memberService.findActiveByUser(userId)
                        .collectList()
                        .flatMap(memberships -> memberships.stream()
                                .min(Comparator.comparing((OrganizationMember m) -> !m.isOwner())
                                        .thenComparing(OrganizationMember::getJoinedAt,
                                                Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(m -> organizationService.findById(m.getOrganizationId()))
                                .orElse(Mono.empty())));
    }

    /**
     * Every event-access grant in one organization, across its events. Visible to the
     * organization's own members and to platform administrators; for anyone else it is empty,
     * which is also what an organization that does not exist returns.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EventAccessGrantOffsetPage> organizationEventAccessGrants(
            @InputArgument String organizationId,
            @InputArgument AccessGrantStatus status,
            @InputArgument OffsetPaginationInput pagination) {
        return reads.grantsForOrganization(organizationId)
                .filter((EventAccessGrant grant) -> status == null || grant.getStatus() == status)
                .collectList()
                .map(all -> {
                    OffsetSlice<EventAccessGrant> slice = OffsetSlice.of(all, pagination);
                    return new EventAccessGrantOffsetPage(slice.content(), slice.pageInfo());
                });
    }

    /** Payout accounts on file, with their review state, for the admin finance team. */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    public Mono<PayoutAccountRecordOffsetPage> bankAccounts(
            @InputArgument PayoutAccountFilterInput filter,
            @InputArgument OffsetPaginationInput pagination) {
        return payoutAccounts.list(filter).collectList()
                .map(all -> {
                    var slice = OffsetSlice.of(all, pagination);
                    return new PayoutAccountRecordOffsetPage(slice.content(), slice.pageInfo());
                });
    }
}
