package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.ContactService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.web.graphql.query.TeamInvitationQueryResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.service.EventAccessService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.UserService;
import com.pml.identity.service.impl.TeamInvitationServiceImpl;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.shared.constants.InvitationStatus;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Inviting a team member by WhatsApp number: stored normalised, superseded, and accepted only by its addressee. */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R9 · an invitation can be addressed to a WhatsApp number")
class TeamInvitationPhoneTest {

    private static final String ORG = "org-phone";
    private static final String PHONE = "+260971234567";
    private static final Instant NOW = Instant.parse("2026-10-04T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ContactHasher hasher;
    private static TeamInvitationServiceImpl service;
    private static UserService users;
    private static OrganizationMemberService members;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "identity_invite_phone");
        template = new ReactiveMongoTemplate(factory);
        ReactiveMongoRepositoryFactory repositories = new ReactiveMongoRepositoryFactory(template);
        hasher = new ContactHasher("invite-phone-test-hash-key");
        users = mock(UserService.class);
        members = mock(OrganizationMemberService.class);
        NotificationProcess notifications = mock(NotificationProcess.class);
        when(notifications.request(any())).thenReturn(Mono.empty());
        service = new TeamInvitationServiceImpl(
                repositories.getRepository(TeamInvitationRepository.class),
                repositories.getRepository(OrganizationRepository.class),
                members, mock(EventAccessService.class), notifications, users, hasher,
                new ContactService(template, hasher), TestClock.frozenAt(NOW),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(factory)), template);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), TeamInvitation.class).block();
        template.remove(new Query(), Organization.class).block();
        template.remove(new Query(), Contact.class).block();
        template.save(Organization.builder().id(ORG).name("Phone Org").slug("phone-org").ownerId("owner")
                .status(OrganizationStatus.ACTIVE).build()).block();
        when(users.findByEmail(anyString())).thenReturn(Mono.empty());
        when(users.findByPhoneNumber(anyString())).thenReturn(Mono.empty());
        when(members.isActiveMember(anyString(), anyString())).thenReturn(Mono.just(false));
    }

    private Mono<TeamInvitation> invite(String email, String phone) {
        return service.invite(ORG, email, phone, "Chanda", OrganizationRole.MANAGER, null, null, "owner");
    }

    private void ownVerifiedNumber(String accountId, String phone) {
        template.save(User.builder().id(accountId).build()).block();
        template.save(Contact.builder().id("c-" + accountId).accountId(accountId).type(ContactType.WHATSAPP)
                .valueHash(hasher.hash(ContactType.WHATSAPP, phone)).valueMasked("+260 97* ***567")
                .valueEncrypted(AccountFixtures.CRYPTO.encrypt(phone).block())
                .verifiedAt(NOW).createdAt(NOW).build()).block();
    }

    @Test
    @DisplayName("a phone-only invitation is stored with the number in E.164 and no email")
    void phoneOnly() {
        TeamInvitation invitation = invite(null, "+260 97 123 4567").block();

        assertThat(invitation.getEmail()).isNull();
        assertThat(invitation.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("an invitation needs an email or a number, and a number must parse")
    void needsAnAddress() {
        assertThatThrownBy(() -> invite(null, null).block()).isInstanceOfSatisfying(DomainRefusal.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED));
        assertThatThrownBy(() -> invite(" ", " ").block()).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> invite(null, "not a number").block()).isInstanceOfSatisfying(DomainRefusal.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTACT_INVALID));
        assertThat(template.count(new Query(), TeamInvitation.class).block()).isZero();
    }

    @Test
    @DisplayName("inviting the same number again supersedes the live invitation instead of leaving two")
    void supersedesByNumber() {
        TeamInvitation first = invite(null, PHONE).block();
        TeamInvitation second = invite(null, "+260 971 234 567").block();

        List<TeamInvitation> all = template.find(new Query(), TeamInvitation.class).collectList().block();
        assertThat(all).hasSize(2);
        assertThat(all.stream().filter(i -> i.getStatus() == InvitationStatus.PENDING)).extracting(TeamInvitation::getId)
                .containsExactly(second.getId());
        assertThat(template.findById(first.getId(), TeamInvitation.class).block().getStatus())
                .isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    @DisplayName("a number that already belongs to an active member is refused")
    void alreadyAMember() {
        when(users.findByPhoneNumber(eq(PHONE))).thenReturn(Mono.just(User.builder().id("member-1").build()));
        when(members.isActiveMember("member-1", ORG)).thenReturn(Mono.just(true));

        assertThatThrownBy(() -> invite(null, PHONE).block()).isInstanceOfSatisfying(DomainRefusal.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.MEMBER_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("the owner of the verified number accepts, although their account document carries no phone")
    void addresseeByContactAccepts() {
        ownVerifiedNumber("buyer-1", PHONE);
        TeamInvitation invitation = invite(null, PHONE).block();
        when(users.findById("buyer-1")).thenReturn(Mono.just(User.builder().id("buyer-1").build()));
        when(members.createFromInvitation(eq(ORG), eq("buyer-1"), eq(OrganizationRole.MANAGER), eq("owner")))
                .thenReturn(Mono.just(OrganizationMember.builder().id("m-1").userId("buyer-1").organizationId(ORG)
                        .role(OrganizationRole.MANAGER).build()));

        OrganizationMember member = service.accept(invitation.getInvitationToken(), "buyer-1").block();

        assertThat(member.getUserId()).isEqualTo("buyer-1");
        assertThat(template.findById(invitation.getId(), TeamInvitation.class).block().getStatus())
                .isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("someone holding the link who does not own the number is refused and the invitation stays pending")
    void strangerIsRefused() {
        ownVerifiedNumber("buyer-1", PHONE);
        ownVerifiedNumber("stranger", "+260961111111");
        TeamInvitation invitation = invite(null, PHONE).block();
        when(users.findById("stranger")).thenReturn(Mono.just(User.builder().id("stranger").build()));

        assertThatThrownBy(() -> service.accept(invitation.getInvitationToken(), "stranger").block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVITATION_NOT_ADDRESSED_TO_CALLER));
        assertThat(template.findById(invitation.getId(), TeamInvitation.class).block().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
        assertThat(template.count(Query.query(Criteria.where("status").is(InvitationStatus.ACCEPTED)), TeamInvitation.class).block())
                .isZero();
    }

    private void verifiedEmail(String accountId, String email) {
        template.save(Contact.builder().id("e-" + accountId).accountId(accountId).type(ContactType.EMAIL)
                .valueHash(hasher.hash(ContactType.EMAIL, email)).valueMasked("m***@example.zm")
                .valueEncrypted(AccountFixtures.CRYPTO.encrypt(email).block())
                .verifiedAt(NOW).createdAt(NOW).build()).block();
    }

    @Test
    @DisplayName("an account that holds the invited email as a verified contact accepts")
    void verifiedEmailAccepts() {
        template.save(User.builder().id("buyer-2").build()).block();
        verifiedEmail("buyer-2", "mwansa@example.zm");
        TeamInvitation invitation = invite("Mwansa@Example.zm", null).block();
        when(users.findById("buyer-2")).thenReturn(Mono.just(User.builder().id("buyer-2").build()));
        when(members.createFromInvitation(eq(ORG), eq("buyer-2"), eq(OrganizationRole.MANAGER), eq("owner")))
                .thenReturn(Mono.just(OrganizationMember.builder().id("m-2").userId("buyer-2").organizationId(ORG)
                        .role(OrganizationRole.MANAGER).build()));

        assertThat(invitation.getEmail()).isEqualTo("mwansa@example.zm");
        assertThat(service.accept(invitation.getInvitationToken(), "buyer-2").block().getUserId()).isEqualTo("buyer-2");
    }

    @Test
    @DisplayName("a staff account with the invited email typed on its profile, never verified, cannot accept")
    void typedButUnverifiedEmailIsNotTheAddressee() {
        TeamInvitation invitation = invite("ops.lead@example.zm", null).block();
        User staff = User.builder().id("staff-1").email("ops.lead@example.zm").phoneNumber(PHONE)
                .phoneVerified(true).emailVerified(true).createdVia("STAFF_ADMIN").build();
        template.save(staff).block();
        when(users.findById("staff-1")).thenReturn(Mono.just(staff));

        assertThatThrownBy(() -> service.accept(invitation.getInvitationToken(), "staff-1").block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVITATION_NOT_ADDRESSED_TO_CALLER));
        assertThat(template.findById(invitation.getId(), TeamInvitation.class).block().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("a number typed on the profile with the old verified flag set does not make the account the addressee")
    void typedPhoneIsNotTheAddressee() {
        TeamInvitation invitation = invite(null, PHONE).block();
        User legacy = User.builder().id("legacy-1").phoneNumber(PHONE).phoneVerified(true).build();
        template.save(legacy).block();
        when(users.findById("legacy-1")).thenReturn(Mono.just(legacy));

        assertThatThrownBy(() -> service.accept(invitation.getInvitationToken(), "legacy-1").block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVITATION_NOT_ADDRESSED_TO_CALLER));
    }

    @Test
    @DisplayName("an invitation to an address that is not an email is refused before anything is stored")
    void invalidEmailIsRefused() {
        assertThatThrownBy(() -> invite("not-an-email", null).block()).isInstanceOfSatisfying(DomainRefusal.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONTACT_INVALID));
        assertThat(template.count(new Query(), TeamInvitation.class).block()).isZero();
    }

    private Flux<TeamInvitation> myPendingInvitationsAs(String accountId, String emailClaim) {
        TeamInvitationQueryResolver resolver = new TeamInvitationQueryResolver(service,
                mock(OrganizationService.class), members, new ContactService(template, hasher), AccountFixtures.CRYPTO);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(accountId).claim("email", emailClaim)
                .claim("email_verified", false).issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
        return resolver.myPendingInvitations().contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of())));
    }

    @Test
    @DisplayName("my pending invitations are those addressed to a verified contact, not to the email in the token")
    void myPendingInvitationsIgnoreTheTokenEmailClaim() {
        template.save(User.builder().id("buyer-3").build()).block();
        verifiedEmail("buyer-3", "verified.person@example.zm");
        ownVerifiedNumber("buyer-3", PHONE);
        invite("verified.person@example.zm", null).block();
        invite(null, PHONE).block();
        invite("claimed.only@example.zm", null).block();
        invite("someone.else@example.zm", null).block();

        List<TeamInvitation> mine = myPendingInvitationsAs("buyer-3", "claimed.only@example.zm").collectList().block();

        assertThat(mine).extracting(TeamInvitation::getEmail, TeamInvitation::getPhoneNumber)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("verified.person@example.zm", null),
                        org.assertj.core.groups.Tuple.tuple(null, PHONE));
    }

    @Test
    @DisplayName("an account with no verified contact has no pending invitations even when the token carries an email")
    void noVerifiedContactNoInvitations() {
        invite("claimed.only@example.zm", null).block();

        assertThat(myPendingInvitationsAs("staff-9", "claimed.only@example.zm").collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("inviting, refusing, accepting and bulk inviting write no email address or number to any log, at any level")
    void invitationPathsLogNoContact() {
        String email = "private.person@example.zm";
        com.pml.identity.web.graphql.mutation.TeamInvitationMutationResolver resolver =
                new com.pml.identity.web.graphql.mutation.TeamInvitationMutationResolver(service,
                        mock(com.pml.identity.security.IdentityTenantReads.class), members);
        when(members.requirePermission(anyString(), anyString(), any())).thenReturn(Mono.empty());
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("owner").issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
        var auth = new JwtAuthenticationToken(jwt, List.of());

        try (com.pml.identity.auth.delivery.LogWatch logs = new com.pml.identity.auth.delivery.LogWatch()) {
            resolver.inviteTeamMember(ORG, new com.pml.identity.web.graphql.dto.organization.InviteMemberInput(
                            email, PHONE, "Chanda", OrganizationRole.MANAGER, null, null))
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)).block();
            // a bulk entry that fails (an invalid email) is logged without the address
            service.bulkInvite(ORG, List.of(new com.pml.identity.service.TeamInvitationService.InviteRequest(
                    "not-an-email@@", null, "X", OrganizationRole.MANAGER, null, null)), "owner").collectList().block();
            // somebody who is not the addressee presents the link
            TeamInvitation invitation = template.find(new Query(), TeamInvitation.class).blockFirst();
            when(users.findById("stranger-9")).thenReturn(Mono.just(User.builder().id("stranger-9").email(email)
                    .phoneNumber(PHONE).build()));
            assertThatThrownBy(() -> service.accept(invitation.getInvitationToken(), "stranger-9").block())
                    .isInstanceOf(DomainRefusal.class);

            logs.assertNoneContains(email, PHONE, PHONE.substring(1), "971234567");
        }
    }

    @Test
    @DisplayName("pending invitations by number are found for the lookup behind myPendingInvitations")
    void findsPendingByPhone() {
        invite(null, PHONE).block();
        invite("other@example.com", null).block();

        assertThat(service.findPendingByPhone(PHONE).collectList().block()).hasSize(1);
        assertThat(service.findPendingByPhone("+260962222222").collectList().block()).isEmpty();
    }

    @Test
    @DisplayName("an organization that has sent fifty invitations today is refused a fifty-first, and no message is queued")
    void dailyLimit() {
        for (int i = 0; i < 50; i++) {
            template.save(TeamInvitation.builder().id("old-" + i).email("p" + i + "@example.com").organizationId(ORG)
                    .proposedRole(OrganizationRole.CONTRIBUTOR).invitedById("owner").invitationToken("t" + i)
                    .expiresAt(NOW.plusSeconds(3600)).status(InvitationStatus.PENDING).createdAt(NOW.minusSeconds(60)).build()).block();
        }

        assertThatThrownBy(() -> invite(null, PHONE).block()).isInstanceOfSatisfying(DomainRefusal.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
        assertThat(template.count(new Query(), TeamInvitation.class).block()).isEqualTo(50);
    }
}
