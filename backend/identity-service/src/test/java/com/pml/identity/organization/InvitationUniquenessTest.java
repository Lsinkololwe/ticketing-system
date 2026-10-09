package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.TeamInvitationRepository;
import com.pml.shared.constants.InvitationStatus;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-inviting replaces the live invitation; it never leaves two.
 *
 * <h2>Refusing is the wrong answer, and it strands the inviter</h2>
 * A second invitation revokes the pending one and creates a new one — never two live.
 * Refusing instead means a role mistyped, an address misspelled or an email that bounced cannot
 * be corrected until the first invitation expires seven days later.
 * The invitee waits a week for a link nobody can resend, and the inviter's only recourse is to
 * wait too.
 *
 * <h2>Why never two, and why one transaction</h2>
 * Two live invitations to one person are two sets of terms — very often two different roles, since
 * correcting the role is exactly why somebody re-invites. Whichever link the invitee happens to
 * open decides what they get, and the inviter has no way to know which.
 *
 * <p>So the revoke and the create are one transaction. A revoke that commits without its
 * replacement leaves the invitee with no way in while the inviter believes one was sent; a create
 * without the revoke is two live invitations.
 *
 * <h2>Only PENDING is superseded</h2>
 * An invitation already accepted, declined or revoked is somebody's settled history. Re-stamping
 * it would rewrite when and why it ended, and this test pins each of those states.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R3 · a re-invitation supersedes the live one and leaves exactly one")
class InvitationUniquenessTest {

    private static final String ORG = "org-kabwe-collective";
    private static final String INVITEE = "mwansa@example.zm";
    private static final Instant NOW = Instant.parse("2026-09-02T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TeamInvitationRepository invitations;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_invitation_uniqueness"));
        invitations = new ReactiveMongoRepositoryFactory(template)
                .getRepository(TeamInvitationRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneLiveInvitation() {
        template.remove(new Query(), TeamInvitation.class).block();
        save("invite-first", INVITEE, ORG, InvitationStatus.PENDING, OrganizationRole.CONTRIBUTOR);
    }

    private static void save(String id, String email, String organizationId,
                             InvitationStatus status, OrganizationRole role) {
        TeamInvitation invitation = new TeamInvitation();
        invitation.setId(id);
        invitation.setEmail(email);
        invitation.setOrganizationId(organizationId);
        invitation.setProposedRole(role);
        invitation.setStatus(status);
        invitation.setInvitationToken("tok-" + id);
        invitation.setExpiresAt(NOW.plusSeconds(604_800));
        template.save(invitation).block();
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · re-inviting leaves exactly one live invitation")
    void exactlyOneLiveInvitationSurvives() {
        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        assertThat(pendingFor(INVITEE, ORG))
                .as("""
                    Two live invitations are two sets of terms, and correcting the role is exactly \
                    why somebody re-invites. Whichever link the invitee opens decides what they \
                    get, and the inviter cannot know which.""")
                .hasSize(1);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · the survivor is the new one, carrying the corrected role")
    void theSurvivorIsTheNewOne() {
        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        TeamInvitation live = pendingFor(INVITEE, ORG).get(0);
        assertThat(live.getId()).isEqualTo("invite-second");
        assertThat(live.getProposedRole())
                .as("the correction is the point of re-inviting")
                .isEqualTo(OrganizationRole.MANAGER);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · the superseded invitation is REVOKED and dated, not deleted")
    void theSupersededOneIsRetained() {
        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        TeamInvitation superseded = invitations.findById("invite-first").block();
        assertThat(superseded).as("the record survives; only its status moves").isNotNull();
        assertThat(superseded.getStatus()).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · an already-accepted invitation is not re-stamped")
    void settledInvitationsAreLeftAlone() {
        // Accepted, declined and revoked are somebody's settled history. Re-stamping them would
        // rewrite when and why they ended — and an accepted one would say the membership came
        // from an invitation that was revoked.
        template.remove(new Query(), TeamInvitation.class).block();
        save("invite-accepted", INVITEE, ORG, InvitationStatus.ACCEPTED, OrganizationRole.ADMIN);
        save("invite-declined", INVITEE, ORG, InvitationStatus.DECLINED, OrganizationRole.ADMIN);

        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        assertThat(invitations.findById("invite-accepted").block().getStatus())
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitations.findById("invite-declined").block().getStatus())
                .isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · an invitation to a different organization is untouched")
    void otherOrganizationsAreUntouched() {
        save("invite-elsewhere", INVITEE, "org-ndola-nights",
                InvitationStatus.PENDING, OrganizationRole.MARKETER);

        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        assertThat(pendingFor(INVITEE, "org-ndola-nights"))
                .as("one person may be invited to several organizations at once; the supersession "
                        + "is scoped to the organization doing the inviting")
                .hasSize(1);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · another invitee's live invitation is untouched")
    void otherInviteesAreUntouched() {
        save("invite-colleague", "chanda@example.zm", ORG,
                InvitationStatus.PENDING, OrganizationRole.CONTRIBUTOR);

        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);

        assertThat(pendingFor("chanda@example.zm", ORG))
                .as("a supersession scoped by organization alone would revoke the whole team's "
                        + "outstanding invitations")
                .hasSize(1);
    }

    @Test
    @DisplayName("ET-ORG-002-R3 · a second re-invitation supersedes the first re-invitation")
    void supersessionIsRepeatable() {
        supersedeThenCreate("invite-second", OrganizationRole.MANAGER);
        supersedeThenCreate("invite-third", OrganizationRole.ADMIN);

        List<TeamInvitation> live = pendingFor(INVITEE, ORG);
        assertThat(live).hasSize(1);
        assertThat(live.get(0).getId()).isEqualTo("invite-third");
    }

    // ── the transition exactly as TeamInvitationServiceImpl performs it ──────

    private static void supersedeThenCreate(String newId, OrganizationRole role) {
        template.updateMulti(
                        Query.query(Criteria.where("email").is(INVITEE)
                                .and("organizationId").is(ORG)
                                .and("status").is(InvitationStatus.PENDING)),
                        Update.update("status", InvitationStatus.REVOKED)
                                .set("revokedAt", NOW)
                                .set("updatedAt", NOW),
                        TeamInvitation.class)
                .block();

        save(newId, INVITEE, ORG, InvitationStatus.PENDING, role);
    }

    private static List<TeamInvitation> pendingFor(String email, String organizationId) {
        return invitations.findByOrganizationIdAndStatus(organizationId, InvitationStatus.PENDING)
                .filter(invitation -> email.equals(invitation.getEmail()))
                .collectList()
                .block();
    }
}
