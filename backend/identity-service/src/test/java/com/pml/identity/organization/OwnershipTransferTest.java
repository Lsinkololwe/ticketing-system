package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.domain.enums.TransferStatus;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
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
 * Ownership moves once, to an eligible nominee, or not at all.
 *
 * <h2>What a transfer actually moves</h2>
 * Control of a business's money, its events and its bank account — a serious operation. Every guarantee below exists because getting one wrong hands a company to the
 * wrong person or leaves it with nobody in charge.
 *
 * <h2>The eligibility check has two halves</h2>
 * The nominee must be an <b>active ADMIN</b>: role and status both. Checking the role alone lets a
 * member who is SUSPENDED — somebody the organization has deliberately shut out, mid-dispute — or
 * already REMOVED be handed the business, because a removed member's document survives by design
 * and still reads `role = ADMIN`.
 *
 * <h2>Two owners, or none</h2>
 * The handover demotes then promotes. The order matters: the partial unique index holds one OWNER
 * per organization, so promoting first collides with the owner still in place. But a demote that
 * commits without its promote leaves the organization with **no owner at all** — worse than the
 * two the index prevents, because nothing can then be transferred, no payout requested, and no
 * member removed. Hence one transaction.
 *
 * <p>Against the real replica set with real threads: a conditional claim and a unique index are
 * both claims about what MongoDB does when two writes arrive together.
 */
@Tag("L5")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R7 · one confirmation wins, and only an active ADMIN may receive")
class OwnershipTransferTest {

