package com.pml.identity.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.service.AdminAuditService;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.SortDirection;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationKeys;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Every way access is cut, against a throwaway replica set and Redis: the automatic triggers, the
 * administrator's manual revocation, the person's own sign-out of other devices, and the log of
 * what is still in force. Each cut is asserted in all three places it must land: the MongoDB
 * record, the Redis key and the audit row naming the trigger.
 */
@Tag("L2")
@Tag("ET-IDN-003")
@DisplayName("Cutting access writes the record, the cache key and an audit row naming why")
class AccessRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T09:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);

    private static AccountFixtures db;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static RevocationRepository repository;

    private FakeKeycloak keycloak;
    private AccessRevocationService service;
    private AccountService accounts;
    private String buyerId;
    private String buyerKeycloakId;
    private String otherId;
    private String otherKeycloakId;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_access_revocation", CLOCK);
        repository = new ReactiveMongoRepositoryFactory(db.template).getRepository(RevocationRepository.class);
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void disconnect() {
        db.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void seed() {
        db.reset();
        db.template.remove(new Query(), RevocationRecord.class).block();
        db.template.remove(new Query(), OrganizationMember.class).block();
        redis.delete(redis.keys("pml:*")).block();
        keycloak = new FakeKeycloak();

        RevocationProperties properties = new RevocationProperties();
        MongoRevocationStore store = new MongoRevocationStore(repository, redis,
                new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl()), properties,
                new RevocationMetrics(new SimpleMeterRegistry(), CLOCK), CLOCK);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("store", store);
        ObjectProvider<MongoRevocationStore> provider = factory.getBeanProvider(MongoRevocationStore.class);

        accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak, provider,
                new KeycloakProperties(), CLOCK);
        service = new AccessRevocationService(provider, new AdminAuditService(db.template, CLOCK), accounts,
                keycloak, db.template, CLOCK);

        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox,
                db.proofs, keycloak, AccountFixtures.CRYPTO, CLOCK);
        buyerId = account(provisioning, "+260971234567", "proof-1");
        buyerKeycloakId = provisioning.createKeycloakUser(buyerId).block();
        provisioning.activate(buyerId, buyerKeycloakId, "web", null, List.of()).block();
        otherId = account(provisioning, "+260972345678", "proof-2");
        otherKeycloakId = provisioning.createKeycloakUser(otherId).block();
        provisioning.activate(otherId, otherKeycloakId, "web", null, List.of()).block();
    }

    private String account(AccountProvisioning provisioning, String number, String proofId) {
        ProofRecord proof = db.proofFor(ContactType.WHATSAPP, number, proofId);
        return provisioning.claim(proofId, proof.contactKey(), ContactType.WHATSAPP).block().accountId();
    }

    private List<AuditLog> accessAudits() {
        return db.template.findAll(AuditLog.class).collectList().block().stream()
                .filter(row -> row.getAction() == AuditLog.AuditAction.ACCESS_REVOKED).toList();
    }

    private void member(String userId, String organizationId, MemberStatus status) {
        db.template.insert(OrganizationMember.builder().id(organizationId + ":" + userId).userId(userId)
                .organizationId(organizationId).role(OrganizationRole.CONTRIBUTOR).status(status).build()).block();
    }

    // ---- automatic triggers -------------------------------------------------------------------------

    @Test
    @DisplayName("cutting an account revokes its Keycloak user and caches the key, with no audit row for an automatic trigger")
    void cutAccountWritesEverywhere() {
        service.cutAccount(buyerId, RevocationTrigger.MEMBER_REMOVED, "admin-1").block();

        RevocationRecord record = repository.findById(RevocationType.USER.documentId(buyerKeycloakId)).block();
        assertThat(record).isNotNull();
        assertThat(record.getTrigger()).isEqualTo(RevocationTrigger.MEMBER_REMOVED);
        assertThat(record.getRevokedBy()).isEqualTo("admin-1");
        assertThat(record.getReason()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(redis.hasKey(RevocationKeys.user(buyerKeycloakId)).block()).isTrue();

        assertThat(accessAudits()).as("an automatic trigger is already recorded by the event that caused it")
                .isEmpty();
    }

    @Test
    @DisplayName("cutting an account that was never signed in, or does not exist, is a no-op")
    void unknownAccountIsANoOp() {
        service.cutAccount("no-such-account", RevocationTrigger.GRANT_REVOKED, "admin-1").block();
        service.cutAccount(null, RevocationTrigger.GRANT_REVOKED, "admin-1").block();

        assertThat(repository.count().block()).isZero();
        assertThat(accessAudits()).isEmpty();
    }

    @Test
    @DisplayName("suspending an organization cuts every active member and nobody else")
    void organizationSuspensionCutsActiveMembersOnly() {
        member(buyerId, "org-1", MemberStatus.ACTIVE);
        member(otherId, "org-2", MemberStatus.ACTIVE);

        service.cutOrganizationMembers("org-1", RevocationTrigger.ORGANIZATION_SUSPENDED, "admin-1").block();

        assertThat(repository.existsById(RevocationType.USER.documentId(buyerKeycloakId)).block()).isTrue();
        assertThat(repository.existsById(RevocationType.USER.documentId(otherKeycloakId)).block())
                .as("a member of another organization keeps their access").isFalse();
        assertThat(repository.findById(RevocationType.USER.documentId(buyerKeycloakId)).block().getTrigger())
                .isEqualTo(RevocationTrigger.ORGANIZATION_SUSPENDED);
    }

    @Test
    @DisplayName("a member already removed is not cut again by the organization's suspension")
    void removedMembersAreSkipped() {
        member(buyerId, "org-1", MemberStatus.REMOVED);

        service.cutOrganizationMembers("org-1", RevocationTrigger.ORGANIZATION_SUSPENDED, "admin-1").block();

        assertThat(repository.count().block()).isZero();
    }

    @Test
    @DisplayName("cutting sessions revokes each current session and not the subject, so the next sign-in is not refused")
    void cutSessionsLeavesTheSubjectAlone() {
        keycloak.openSession(buyerKeycloakId, "sid-a");
        keycloak.openSession(buyerKeycloakId, "sid-b");

        service.cutSessions(buyerId, RevocationTrigger.CONTACT_CHANGED, null).block();

        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-a")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-b")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.USER.documentId(buyerKeycloakId)).block()).isFalse();
        assertThat(accessAudits()).isEmpty();
    }

    @Test
    @DisplayName("a credential change ends every session except the one that made the change")
    void credentialChangeKeepsTheChangingSession() {
        keycloak.openSession(buyerKeycloakId, "sid-keep");
        keycloak.openSession(buyerKeycloakId, "sid-old-phone");
        keycloak.openSession(buyerKeycloakId, "sid-old-laptop");

        Long ended = service.cutOtherSessions(buyerKeycloakId, "sid-keep", RevocationTrigger.CREDENTIAL_CHANGE).block();

        assertThat(ended).isEqualTo(2L);
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-old-phone")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-old-laptop")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-keep")).block()).isFalse();
        assertThat(redis.hasKey(RevocationKeys.session("sid-old-phone")).block()).isTrue();
        assertThat(keycloak.sessions(null, buyerKeycloakId).map(view -> view.id()).collectList().block())
                .containsExactly("sid-keep");
        assertThat(accessAudits()).isEmpty();
    }

    @Test
    @DisplayName("a credential set during a sign-in, which names no session, does not end the session that sign-in created")
    void firstSignInCredentialDoesNotEndTheNewSession() {
        java.time.Instant changedAt = java.time.Instant.parse("2026-10-14T14:20:00Z");
        keycloak.openSession(buyerKeycloakId, "sid-before", changedAt.minusSeconds(3600));
        keycloak.openSession(buyerKeycloakId, "sid-new-signin", changedAt.plusSeconds(2));

        Long ended = service.cutOtherSessions(buyerKeycloakId, null, changedAt, RevocationTrigger.CREDENTIAL_CHANGE)
                .block();

        assertThat(ended).isEqualTo(1L);
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-before")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-new-signin")).block()).isFalse();
        assertThat(keycloak.sessions(null, buyerKeycloakId).map(view -> view.id()).collectList().block())
                .containsExactly("sid-new-signin");
    }

    @Test
    @DisplayName("a credential change for a user this platform does not know ends nothing")
    void unknownKeycloakUserEndsNothing() {
        assertThat(service.cutOtherSessions("unknown-kc-user", "sid-x", RevocationTrigger.CREDENTIAL_CHANGE).block())
                .isZero();
    }

    // ---- the administrator ------------------------------------------------------------------------------

    @Test
    @DisplayName("an administrator's revocation by account id needs a reason of twenty characters and persists nothing without one")
    void administratorMustWriteAReason() {
        assertThatThrownBy(() -> service.revokeAccountManually(buyerId, "no", "admin-1").block())
                .isInstanceOfSatisfying(TranslatedRefusal.class,
                        refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED));
        assertThat(repository.count().block()).isZero();
        assertThat(accessAudits()).isEmpty();

        RevocationRecord record = service.revokeAccountManually(buyerId,
                "reported stolen phone by the organizer", "admin-1").block();

        assertThat(record.getId()).isEqualTo(RevocationType.USER.documentId(buyerKeycloakId));
        assertThat(record.getTrigger()).isEqualTo(RevocationTrigger.ADMIN_REVOKE);
        List<AuditLog> audits = accessAudits();
        assertThat(audits).as("an administrator's revocation is audited").hasSize(1);
        assertThat(audits.get(0).getMetadata()).containsEntry("trigger", "ADMIN_REVOKE")
                .containsEntry("type", "USER");
        assertThat(audits.get(0).getMetadata().get("identifier"))
                .as("the audit row carries a masked identifier, never the raw one")
                .doesNotContain(buyerKeycloakId);
        assertThat(audits.get(0).getPerformedBy()).isEqualTo("admin-1");
        assertThat(audits.get(0).getUserId()).as("the row is filed under the account").isEqualTo(buyerId);
    }

    @Test
    @DisplayName("revoking an account that has no sign-in is refused as unknown")
    void accountWithoutSignInIsRefused() {
        db.template.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(otherId)),
                new org.springframework.data.mongodb.core.query.Update().unset("keycloakUserId"),
                com.pml.identity.domain.model.User.class).block();

        assertThatThrownBy(() -> service.revokeAccountManually(otherId, "no sign-in to be revoked at all", "admin-1").block())
                .isInstanceOfSatisfying(TranslatedRefusal.class,
                        refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.USER_UNKNOWN));
    }

    @Test
    @DisplayName("an administrator can cut one token or one session by identifier")
    void administratorCutsATokenOrASession() {
        service.revokeManually(RevocationType.TOKEN, "jti-admin", "token seen in a public paste, rotate now", "admin-1")
                .block();
        service.revokeManually(RevocationType.SESSION, "sid-admin", "session opened from an unknown device", "admin-1")
                .block();

        assertThat(redis.hasKey(RevocationKeys.token("jti-admin")).block()).isTrue();
        assertThat(redis.hasKey(RevocationKeys.session("sid-admin")).block()).isTrue();
        assertThat(accessAudits()).hasSize(2);
    }

    // ---- the person ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("signing out everywhere ends the person's other sessions and leaves the current one and everyone else's")
    void signOutEverywhereKeepsTheCurrentSession() {
        keycloak.openSession(buyerKeycloakId, "sid-current");
        keycloak.openSession(buyerKeycloakId, "sid-phone");
        keycloak.openSession(otherKeycloakId, "sid-someone-else");

        Long ended = service.signOutOtherSessions(buyerKeycloakId, "sid-current").block();

        assertThat(ended).isEqualTo(1L);
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-phone")).block()).isTrue();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-current")).block()).isFalse();
        assertThat(repository.existsById(RevocationType.SESSION.documentId("sid-someone-else")).block())
                .as("another person's session is never touched").isFalse();
    }

    // ---- the log ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the revocation log lists what is still in force, newest first, by type, and drops what has lapsed")
    void logListsLiveRevocations() {
        service.revokeManually(RevocationType.TOKEN, "jti-1", "first revocation written by an administrator", "a").block();
        service.revokeManually(RevocationType.USER, "sub-1", "second revocation written by an administrator", "a").block();
        db.template.insert(RevocationRecord.builder().id("SESSION:lapsed").type(RevocationType.SESSION)
                .value("lapsed").reason("a record whose window has passed already").revokedBy("a")
                .trigger(RevocationTrigger.LOGOUT).revokedAt(NOW.minus(Duration.ofHours(1)))
                .expiresAt(NOW.minus(Duration.ofMinutes(54))).build()).block();

        var all = service.active(null, OffsetPaginationInput.defaults()).block();
        assertThat(all.content()).extracting(view -> view.id())
                .containsExactlyInAnyOrder("TOKEN:jti-1", "USER:sub-1");
        assertThat(all.pageInfo().totalElements()).isEqualTo(2);

        var tokens = service.active(RevocationType.TOKEN,
                new OffsetPaginationInput(0, 10, "revokedAt", SortDirection.DESC)).block();
        assertThat(tokens.content()).extracting(view -> view.id()).containsExactly("TOKEN:jti-1");
    }

    @Test
    @DisplayName("a page larger than the platform's ceiling is refused, not clamped")
    void pageSizeIsBounded() {
        assertThatThrownBy(() -> new OffsetPaginationInput(0, 1_000_000, null, null))
                .isInstanceOf(RuntimeException.class);
    }
}
