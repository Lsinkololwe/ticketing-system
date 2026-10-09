package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.enums.TransferStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.OwnershipTransferRepository;
import com.pml.identity.service.impl.OwnershipTransferServiceImpl;
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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The transfer writes the workflow's activities make, against a real replica set.
 *
 * <p>Each write is run twice where a retried activity would run it twice. The case with teeth is the
 * nominee who became ineligible between nomination and confirmation: the claim is inside the
 * transaction, so the refusal rolls it back and the transfer is still pending, not spent.
 */
@Tag("L2")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R7 · transfer writes hand over once, roll back whole, and close only for the right party")
class OwnershipTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final String ORG = "org-transfer";
    private static final String OWNER = "owner-transfer";
    private static final String NOMINEE = "nominee-transfer";
    private static final String TRANSFER = "transfer-service-1";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OwnershipTransferServiceImpl transfers;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "identity_transfer_service");
        template = new ReactiveMongoTemplate(factory);
        ReactiveMongoRepositoryFactory repositories = new ReactiveMongoRepositoryFactory(template);
        Clock clock = TestClock.frozenAt(NOW);
        transfers = new OwnershipTransferServiceImpl(
                repositories.getRepository(OwnershipTransferRepository.class),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(factory)),
                template,
                clock,
                repositories.getRepository(OrganizationRepository.class),
                repositories.getRepository(OrganizationMemberRepository.class));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Organization.class).block();
        template.remove(new Query(), OrganizationMember.class).block();
        template.remove(new Query(), OwnershipTransferRequest.class).block();
        template.save(Organization.builder().id(ORG).name("Transfer Co").slug("transfer-co").ownerId(OWNER).build()).block();
        member("member-owner", OWNER, OrganizationRole.OWNER, MemberStatus.ACTIVE);
        member("member-nominee", NOMINEE, OrganizationRole.ADMIN, MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("R7 · initiating twice under one id writes one pending transfer, expiring in three days")
    void initiateIsIdempotent() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, "retiring").block();
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, "retiring").block();

        assertThat(template.count(new Query(), OwnershipTransferRequest.class).block()).isEqualTo(1);
        assertThat(transfer().getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
    }

    @Test
    @DisplayName("R7 · a suspended nominee is refused, and nothing is written")
    void anIneligibleNomineeIsRefused() {
        member("member-nominee", NOMINEE, OrganizationRole.ADMIN, MemberStatus.SUSPENDED);

        assertRefused(() -> transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block(), ErrorCode.TRANSFER_TARGET_INELIGIBLE);
        assertThat(template.count(new Query(), OwnershipTransferRequest.class).block()).isZero();
    }

    @Test
    @DisplayName("R7 · completing twice hands over once: one owner, the previous owner an ADMIN")
    void completeHandsOverOnce() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block();

        transfers.complete(TRANSFER).block();
        OwnershipTransferRequest again = transfers.complete(TRANSFER).block();

        assertThat(again.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(role("member-owner")).isEqualTo(OrganizationRole.ADMIN);
        assertThat(role("member-nominee")).isEqualTo(OrganizationRole.OWNER);
        assertThat(template.findById(ORG, Organization.class).block().getOwnerId()).isEqualTo(NOMINEE);
    }

    @Test
    @DisplayName("R7 · a nominee who became ineligible rolls the claim back: the transfer stays pending and nobody moves")
    void anIneligibleConfirmationRollsBack() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block();
        member("member-nominee", NOMINEE, OrganizationRole.ADMIN, MemberStatus.REMOVED);

        assertRefused(() -> transfers.complete(TRANSFER).block(), ErrorCode.TRANSFER_TARGET_INELIGIBLE);

        assertThat(transfer().getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(role("member-owner")).isEqualTo(OrganizationRole.OWNER);
    }

    @Test
    @DisplayName("§4 · an expired transfer cannot be completed")
    void anExpiredTransferIsNotCompleted() {
        template.save(OwnershipTransferRequest.builder().id(TRANSFER).organizationId(ORG).currentOwnerId(OWNER)
                .newOwnerId(NOMINEE).transferToken("tok-expired").status(TransferStatus.PENDING)
                .expiresAt(NOW.minusSeconds(1)).build()).block();

        assertRefused(() -> transfers.complete(TRANSFER).block(), ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(role("member-nominee")).isEqualTo(OrganizationRole.ADMIN);
    }

    @Test
    @DisplayName("R7 · only the nominee declines, and declining twice is one decline")
    void declineByTheNomineeOnly() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block();

        assertRefused(() -> transfers.decline(TRANSFER, OWNER).block(), ErrorCode.ACTOR_NOT_PERMITTED);
        transfers.decline(TRANSFER, NOMINEE).block();

        assertThat(transfers.decline(TRANSFER, NOMINEE).block().getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(transfer().getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("§4 · expiry never overrides a completed transfer")
    void expireLeavesACompletedTransfer() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block();
        transfers.complete(TRANSFER).block();

        assertThat(transfers.expire(TRANSFER).block().getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    @DisplayName("R8 · a mirror that never landed marks both parties' memberships for the repair Schedule")
    void mirrorPendingMarksBothParties() {
        transfers.initiate(TRANSFER, ORG, OWNER, NOMINEE, null).block();

        transfers.markMirrorPending(TRANSFER).block();

        assertThat(template.findById("member-owner", OrganizationMember.class).block().isMirrorPending()).isTrue();
        assertThat(template.findById("member-nominee", OrganizationMember.class).block().isMirrorPending()).isTrue();
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static void member(String id, String userId, OrganizationRole role, MemberStatus status) {
        template.save(OrganizationMember.builder().id(id).organizationId(ORG).userId(userId).role(role).status(status).build())
                .block();
    }

    private static OrganizationRole role(String memberId) {
        return template.findById(memberId, OrganizationMember.class).block().getRole();
    }

    private static OwnershipTransferRequest transfer() {
        return template.findById(TRANSFER, OwnershipTransferRequest.class).block();
    }

    private static void assertRefused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainRefusal.class)
                .satisfies(error -> assertThat(((DomainRefusal) error).errorCode()).isEqualTo(code));
    }
}