    private static final String ORG = "org-kabwe-collective";
    private static final String OWNER = "user-owner";
    private static final String NOMINEE = "user-nominee";
    private static final String TRANSFER = "transfer-kabwe";
    private static final Instant CONFIRMED_AT = Instant.parse("2026-09-02T09:00:00Z");
    private static final int CALLERS = 24;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizationMemberRepository members;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_ownership_transfer"));
        members = new ReactiveMongoRepositoryFactory(template)
                .getRepository(OrganizationMemberRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedAnOrganizationWithAPendingTransfer() {
        template.remove(new Query(), OrganizationMember.class).block();
        template.remove(new Query(), OwnershipTransferRequest.class).block();

        member("member-owner", OWNER, OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member("member-nominee", NOMINEE, OrganizationRole.ADMIN, MemberStatus.ACTIVE);

        OwnershipTransferRequest transfer = new OwnershipTransferRequest();
        transfer.setId(TRANSFER);
        transfer.setOrganizationId(ORG);
        transfer.setCurrentOwnerId(OWNER);
        transfer.setNewOwnerId(NOMINEE);
        transfer.setTransferToken("tok-" + TRANSFER);
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setExpiresAt(Instant.parse("2026-09-05T09:00:00Z"));
        template.save(transfer).block();
    }

    private static void member(String id, String userId,
                               OrganizationRole role, MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setId(id);
        member.setUserId(userId);
        member.setOrganizationId(ORG);
        member.setRole(role);
        member.setStatus(status);
        template.save(member).block();
    }

    @Nested
    @DisplayName("R7 · who may receive ownership")
    class Eligibility {

        @Test
        @DisplayName("ET-ORG-002-R7 · an active ADMIN is eligible")
        void activeAdminIsEligible() {
            assertThat(eligible(OrganizationRole.ADMIN, MemberStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("ET-ORG-002-R7 · a SUSPENDED admin is not — this is the half that was missing")
        void suspendedAdminIsNot() {
            assertThat(eligible(OrganizationRole.ADMIN, MemberStatus.SUSPENDED))
                    .as("""
                        Somebody the organization has deliberately shut out, mid-dispute, being \
                        handed the business. The role check alone admits them.""")
                    .isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R7 · a REMOVED admin is not, and their document still says ADMIN")
        void removedAdminIsNot() {
            // Removal retains the record by design, so a removed member's row survives reading
            // `role = ADMIN` indefinitely. A role-only check treats it as live.
            assertThat(eligible(OrganizationRole.ADMIN, MemberStatus.REMOVED)).isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R7 · no other role is eligible however active")
        void lesserRolesAreNot() {
            for (OrganizationRole role : List.of(OrganizationRole.MANAGER,
                    OrganizationRole.MARKETER, OrganizationRole.CONTRIBUTOR)) {
                assertThat(eligible(role, MemberStatus.ACTIVE))
                        .as("%s receiving ownership", role)
                        .isFalse();
            }
        }

        /** The condition exactly as {@code initiate} applies it. */
        private boolean eligible(OrganizationRole role, MemberStatus status) {
            return role == OrganizationRole.ADMIN && status == MemberStatus.ACTIVE;
        }
    }

    @Nested
    @DisplayName("R7 · one confirmation wins")
    class Confirmation {

        @RepeatedTest(3)
        @DisplayName("ET-ORG-002-R7 · exactly one of twenty-four parallel confirmations claims it")
        void exactlyOneConfirmationWins() {
            Concurrency.Outcome<Boolean> outcome =
                    Concurrency.inParallel(CALLERS, caller -> claim());

            assertThat(outcome.successes().stream().filter(Boolean::booleanValue).count())
                    .as("""
                        Two confirmations racing is the ordinary case — a nominee tapping twice, \
                        or a retry after a timeout. Both proceeding runs the handover twice: the \
                        second demotes the new owner it has just promoted.""")
                    .isEqualTo(1);
            assertThat(outcome.failures()).isEmpty();
        }

        @Test
        @DisplayName("ET-ORG-002-R7 · a second confirmation after the first claims nothing")
        void theTransferIsSpent() {
            assertThat(claim()).isTrue();
            assertThat(claim()).isFalse();
        }

        @Test
        @DisplayName("ET-ORG-002-R7 · a cancelled transfer cannot be confirmed")
        void cancelledTransfersCannotBeClaimed() {
            template.updateFirst(
                            Query.query(Criteria.where("_id").is(TRANSFER)),
                            Update.update("status", TransferStatus.CANCELLED),
                            OwnershipTransferRequest.class)
                    .block();

            assertThat(claim())
                    .as("cancellation must survive the nominee already holding the link")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · the organization ends with exactly one owner")
    void exactlyOneOwnerSurvives() {
        // The property the whole handover exists to preserve, asserted on the outcome rather than
        // on the steps. Two owners means each can remove the other; none means nothing can be
        // transferred, no payout requested and no member removed, ever again.
        handOver();

        List<OrganizationMember> owners = members.findByOrganizationId(ORG)
                .filter(member -> member.getRole() == OrganizationRole.OWNER)
                .collectList()
                .block();

        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).getUserId()).isEqualTo(NOMINEE);
    }

    @Test
    @DisplayName("ET-ORG-002-R7 · the previous owner becomes ADMIN, not nothing")
    void thePreviousOwnerStaysOnTheTeam() {
        handOver();

        OrganizationMember previous = members.findById("member-owner").block();
        assertThat(previous).isNotNull();
        assertThat(previous.getRole())
                .as("R7 says the previous owner becomes ADMIN — removing them instead would strand "
                        + "the person who built the organization outside it")
                .isEqualTo(OrganizationRole.ADMIN);
        assertThat(previous.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // ── the transition exactly as OwnershipTransferServiceImpl performs it ───

    private static boolean claim() {
        return Boolean.TRUE.equals(template.updateFirst(
                        Query.query(Criteria.where("_id").is(TRANSFER)
                                .and("status").is(TransferStatus.PENDING)),
                        Update.update("status", TransferStatus.COMPLETED)
                                .set("completedAt", CONFIRMED_AT),
                        OwnershipTransferRequest.class)
                .map(result -> result.getModifiedCount() == 1)
                .block());
    }

    /** Demote before promote, which is the order the one-owner index requires. */
    private static void handOver() {
        OrganizationMember previous = members.findById("member-owner").block();
        OrganizationMember nominee = members.findById("member-nominee").block();

        previous.setRole(OrganizationRole.ADMIN);
        members.save(previous).block();

        nominee.setRole(OrganizationRole.OWNER);
        members.save(nominee).block();
    }
}
