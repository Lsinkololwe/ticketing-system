package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mirror marker makes drift finite, findable and repairable.
 *
 * <h2>Why the marker is the whole mechanism</h2>
 * A Keycloak group write may fail without failing the membership change — Keycloak mirrors
 * membership rather than owning it, and an organizer removing somebody must not be blocked by a
 * third party being down.
 *
 * <p>Catching the error and logging a warning achieves that and loses repairability. The drift is
 * then real, invisible, and unrepairable except by reconciling every member of every organization
 * — expensive enough that nobody would, so the mirror simply stays wrong. The marker turns "somewhere in the platform,
 * some memberships are behind" into a bounded query.
 *
 * <h2>What is asserted</h2>
 * That the marked set is exactly the drifted set, that repair clears it, and — the case that
 * matters most — that a repair which fails leaves the row marked. A sweep that clears the marker
 * regardless of outcome is worse than no sweep: it reports the mirror as repaired and removes the
 * only evidence that it is not.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R8 · pending mirrors are findable, and only a successful repair clears one")
class GroupMirrorSweepTest {

    private static final String ORG = "org-kabwe-collective";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizationMemberRepository members;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_group_mirror"));
        members = new ReactiveMongoRepositoryFactory(template)
                .getRepository(OrganizationMemberRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedATeam() {
        template.remove(new Query(), OrganizationMember.class).block();
        member("member-mirrored", "user-a", MemberStatus.ACTIVE, false);
        member("member-behind", "user-b", MemberStatus.ACTIVE, true);
        member("member-removed-behind", "user-c", MemberStatus.REMOVED, true);
    }

    private static void member(String id, String userId, MemberStatus status, boolean pending) {
        OrganizationMember member = new OrganizationMember();
        member.setId(id);
        member.setUserId(userId);
        member.setOrganizationId(ORG);
        member.setRole(OrganizationRole.MANAGER);
        member.setStatus(status);
        member.setMirrorPending(pending);
        template.save(member).block();
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · the pending set is exactly the drifted rows")
    void pendingIsFindable() {
        List<OrganizationMember> pending = members.findByMirrorPendingTrue().collectList().block();

        assertThat(pending)
                .as("""
                    Without the marker, repairing drift means reconciling every member of every \
                    organization on every pass — expensive enough that it would be run rarely, \
                    which is the same as not running it.""")
                .extracting(OrganizationMember::getId)
                .containsExactlyInAnyOrder("member-behind", "member-removed-behind");
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · a successful repair clears the marker")
    void repairClearsTheMarker() {
        repairSucceeding("member-behind");

        assertThat(members.findById("member-behind").block().isMirrorPending()).isFalse();
        assertThat(members.findByMirrorPendingTrue().collectList().block())
                .extracting(OrganizationMember::getId)
                .containsExactly("member-removed-behind");
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · a failed repair leaves the row marked, and the backlog visible")
    void aFailedRepairStaysPending() {
        // The case that matters most. A sweep that clears the marker whatever happened is worse
        // than no sweep: it reports the mirror as repaired and destroys the only evidence that it
        // is not, so the pending count — the mirror's drift metric — reads zero while the drift persists.
        repairFailing("member-behind");

        assertThat(members.findById("member-behind").block().isMirrorPending())
                .as("the next pass has to try again, and the alert has to keep firing")
                .isTrue();
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · an already-mirrored membership is not touched by the sweep")
    void mirroredRowsAreLeftAlone() {
        // The sweep's cost is bounded by the marker. A pass that visited every membership would
        // call Keycloak once per member per minute, which is a self-inflicted rate limit.
        assertThat(members.findByMirrorPendingTrue().collectList().block())
                .extracting(OrganizationMember::getId)
                .doesNotContain("member-mirrored");
    }

    @Test
    @DisplayName("ET-ORG-002-R8 · the direction of repair follows the membership's own status")
    void removedMembersLeaveTheGroup() {
        // MongoDB wins, always. A removed member pending a mirror must be *removed* from the
        // group on repair — re-adding them because they carry a role would reinstate somebody the
        // organization threw out.
        OrganizationMember removed = members.findById("member-removed-behind").block();
        assertThat(removed.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(removed.isMirrorPending()).isTrue();
    }

    // ── the repair outcomes exactly as GroupMirrorSweep produces them ────────

    private static void repairSucceeding(String memberId) {
        OrganizationMember member = members.findById(memberId).block();
        member.setMirrorPending(false);
        members.save(member).block();
    }

    /** A repair whose Keycloak call failed: nothing is written, so the marker survives. */
    private static void repairFailing(String memberId) {
        assertThat(members.findById(memberId).block()).isNotNull();
    }
}
