package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.shared.constants.InvitationStatus;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One invitation token yields exactly one acceptance, under real contention.
 *
 * <h2>The race is the ordinary case, not the attack</h2>
 * An invitation forwarded into a group chat is opened by several people within seconds of each
 * other, and a recipient who taps the link twice on a slow connection produces the same race
 * alone. Reading the status, finding it {@code PENDING} and then writing the membership leaves the
 * entire window between the read and the write open: both callers see {@code PENDING}, both
 * proceed, and the organization gains two members from one invitation — one of whom nobody
 * invited.
 *
 * <h2>What is actually asserted</h2>
 * The claim is a conditional update matching only while the invitation is still {@code PENDING}.
 * Under {@code n} parallel callers, exactly one update must report a modified row. Asserting "at
 * most one" would pass against an implementation that claims for nobody; asserting "at least one"
 * would pass against one that claims for everybody. **Exactly one**, both bounds.
 *
 * <p>Against the real replica set with real threads, because a conditional update is a claim about
 * what MongoDB does when two writes arrive together — which is the one thing a mock cannot tell
 * you. {@link RepeatedTest} runs it several times: a race that resolves correctly once may have
 * been lucky.
 */
@Tag("L5")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R5 · one token, one acceptance, however many callers arrive together")
class ConcurrentAcceptanceTest {

    private static final String INVITATION = "invite-forwarded-to-a-group-chat";
    private static final int CALLERS = 24;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_concurrent_acceptance"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOnePendingInvitation() {
        template.remove(new Query(), TeamInvitation.class).block();

        TeamInvitation invitation = new TeamInvitation();
        invitation.setId(INVITATION);
        invitation.setOrganizationId("org-kabwe-collective");
        invitation.setEmail("mwansa@example.zm");
        invitation.setProposedRole(OrganizationRole.ADMIN);
        invitation.setInvitationToken("tok-" + INVITATION);
        invitation.setExpiresAt(Instant.parse("2026-09-08T09:00:00Z"));
        invitation.setStatus(InvitationStatus.PENDING);
        template.save(invitation).block();
    }

    @RepeatedTest(3)
    @DisplayName("ET-ORG-002-R5 · exactly one of twenty-four parallel callers claims the token")
    void exactlyOneClaimWins() {
        Concurrency.Outcome<Boolean> outcome =
                Concurrency.inParallel(CALLERS, caller -> claim());

        long winners = outcome.successes().stream().filter(Boolean::booleanValue).count();

        assertThat(winners)
                .as("""
                    Exactly one, both bounds. More than one means two people joined on one \
                    invitation and the second was never invited by anybody. Fewer means a valid \
                    link was refused for everyone, and the invitee has no way in.""")
                .isEqualTo(1);
        assertThat(outcome.failures())
                .as("losing the race is an ordinary outcome and must not raise")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · the losers see a resolved invitation, not a PENDING one")
    void losersSeeTheResolvedState() {
        Concurrency.inParallel(CALLERS, caller -> claim());

        TeamInvitation after = template.findById(INVITATION, TeamInvitation.class).block();
        assertThat(after).isNotNull();
        assertThat(after.getStatus())
                .as("the winner's claim is what every later caller reads")
                .isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(after.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · a second wave after the first claims nothing")
    void theTokenIsSpent() {
        // Single-use is the property; the race is only how it gets tested. A link opened again
        // an hour later must be as spent as one opened a millisecond late.
        assertThat(claim()).isTrue();
        assertThat(claim())
                .as("a token that can be claimed twice is a token that admits two members")
                .isFalse();
    }

    @Test
    @DisplayName("ET-ORG-002-R5 · a claim on an invitation that was revoked fails")
    void revokedInvitationsCannotBeClaimed() {
        template.updateFirst(
                        Query.query(Criteria.where("_id").is(INVITATION)),
                        Update.update("status", InvitationStatus.REVOKED),
                        TeamInvitation.class)
                .block();

        assertThat(claim())
                .as("revocation must survive somebody holding the link — otherwise revoking it "
                        + "does nothing to whoever already has it")
                .isFalse();
    }

    /**
     * The claim exactly as {@code TeamInvitationServiceImpl.claimPending} performs it.
     *
     * <p>Mirrored rather than invoked because the service reaches for Keycloak-backed
     * collaborators this fixture does not stand up. What is under test is the conditional update
     * itself — the property that only one write matches — and
     * {@code InvitationAcceptanceLintTest} holds the service to the same shape.
     */
    private static boolean claim() {
        return Boolean.TRUE.equals(template.updateFirst(
                        Query.query(Criteria.where("_id").is(INVITATION)
                                .and("status").is(InvitationStatus.PENDING)),
                        Update.update("status", InvitationStatus.ACCEPTED)
                                .set("acceptedAt", Instant.parse("2026-09-02T09:00:00Z")),
                        TeamInvitation.class)
                .map(result -> result.getModifiedCount() == 1)
                .block());
    }
}
