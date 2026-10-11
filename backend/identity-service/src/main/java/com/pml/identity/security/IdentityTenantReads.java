package com.pml.identity.security;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.TenantGuard;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Loads organization-owned identity records for the caller of the current request.
 *
 * <p>Every lookup puts the caller's organizations into the query, so another organization's record
 * is never read, and refuses it with the same {@code *_UNKNOWN} code an id that was never issued
 * produces. Only after a record is found inside the caller's organizations does the caller's
 * permission get checked, so "permission denied" is only ever said about the caller's own records.
 * Platform administrators read any record.
 */
@Component
public class IdentityTenantReads {

    private final EventAccessGrantRepository grants;
    private final OrganizationMemberRepository members;
    private final TeamInvitationRepository invitations;
    private final VerificationDocumentRepository documents;

    public IdentityTenantReads(EventAccessGrantRepository grants, OrganizationMemberRepository members,
                               TeamInvitationRepository invitations, VerificationDocumentRepository documents) {
        this.grants = grants;
        this.members = members;
        this.invitations = invitations;
        this.documents = documents;
    }

    /** A grant held by the caller, or one in one of the caller's organizations. */
    public Mono<EventAccessGrant> grantForCaller(String grantId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(caller -> grants.findByIdAndUserId(grantId, caller))
                .switchIfEmpty(Mono.defer(() -> CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                        scope,
                        grants.findById(grantId),
                        organizationIds -> grants.findByIdAndOrganizationIdIn(grantId, organizationIds),
                        ErrorCode.ACCESS_GRANT_UNKNOWN,
                        "event access grant " + grantId))));
    }

    /** The event's grants that sit in the caller's organizations; every grant for a platform administrator. */
    public Flux<EventAccessGrant> grantsForEvent(String eventId) {
        return CurrentTenantScope.get().flatMapMany(scope -> PlatformWideAccess
                .isPlatformWide(scope, PlatformWideAccess.Reason.EVENT_ACCESS_GRANTS_READ)
                .flatMapMany(platformWide -> platformWide
                        ? grants.findByEventId(eventId)
                        : grants.findByEventIdAndOrganizationIdIn(eventId, scope.organizationIds())));
    }

    /** Every grant in one organization, when the caller belongs to it or is a platform administrator. */
    public Flux<EventAccessGrant> grantsForOrganization(String organizationId) {
        return CurrentTenantScope.get().flatMapMany(scope -> scope.permits(organizationId)
                ? grants.findByOrganizationId(organizationId)
                : Flux.empty());
    }

    /**
     * A user's grant on an event: the caller's own, or another user's when the grant sits in one of
     * the caller's organizations. Anything else is empty.
     */
    public Mono<EventAccessGrant> userGrantForCaller(String userId, String eventId) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(caller -> caller.equals(userId)
                ? grants.findByUserIdAndEventId(userId, eventId)
                : CurrentTenantScope.get().flatMap(scope -> PlatformWideAccess
                        .isPlatformWide(scope, PlatformWideAccess.Reason.USER_EVENT_GRANT_READ)
                        .flatMap(platformWide -> platformWide
                                ? grants.findByUserIdAndEventId(userId, eventId)
                                : grants.findByUserIdAndEventIdAndOrganizationIdIn(userId, eventId, scope.organizationIds()).next())));
    }

    public Mono<OrganizationMember> memberForCaller(String memberId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                members.findById(memberId),
                organizationIds -> members.findByIdAndOrganizationIdIn(memberId, organizationIds),
                ErrorCode.MEMBER_UNKNOWN,
                "organization member " + memberId));
    }

    public Mono<TeamInvitation> invitationForCaller(String invitationId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                invitations.findById(invitationId),
                organizationIds -> invitations.findByIdAndOrganizationIdIn(invitationId, organizationIds),
                ErrorCode.INVITATION_UNKNOWN,
                "team invitation " + invitationId));
    }

    public Mono<VerificationDocument> documentForCaller(String documentId) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                documents.findById(documentId),
                organizationIds -> documents.findByIdAndOrganizationIdIn(documentId, organizationIds),
                ErrorCode.DOCUMENT_UNKNOWN,
                "verification document " + documentId));
    }
}
