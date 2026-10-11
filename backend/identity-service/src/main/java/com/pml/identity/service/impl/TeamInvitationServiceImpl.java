package com.pml.identity.service.impl;

import com.pml.shared.constants.InvitationStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.UserService;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.reactive.TransactionalOperator;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.TeamInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Team Invitation Service Implementation
 *
 * Manages team invitation workflow including:
 * - Creating and sending invitations
 * - Invitation acceptance/decline
 * - Expiration handling
 * - Notification sending
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamInvitationServiceImpl implements TeamInvitationService {

    private final TeamInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberService memberService;
    private final EventAccessService eventAccessService;
    /** Messages are requested after the write commits, and their failure stays theirs. */
    private final NotificationProcess notifications;

    /** The accepting user's own email and phone, read from their account. */
    private final UserService userService;

    /** Normalises an invitee's WhatsApp number to the form contacts are stored and matched in. */
    private final com.pml.identity.security.ContactHasher contactHasher;

    /** Finds the account that owns a verified WhatsApp number or email. */
    private final com.pml.identity.account.ContactService contactService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    /** The membership, its grants and the invitation's status are one transaction. */
    private final TransactionalOperator transactionalOperator;

    /** For the conditional claim; a repository save cannot express "only if still PENDING". */
    private final ReactiveMongoTemplate mongoTemplate;

    private static final int INVITATION_EXPIRY_DAYS = 7;

    /** Invitations one organization may send in 24 hours. */
    static final int MAX_INVITATIONS_PER_DAY = 50;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<TeamInvitation> findById(String id) {
        return invitationRepository.findById(id);
    }

    @Override
    public Mono<TeamInvitation> findByToken(String invitationToken) {
        return invitationRepository.findByInvitationToken(invitationToken);
    }

    @Override
    public Flux<TeamInvitation> findPendingByOrganization(String organizationId, Pageable pageable) {
        return invitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING, pageable);
    }

    @Override
    public Flux<TeamInvitation> findPendingByOrganization(String organizationId) {
        return invitationRepository.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING);
    }

    @Override
    public Flux<TeamInvitation> findByOrganization(String organizationId, Pageable pageable) {
        return invitationRepository.findByOrganizationId(organizationId, pageable);
    }

    @Override
    public Flux<TeamInvitation> findPendingByPhone(String phoneNumber) {
        return invitationRepository.findByPhoneNumberAndStatus(phoneNumber, InvitationStatus.PENDING);
    }

    @Override
    public Flux<TeamInvitation> findPendingByEmail(String email) {
        return invitationRepository.findByEmailAndStatus(email, InvitationStatus.PENDING);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<TeamInvitation> invite(
            String organizationId,
            String email,
            String phoneNumber,
            String inviteeName,
            OrganizationRole role,
            String message,
            List<TeamInvitation.EventAccessInput> eventAccessGrants,
            String invitedById) {
        log.info("Creating invitation to organization: {} with role: {} (by {})",
                organizationId, role, email != null && !email.isBlank() ? "email" : "phone");

        // Validate role - cannot invite as OWNER
        if (role == OrganizationRole.OWNER) {
            return Mono.error(new IllegalArgumentException("Cannot invite someone as OWNER"));
        }

        // An invitation goes to an email, a WhatsApp number, or both. A number is stored in its
        // normalised E.164 form, so the same person typed two ways is one invitee.
        String normalisedEmail = email == null || email.isBlank() ? null : email.toLowerCase().trim();
        String normalisedPhone = null;
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            normalisedPhone = contactHasher.normalize(phoneNumber, com.pml.identity.domain.enums.ContactType.WHATSAPP, null, null)
                    .map(com.pml.identity.security.ContactHasher.Normalized::value)
                    .orElse(null);
            if (normalisedPhone == null) {
                return Mono.error(new TranslatedRefusal(ErrorCode.CONTACT_INVALID, "the invitee's number is not valid"));
            }
        }
        if (normalisedEmail == null && normalisedPhone == null) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "an invitation needs an email or a WhatsApp number"));
        }
        final String inviteeEmail = normalisedEmail;
        final String inviteePhone = normalisedPhone;

        // Check if organization exists
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(
                        ErrorCode.ORGANIZATION_UNKNOWN, "organization " + organizationId)))
                .flatMap(org -> {
                    // Only an ACTIVE organization invites. Inviting into one that is suspended
                    // or still awaiting approval builds a team for something that cannot sell a
                    // ticket, and the invitee's first experience is a dead account.
                    if (!org.isActive()) {
                        return Mono.error(new TranslatedRefusal(
                                ErrorCode.ORGANIZATION_NOT_ACTIVE,
                                "organization " + organizationId + " is not active"));
                    }

                    return withinDailyInvitationLimit(organizationId)
                            .then(alreadyAMember(inviteeEmail, inviteePhone, organizationId))
                            .flatMap(isMember -> {
                                if (isMember) {
                                    return Mono.error(new TranslatedRefusal(
                                            ErrorCode.MEMBER_ALREADY_EXISTS,
                                            "the invitee is already a member"));
                                }

                                TeamInvitation invitation = TeamInvitation.builder()
                                        .email(inviteeEmail)
                                        .phoneNumber(inviteePhone)
                                        .inviteeName(inviteeName)
                                        .organizationId(organizationId)
                                        .proposedRole(role)
                                        .eventAccessGrants(eventAccessGrants)
                                        .invitedById(invitedById)
                                        .message(message)
                                        .invitationToken(generateToken())
                                        .expiresAt(clock.instant().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS))
                                        .status(InvitationStatus.PENDING)
                                        .createdAt(clock.instant())
                                        .build();

                                // Revoke, then create, never two live. Refusing instead
                                // strands the inviter: a role mistyped, an address misspelled or
                                // an email that bounced cannot be corrected until the old
                                // invitation expires a week later, and the invitee waits.
                                //
                                // One transaction, because the pair is the guarantee. A revoke
                                // that commits without its replacement leaves the invitee with no
                                // way in and the inviter believing they have been sent one; a
                                // create without the revoke leaves two live invitations,
                                // either of which admits a member on terms nobody chose.
                                return revokePendingFor(inviteeEmail, inviteePhone, organizationId)
                                        .then(invitationRepository.save(invitation))
                                        .as(transactionalOperator::transactional)
                                        .flatMap(saved -> {
                                            log.info("Invitation created: {} for organization: {}",
                                                    saved.getId(), organizationId);
                                            return notifyInvitation(saved).thenReturn(saved);
                                        });
                            });
                });
    }

    @Override
    public Flux<TeamInvitation> bulkInvite(
            String organizationId,
            List<InviteRequest> invites,
            String invitedById) {
        log.info("Creating bulk invitations for organization: {} - Count: {}",
                organizationId, invites.size());

        return Flux.fromIterable(invites)
                .flatMap(request -> invite(
                        organizationId,
                        request.email(),
                        request.phoneNumber(),
                        request.inviteeName(),
                        request.role(),
                        request.message(),
                        request.eventAccessGrants(),
                        invitedById)
                        .onErrorResume(e -> {
                            log.warn("Failed to create invitation for {}: {}",
                                    request.email(), e.getMessage());
                            return Mono.empty();
                        }));
    }

    /**
     * Withdraws any live invitation for this address, so the new one is the only one.
     *
     * <p>Conditional on {@code PENDING}: an invitation already accepted, declined or revoked is
     * somebody's settled history, and re-stamping it would rewrite when and why it ended.
     */
    private Mono<Void> revokePendingFor(String email, String phone, String organizationId) {
        java.util.List<Criteria> addressed = new java.util.ArrayList<>();
        if (email != null) {
            addressed.add(Criteria.where("email").is(email));
        }
        if (phone != null) {
            addressed.add(Criteria.where("phoneNumber").is(phone));
        }
        return mongoTemplate.updateMulti(
                        Query.query(new Criteria().andOperator(
                                Criteria.where("organizationId").is(organizationId),
                                Criteria.where("status").is(InvitationStatus.PENDING),
                                new Criteria().orOperator(addressed.toArray(new Criteria[0])))),
                        Update.update("status", InvitationStatus.REVOKED)
                                .set("revokedAt", clock.instant())
                                .set("updatedAt", clock.instant()),
                        TeamInvitation.class)
                .doOnNext(result -> {
                    if (result.getModifiedCount() > 0) {
                        log.info("Superseded {} pending invitation(s) in organization {}",
                                result.getModifiedCount(), organizationId);
                    }
                })
                .then();
    }

    /**
     * An organization may send this many invitations a day. Each one is a WhatsApp message or an
     * email to an address the sender typed, so without a ceiling the invite form is a free way to
     * message strangers. The count is read from the invitations themselves, so it holds across
     * instances and restarts with nothing extra to keep in step.
     */
    private Mono<Void> withinDailyInvitationLimit(String organizationId) {
        return mongoTemplate.count(Query.query(Criteria.where("organizationId").is(organizationId)
                                .and("createdAt").gte(clock.instant().minus(1, ChronoUnit.DAYS))), TeamInvitation.class)
                .flatMap(sent -> sent >= MAX_INVITATIONS_PER_DAY
                        ? Mono.<Void>error(new TranslatedRefusal(ErrorCode.RATE_LIMIT_EXCEEDED,
                                "this organization has sent its invitations for the day",
                                java.util.Map.of("retryAfterSeconds", 3600)))
                        : Mono.<Void>empty());
    }

    /**
     * Whether this address already belongs to an <em>active</em> member.
     *
     * <p>Active specifically: a removed member keeps their document, and treating that as
     * membership would make a re-invitation impossible, whereas a removed member re-invited later
     * is meant to get a new membership row.
     */
    private Mono<Boolean> alreadyAMember(String email, String phone, String organizationId) {
        Mono<com.pml.identity.domain.model.User> byEmail = email == null ? Mono.empty() : userService.findByEmail(email);
        Mono<com.pml.identity.domain.model.User> byPhone = phone == null ? Mono.empty() : userService.findByPhoneNumber(phone);
        return byEmail.switchIfEmpty(byPhone)
                .flatMap(user -> memberService.isActiveMember(user.getId(), organizationId))
                // No account yet is the ordinary case: invitations are how people arrive.
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<TeamInvitation> resend(String invitationId) {
        log.info("Resending invitation: {}", invitationId);

        // The resolver's reads.invitationForCaller(invitationId) already proved ownership before
        // calling this; defense in depth here too.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        invitationRepository.findById(invitationId),
                        organizationIds -> invitationRepository.findByIdAndOrganizationIdIn(invitationId, organizationIds),
                        ErrorCode.INVITATION_UNKNOWN,
                        "team invitation " + invitationId))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Can only resend pending invitations"));
                    }

                    // Generate new token and extend expiry
                    invitation.setInvitationToken(generateToken());
                    invitation.setExpiresAt(clock.instant().plus(INVITATION_EXPIRY_DAYS, ChronoUnit.DAYS));

                    return invitationRepository.save(invitation)
                            .flatMap(saved -> notifyInvitation(saved).thenReturn(saved));
                });
    }

    @Override
    public Mono<TeamInvitation> revoke(String invitationId) {
        log.info("Revoking invitation: {}", invitationId);

        // The resolver's reads.invitationForCaller(invitationId) already proved ownership before
        // calling this; defense in depth here too.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        invitationRepository.findById(invitationId),
                        organizationIds -> invitationRepository.findByIdAndOrganizationIdIn(invitationId, organizationIds),
                        ErrorCode.INVITATION_UNKNOWN,
                        "team invitation " + invitationId))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Can only revoke pending invitations"));
                    }

                    invitation.setStatus(InvitationStatus.REVOKED);
                    return invitationRepository.save(invitation)
                            .doOnSuccess(revoked -> log.info("Invitation revoked: {}", revoked.getId()));
                });
    }

    @Override
    public Mono<OrganizationMember> accept(String invitationToken, String userId) {
        log.info("Accepting invitation with token for user: {}", userId);
        Instant now = clock.instant();

        return invitationRepository.findByInvitationToken(invitationToken)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(
                        ErrorCode.INVITATION_UNKNOWN, "no invitation for that token")))
                .flatMap(invitation -> {
                    // Validate invitation state
                    if (!invitation.isValid(now)) {
                        if (invitation.isExpired(now)) {
                            invitation.setStatus(InvitationStatus.EXPIRED);
                            return invitationRepository.save(invitation)
                                    .then(Mono.error(new TranslatedRefusal(
                                            ErrorCode.INVITATION_EXPIRED,
                                            "expired at " + invitation.getExpiresAt())));
                        }
                        return Mono.error(new TranslatedRefusal(
                                ErrorCode.INVITATION_NOT_PENDING,
                                "invitation is " + invitation.getStatus()));
                    }

                    return acceptAsAddressee(invitation, userId, now);
                });
    }

    /**
     * The token says which invitation; it does not say who is accepting.
     *
     * <p>An invitation link travels by email or WhatsApp and is then forwarded, screenshotted and
     * pasted into group chats — the holder and the addressee are routinely different people. Without
     * this check, whoever held the link would join the organization in the proposed role, which for
     * an `ADMIN` invitation is the team, the events and the organization's settings.
     *
     * <p>The accepting user's own email and phone are read from their account, never from the
     * request: an identity the caller supplies is not an identity.
     */
    private Mono<OrganizationMember> acceptAsAddressee(
            TeamInvitation invitation, String userId, Instant now) {

        return userService.findById(userId)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(
                        ErrorCode.USER_UNKNOWN, "no account for the authenticated caller")))
                .flatMap(user -> (invitation.addressedTo(user.getEmail(), user.getPhoneNumber())
                        ? Mono.just(true) : addressedToContact(invitation, user)).flatMap(addressed -> {
                    if (!addressed) {
                        // Deliberately not naming the addressee. Telling the holder of a forwarded
                        // link which address it was meant for hands them the invitee's contact
                        // details, which is what the narrow invitation preview exists to withhold.
                        log.warn("securityIncident=true invitation {} presented by {}, who is not "
                                + "the addressee", invitation.getId(), userId);
                        return Mono.error(new TranslatedRefusal(
                                ErrorCode.INVITATION_NOT_ADDRESSED_TO_CALLER,
                                "invitation " + invitation.getId() + " presented by " + userId));
                    }
                    return claimThenCreate(invitation, userId, now);
                }));
    }

    /**
     * Whether the account owns the verified contact the invitation was sent to. A buyer-style account has no email or phone on
     * the document; its numbers are verified contacts, and those are what is compared.
     */
    private Mono<Boolean> addressedToContact(TeamInvitation invitation,
                                             com.pml.identity.domain.model.User user) {
        Mono<Boolean> byPhone = invitation.getPhoneNumber() == null || invitation.getPhoneNumber().isBlank()
                ? Mono.just(false)
                : contactService.accountByContact(invitation.getPhoneNumber(),
                                com.pml.identity.domain.enums.ContactType.WHATSAPP)
                        .map(owner -> owner.getId().equals(user.getId())).defaultIfEmpty(false);
        Mono<Boolean> byEmail = invitation.getEmail() == null || invitation.getEmail().isBlank()
                ? Mono.just(false)
                : contactService.accountByContact(invitation.getEmail(),
                                com.pml.identity.domain.enums.ContactType.EMAIL)
                        .map(owner -> owner.getId().equals(user.getId())).defaultIfEmpty(false);
        return byPhone.flatMap(matched -> matched ? Mono.just(true) : byEmail);
    }

    /**
     * Exactly one membership per token, and everything or nothing.
     *
     * <h2>Claim first, then build</h2>
     * The token is single-use, and two acceptances of one link race by design: an invitation
     * forwarded to a group chat is opened by several people within seconds of each other. Reading
     * the status, finding it {@code PENDING}, and then writing the membership leaves the whole
     * window between the read and the write open — both callers see {@code PENDING}, both proceed.
     *
     * <p>So the status transition happens <em>first</em>, as one conditional update that matches
     * only while the invitation is still {@code PENDING}. Exactly one caller's update reports a
     * modified row; that caller owns the acceptance and everyone else is refused. It is the same
     * compare-and-set booking uses for reservations, and for the same reason.
     *
     * <h2>One transaction</h2>
     * The membership and the grants it creates are one transaction. They are three
     * writes — the invitation's status, the membership, the event grants — and a failure between
     * any two leaves a member with no grants, or an invitation still {@code PENDING} with a
     * membership already created, which the next holder of the link can accept again.
     *
     * <p>The claim is inside the transaction rather than before it, so a failure further down
     * rolls the status back and the invitee can retry with the same link. Claiming outside would
     * burn the token on a transient failure.
     */
    private Mono<OrganizationMember> claimThenCreate(
            TeamInvitation invitation, String userId, Instant now) {

        return claimPending(invitation.getId(), now)
                .flatMap(claimed -> claimed
                        ? createMembership(invitation, userId)
                        : Mono.error(new TranslatedRefusal(
                                ErrorCode.INVITATION_NOT_PENDING,
                                "invitation " + invitation.getId() + " was resolved concurrently")))
                .as(transactionalOperator::transactional)
                // The notification is requested once the transaction has committed: an inviter told
                // somebody joined, by a transaction that then rolls back, is worse than one told a
                // moment late.
                .flatMap(member -> notifyAcceptance(invitation).thenReturn(member));
    }

    /**
     * Moves the invitation {@code PENDING → ACCEPTED}, if and only if it is still
     * {@code PENDING} at the moment of the write.
     *
     * @return {@code true} when this caller made the move and therefore owns the acceptance
     */
    private Mono<Boolean> claimPending(String invitationId, Instant now) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(invitationId)
                                .and("status").is(InvitationStatus.PENDING)),
                        Update.update("status", InvitationStatus.ACCEPTED)
                                .set("acceptedAt", now)
                                .set("updatedAt", now),
                        TeamInvitation.class)
                .map(result -> result.getModifiedCount() == 1);
    }

    private Mono<OrganizationMember> createMembership(
            TeamInvitation invitation, String userId) {

        // Create organization member
        return memberService.createFromInvitation(
                                    invitation.getOrganizationId(),
                                    userId,
                                    invitation.getProposedRole(),
                                    invitation.getInvitedById())
                            .flatMap(member -> {
                                // Create event access grants if specified
                                if (invitation.getEventAccessGrants() != null && !invitation.getEventAccessGrants().isEmpty()) {
                                    return createEventAccessGrants(invitation, userId)
                                            .then(Mono.just(member));
                                }
                                return Mono.just(member);
                            })
                            .doOnSuccess(member -> log.info("Invitation accepted: {} by user: {}",
                                    invitation.getId(), userId));
    }

    @Override
    public Mono<TeamInvitation> decline(String invitationToken) {
        log.info("Declining invitation with token");

        return invitationRepository.findByInvitationToken(invitationToken)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid invitation token")))
                .flatMap(invitation -> {
                    if (invitation.getStatus() != InvitationStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Invitation is no longer pending"));
                    }

                    invitation.setStatus(InvitationStatus.DECLINED);
                    invitation.setDeclinedAt(clock.instant());
                    return invitationRepository.save(invitation)
                            .doOnSuccess(declined -> log.info("Invitation declined: {}", declined.getId()));
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Mono<Void> createEventAccessGrants(TeamInvitation invitation, String userId) {
        return Flux.fromIterable(invitation.getEventAccessGrants())
                .flatMap(grant -> eventAccessService.grant(
                        grant.getEventId(),
                        invitation.getOrganizationId(),
                        userId,
                        grant.getRole(),
                        new HashSet<>(),
                        "Granted via team invitation",
                        grant.getExpiresAt(),
                        invitation.getInvitedById()))
                .then();
    }

    /** One message per invitation link; a resend extends the expiry and so is a new message. */
    private Mono<Void> notifyInvitation(TeamInvitation invitation) {
        long expiresAt = invitation.getExpiresAt() == null ? 0L : invitation.getExpiresAt().toEpochMilli();
        return notifications.request(new NotificationWorkflow.Request(
                NotificationRules.key("team.invitation", invitation.getId() + ":" + expiresAt),
                "team.invitation", null, NotificationRules.TEAM_INVITATION, invitation.getId()));
    }

    private Mono<Void> notifyAcceptance(TeamInvitation invitation) {
        return notifications.request(new NotificationWorkflow.Request(
                NotificationRules.key("team.accepted", invitation.getId()),
                "team.accepted", invitation.getInvitedById(), NotificationRules.TEAM_INVITATION, invitation.getId()));
    }
}
