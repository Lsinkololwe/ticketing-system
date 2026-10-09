package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.service.OrganizationApprovalService;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
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
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The MongoDB half of the approval saga against a real replica set.
 *
 * <p>Each step is run twice where a retried activity would run it twice, and each compensation is
 * asserted to undo only what its step did. The sequence over these writes is
 * {@code OrganizerOnboardingWorkflowTest}.
 */
@Tag("L2")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R6 · approval writes transition once, stage one envelope, and compensate only their own work")
class OrganizationApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final String ORG = "org-approval";
    private static final String OWNER = "owner-approval";
    private static final String REVIEWER = "admin-approval";
    private static final String OUTBOX = "identity_outbox";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TransactionalOperator transaction;
    private static Clock clock;

    private OrganizationApprovalService approvals;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory = new SimpleReactiveMongoDatabaseFactory(client, "identity_approval_saga");
        template = new ReactiveMongoTemplate(factory);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));
        clock = TestClock.frozenAt(NOW);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Organization.class).block();
        template.remove(new Query(), OrganizationMember.class).block();
        template.remove(new Query(), User.class).block();
        template.remove(new Query(), Document.class, OUTBOX).block();
        template.save(Organization.builder().id(ORG).name("Kabwe Collective").slug("kabwe-collective")
                .ownerId(OWNER).status(OrganizationStatus.PENDING_REVIEW).build()).block();
        saveOwner(EnumSet.of(UserType.CUSTOMER));
        approvals = new OrganizationApprovalService(template, transaction, new Outbox(template, OUTBOX, clock), clock);
    }

    @Test
    @DisplayName("step 1 · activating twice moves the status once and records the approval")
    void activateTwiceTransitionsOnce() {
        approvals.activate(ORG, REVIEWER).block();
        Organization again = approvals.activate(ORG, REVIEWER).block();

        assertThat(again.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(again.getApprovalSagaStep()).isEqualTo(1);
        assertThat(again.getApprovedAt()).isEqualTo(NOW);
        assertThat(again.getReviewedBy()).isEqualTo(REVIEWER);
    }

    @Test
    @DisplayName("R5 · an organization not under review is refused, and nothing is written")
    void activateRefusesOutsideReview() {
        template.save(organization().toBuilder().status(OrganizationStatus.DRAFT).build()).block();

        assertRefused(() -> approvals.activate(ORG, REVIEWER).block(), ErrorCode.ORGANIZATION_STATE_INVALID);
        assertThat(organization().getStatus()).isEqualTo(OrganizationStatus.DRAFT);
    }

    @Test
    @DisplayName("R5 · a rejection is recorded once, and a second decision afterwards is refused")
    void decisionsAreRecordedOnce() {
        approvals.decide(ORG, REVIEWER, "business not found", OrganizationStatus.REJECTED).block();
        approvals.decide(ORG, REVIEWER, "business not found", OrganizationStatus.REJECTED).block();

        assertThat(organization().getStatus()).isEqualTo(OrganizationStatus.REJECTED);
        assertThat(organization().getRejectionReason()).isEqualTo("business not found");
        assertRefused(() -> approvals.decide(ORG, REVIEWER, "again", OrganizationStatus.CHANGES_REQUESTED).block(),
                ErrorCode.ORGANIZATION_STATE_INVALID);
    }

    @Test
    @DisplayName("step 2 · an owner with no membership gets one row, however often the step runs; compensation removes it")
    void ownerMembershipCreatedOnceAndRemoved() {
        approvals.ensureOwnerMembership(ORG).block();
        approvals.ensureOwnerMembership(ORG).block();

        List<OrganizationMember> members = members();
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(OnboardingStep.ownerMembershipId(ORG));
        assertThat(members.get(0).getRole()).isEqualTo(OrganizationRole.OWNER);
        assertThat(organization().getApprovalSagaStep()).isEqualTo(2);

        approvals.removeOwnerMembership(ORG).block();
        assertThat(members()).isEmpty();
    }

    @Test
    @DisplayName("R6 · compensation never removes an owner membership that existed before the approval")
    void aPriorMembershipSurvivesCompensation() {
        template.save(OrganizationMember.builder().id("member-owner").organizationId(ORG).userId(OWNER)
                .role(OrganizationRole.OWNER).status(MemberStatus.ACTIVE).build()).block();

        approvals.ensureOwnerMembership(ORG).block();
        approvals.removeOwnerMembership(ORG).block();

        assertThat(members()).extracting(OrganizationMember::getId).containsExactly("member-owner");
    }

    @Test
    @DisplayName("step 3 · ORGANIZER is granted once, and compensation removes only a role the approval granted")
    void promoteAndRestore() {
        approvals.promoteOwner(ORG).block();
        approvals.promoteOwner(ORG).block();

        assertThat(owner().hasRole(UserType.ORGANIZER)).isTrue();
        assertThat(organization().getApprovalOwnerWasOrganizer()).isFalse();

        approvals.restoreOwnerUserType(ORG).block();
        assertThat(owner().hasRole(UserType.ORGANIZER)).isFalse();
    }

    @Test
    @DisplayName("R6 · an owner who was already an ORGANIZER keeps the role through a compensation")
    void aPriorOrganizerKeepsTheRole() {
        saveOwner(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER));

        approvals.promoteOwner(ORG).block();
        approvals.restoreOwnerUserType(ORG).block();

        assertThat(organization().getApprovalOwnerWasOrganizer()).isTrue();
        assertThat(owner().hasRole(UserType.ORGANIZER)).isTrue();
    }

    @Test
    @DisplayName("step 6 · marking approved twice stages exactly one identity.OrganizationApproved")
    void markApprovedStagesOneEnvelope() {
        approvals.activate(ORG, REVIEWER).block();

        approvals.markApproved(ORG, "group-kabwe").block();
        approvals.markApproved(ORG, "group-kabwe").block();

        assertThat(organization().getApprovalSagaStep()).isEqualTo(6);
        assertThat(organization().getKeycloakGroupId()).isEqualTo("group-kabwe");
        List<Document> rows = template.find(new Query(), Document.class, OUTBOX).collectList().block();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getString("eventType")).isEqualTo("identity.OrganizationApproved");
        assertThat(rows.get(0).get("payload", Document.class))
                .containsEntry("organizationId", ORG)
                .containsEntry("ownerId", OWNER)
                .containsEntry("slug", "kabwe-collective");
    }

    @Test
    @DisplayName("R6 · compensation returns an unfinished approval to PENDING_REVIEW with its reason, and leaves a finished one alone")
    void revertOnlyUnfinishedApprovals() {
        approvals.activate(ORG, REVIEWER).block();
        approvals.promoteOwner(ORG).block();

        Organization reverted = approvals.revertToPendingReview(ORG, "approval step 5 failed").block();
        assertThat(reverted.getStatus()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
        assertThat(reverted.getApprovalSagaStep()).isZero();
        assertThat(reverted.getApprovalSagaFailure()).isEqualTo("approval step 5 failed");
        assertThat(reverted.getApprovedAt()).isNull();
        assertThat(template.count(new Query(), Document.class, OUTBOX).block()).isZero();

        approvals.activate(ORG, REVIEWER).block();
        approvals.markApproved(ORG, "group-kabwe").block();
        assertThat(approvals.revertToPendingReview(ORG, "late").block().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    @DisplayName("the step marker is raised by a completed step and never lowered")
    void theMarkerNeverFalls() {
        approvals.markStep(ORG, OnboardingStep.GROUP_TREE).block();
        approvals.markStep(ORG, OnboardingStep.OWNER_MEMBERSHIP).block();

        assertThat(organization().getApprovalSagaStep()).isEqualTo(5);
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private static void saveOwner(EnumSet<UserType> roles) {
        template.save(User.builder().id(OWNER).username("owner-approval").email("owner-approval@example.test")
                .firstName("Mutale").lastName("Banda").roles(roles).build()).block();
    }

    private static Organization organization() {
        return template.findById(ORG, Organization.class).block();
    }

    private static User owner() {
        return template.findById(OWNER, User.class).block();
    }

    private static List<OrganizationMember> members() {
        return template.find(new Query(), OrganizationMember.class).collectList().block();
    }

    private static void assertRefused(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainRefusal.class)
                .satisfies(error -> assertThat(((DomainRefusal) error).errorCode()).isEqualTo(code));
    }
}
