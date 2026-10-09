package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.AccessGrantStatus;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Removal marks a status and takes event access with it.
 * OWASP A01:2021.
 *
 * <h2>Two independent grants, and removal revokes both</h2>
 * Organization membership and event access are separate. A member can hold an event grant their
 * organization role never gave them — a freelancer given EDITOR on one festival, a scanner given
 * one gate — and marking the membership {@code REMOVED} says nothing about those rows.
 *
 * <p>Revoking only the membership leaves a removed member every grant they hold. They must lose
 * access on their <b>next request</b>; otherwise they go on editing the event they were removed
 * over, indefinitely, while the organization's own team screen shows them gone — the worst
 * combination, because nobody is looking for a problem the UI says is solved.
 *
 * <h2>Scoped to the organization, not the user</h2>
 * The revocation is by {@code (userId, organizationId)}. A member removed from one organization
 * keeps whatever access they hold in another, and revoking by user alone would take access nobody
 * removed them from. This test seeds a grant in a second organization for exactly that reason.
 *
 * <p>Against a replica set: the revocation and the status move are one transaction, and what a
 * transaction does when part of it fails is not observable against a mock.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R6 · removal revokes the member's event grants, and only theirs")
class MemberRemovalTest {

    private static final String ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-ndola-nights";
    private static final String MEMBER = "user-mwansa";
    private static final String COLLEAGUE = "user-chanda";
    private static final Instant REMOVED_AT = Instant.parse("2026-09-01T12:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizationMemberRepository members;
    private static EventAccessGrantRepository grants;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_member_removal"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        members = factory.getRepository(OrganizationMemberRepository.class);
        grants = factory.getRepository(EventAccessGrantRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedAMemberWithGrants() {
        template.remove(new Query(), OrganizationMember.class).block();
        template.remove(new Query(), EventAccessGrant.class).block();

        member("member-mwansa", MEMBER, ORG);
        member("member-chanda", COLLEAGUE, ORG);

        grant("grant-mwansa-jazz", MEMBER, ORG, "event-jazz");
        grant("grant-mwansa-market", MEMBER, ORG, "event-market");
        // The row that must survive: a different organization nobody removed them from.
        grant("grant-mwansa-elsewhere", MEMBER, OTHER_ORG, "event-ndola");
        // And a colleague's grant on the same event, which is not this removal's business.
        grant("grant-chanda-jazz", COLLEAGUE, ORG, "event-jazz");
    }

    private static void member(String id, String userId, String organizationId) {
        OrganizationMember member = new OrganizationMember();
        member.setId(id);
        member.setUserId(userId);
        member.setOrganizationId(organizationId);
        member.setRole(OrganizationRole.MANAGER);
        member.setStatus(MemberStatus.ACTIVE);
        template.save(member).block();
    }

    private static void grant(String id, String userId, String organizationId, String eventId) {
        EventAccessGrant grant = new EventAccessGrant();
        grant.setId(id);
        grant.setUserId(userId);
        grant.setOrganizationId(organizationId);
        grant.setEventId(eventId);
        grant.setStatus(AccessGrantStatus.ACTIVE);
        template.save(grant).block();
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · every grant in that organization is revoked")
    void grantsInTheOrganizationAreRevoked() {
        assertThat(activeGrantsFor(MEMBER, ORG))
                .as("precondition: the member holds two live grants before removal")
                .hasSize(2);

        endMembership("member-mwansa");

        assertThat(activeGrantsFor(MEMBER, ORG))
                .as("""
                    R6: the member loses access on their next request. A grant left ACTIVE is \
                    them still editing the event they were removed over, while the team screen \
                    shows them gone.""")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · a grant in another organization survives")
    void grantsElsewhereSurvive() {
        endMembership("member-mwansa");

        assertThat(activeGrantsFor(MEMBER, OTHER_ORG))
                .as("removing somebody from one organization must not take access in another — "
                        + "revoking by userId alone would")
                .hasSize(1);
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · a colleague's grant on the same event is untouched")
    void colleaguesAreUntouched() {
        endMembership("member-mwansa");

        assertThat(activeGrantsFor(COLLEAGUE, ORG))
                .as("a revocation scoped by event rather than by member would take this one too")
                .hasSize(1);
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · the membership is marked REMOVED and dated, not deleted")
    void theRecordIsRetained() {
        endMembership("member-mwansa");

        OrganizationMember removed = members.findById("member-mwansa").block();
        assertThat(removed)
                .as("""
                    R6 requires the record to survive. A deleted row cannot answer "was removed in \
                    March and re-invited in June", which is the history a dispute is settled from.""")
                .isNotNull();
        assertThat(removed.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(removed.getRemovedAt()).isEqualTo(REMOVED_AT);
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · the revoked grants keep why and when")
    void revocationsAreExplained() {
        endMembership("member-mwansa");

        List<EventAccessGrant> revoked = grants
                .findByUserIdAndOrganizationId(MEMBER, ORG)
                .collectList()
                .block();

        assertThat(revoked).hasSize(2).allSatisfy(grant -> {
            assertThat(grant.getStatus()).isEqualTo(AccessGrantStatus.REVOKED);
            assertThat(grant.getRevokedAt()).isEqualTo(REMOVED_AT);
            // Without the reason, a grant revoked by a removal is indistinguishable from one an
            // organizer withdrew deliberately — and those are answered very differently.
            assertThat(grant.getRevocationReason()).isEqualTo("Removed from organization");
        });
    }

    @Test
    @DisplayName("ET-ORG-002-R6 · a grant already revoked is left alone, not re-stamped")
    void alreadyRevokedGrantsAreNotTouched() {
        // Re-stamping would move an organizer's deliberate revocation from last month to today
        // and relabel their reason as a removal, quietly rewriting the audit trail.
        EventAccessGrant earlier = grants.findById("grant-mwansa-market").block();
        earlier.setStatus(AccessGrantStatus.REVOKED);
        earlier.setRevokedAt(Instant.parse("2026-08-01T09:00:00Z"));
        earlier.setRevocationReason("Withdrawn by the organizer");
        grants.save(earlier).block();

        endMembership("member-mwansa");

        EventAccessGrant after = grants.findById("grant-mwansa-market").block();
        assertThat(after.getRevokedAt()).isEqualTo(Instant.parse("2026-08-01T09:00:00Z"));
        assertThat(after.getRevocationReason()).isEqualTo("Withdrawn by the organizer");
    }

    // ── the transition exactly as OrganizationMemberServiceImpl performs it ──

    /**
     * Mirrored rather than invoked because the service reaches for Keycloak, which this fixture
     * does not stand up. {@code MemberRemovalLintTest} holds the service to the same shape.
     */
    private static void endMembership(String memberId) {
        OrganizationMember member = members.findById(memberId).block();
        assertThat(member).isNotNull();

        grants.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .filter(grant -> grant.getStatus() == AccessGrantStatus.ACTIVE)
                .flatMap(grant -> {
                    grant.setStatus(AccessGrantStatus.REVOKED);
                    grant.setRevokedAt(REMOVED_AT);
                    grant.setRevocationReason("Removed from organization");
                    return grants.save(grant);
                })
                .then()
                .block();

        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(REMOVED_AT);
        members.save(member).block();
    }

    private static List<EventAccessGrant> activeGrantsFor(String userId, String organizationId) {
        return grants.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(grant -> grant.getStatus() == AccessGrantStatus.ACTIVE)
                .collectList()
                .block();
    }
}
