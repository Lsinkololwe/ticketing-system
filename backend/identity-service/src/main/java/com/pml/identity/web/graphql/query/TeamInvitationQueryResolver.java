package com.pml.identity.web.graphql.query;

import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.graphql.dto.InvitationPreview;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
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
 * GraphQL Query Resolver for Team Invitation operations.
 * Handles invitation-related queries with offset pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TeamInvitationQueryResolver {

    private final TeamInvitationService invitationService;
    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;
    private final com.pml.identity.account.ContactService contactService;
    private final com.pml.identity.security.ContactCrypto contactCrypto;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * The acceptance page's view of an invitation.
     * Schema: {@code invitationByToken(token: String!): InvitationPreview}
     *
     * <h2>Narrow on purpose</h2>
     * This returns {@link InvitationPreview} and not the invitation document. The token is a
     * bearer credential that travels by email or WhatsApp and is forwarded, screenshotted and
     * pasted into group chats — so whatever this answers is answered to whoever the link reached,
     * not to the person it was addressed to.
     *
     * <p>Returning {@code TeamInvitation} would hand that audience the invitee's
     * {@code email} and {@code phoneNumber}, the inviter's user id, the personal message, and the
     * token itself. The preview carries the five fields a stranger needs to decide whether to accept, and
     * this returns those five.
     *
     * <p>Identity is checked at acceptance, not here: the preview deliberately answers before
     * anyone has proved who they are, because the recipient may not have an account yet.
     */
    @DgsQuery
    public Mono<InvitationPreview> invitationByToken(@InputArgument String token) {
        log.debug("GraphQL query: invitationByToken");
        Objects.requireNonNull(token, "Token is required");

        return invitationService.findByToken(token)
                .flatMap(invitation -> organizationService.findById(invitation.getOrganizationId())
                        .map(organization -> new InvitationPreview(
                                organization.getName(),
                                organization.getLogoUrl(),
                                invitation.getProposedRole(),
                                inviterDisplayName(invitation),
                                invitation.getExpiresAt())));
    }

    /**
     * A name, never an identifier.
     *
     * <p>Falls back to the organization's own name rather than to the inviter's id or email: an
     * unresolvable inviter is a display problem, and answering it with a user id would put back
     * one of the fields this type exists to withhold.
     */
    private static String inviterDisplayName(TeamInvitation invitation) {
        return invitation.getInviteeName() != null && !invitation.getInviteeName().isBlank()
                ? invitation.getInviteeName()
                : "A team administrator";
    }

    /**
     * Get my pending invitations.
     * Schema: myPendingInvitations: [TeamInvitation!]!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<TeamInvitation> myPendingInvitations() {
        // Invitations addressed to the caller by email (the token's address) and by any verified
        // WhatsApp number the account holds. The numbers come from the account's own contacts,
        // never from the request.
        Flux<TeamInvitation> byEmail = SecurityContextUtils.getCurrentUserEmail()
                .filter(email -> !email.isBlank())
                .flatMapMany(invitationService::findPendingByEmail);
        Flux<TeamInvitation> byPhone = SecurityContextUtils.requireCurrentUserId()
                .flatMapMany(userId -> contactService.contactsOf(userId)
                        .filter(contact -> contact.getType() == com.pml.identity.domain.enums.ContactType.WHATSAPP
                                && contact.getVerifiedAt() != null)
                        .concatMap(contact -> contactCrypto.decrypt(contact.getValueEncrypted()))
                        .concatMap(invitationService::findPendingByPhone));
        return byEmail.concatWith(byPhone).distinct(TeamInvitation::getId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get pending invitations for organization with offset pagination.
     * Schema: pendingInvitations(organizationId: ID!, pagination: OffsetPaginationInput): TeamInvitationOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<TeamInvitationOffsetPage> pendingInvitations(
            @InputArgument String organizationId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        Objects.requireNonNull(organizationId, "Organization ID is required");

        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: pendingInvitations(orgId={})", organizationId))
                .flatMap(userId -> memberService.requirePermission(userId, organizationId, Permission.TEAM_VIEW)
                        .then(Mono.defer(() -> {
                            return buildOffsetPage(invitationService.findPendingByOrganization(organizationId), pagination);
                        })));
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

}
