package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Approval;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Decision;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Start;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Submission;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.View;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The onboarding workflow end to end, with time skipped.
 *
 * <p>MongoDB and Keycloak are in-memory stand-ins sharing one {@link State}, so each test asserts
 * on what an approval leaves behind — the status, the membership, the role, the group, the envelope —
 * rather than on calls. The MongoDB half of the same steps is {@code OrganizationApprovalServiceTest}.
 */
@Tag("L3")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R6 · an approval completes all six steps, or reverses every step it took")
class OrganizerOnboardingWorkflowTest {

    private static final String ORG = "org-kabwe";
    private static final String OWNER = "owner-1";
    private static final String REVIEWER = "admin-1";

    private static final List<String> COMPENSATIONS_IN_ORDER = List.of(
            "removeOwnerFromGroups", "revokeOrganizerRole", "restoreOwnerUserType", "removeOwnerMembership", "revertToPendingReview");

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private State state;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        state = new State();
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(OrganizerOnboardingWorkflowImpl.class);
        worker.registerActivitiesImplementations(new FakeRecords(state), new FakeKeycloak(state));
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R6 · an approval runs the six steps in order and stages one OrganizationApproved")
    void anApprovalCompletesEverySix() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);

        View approved = workflow.approve(new Approval(ORG, REVIEWER));
        awaitClosed(workflow);

        assertThat(approved.status()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(approved.sagaStep()).isEqualTo(6);
        assertThat(state.steps).containsExactly("activate", "ensureOwnerMembership", "promoteOwner",
                "grantOrganizerRole", "ensureGroupTree", "markApproved");
        assertThat(state.membershipCreated).isTrue();
        assertThat(state.ownerOrganizer).isTrue();
        assertThat(state.realmRole).isTrue();
        assertThat(state.inOwners).isTrue();
        assertThat(state.envelopes).isEqualTo(1);
        assertThat(state.compensations).isEmpty();
    }

    @ParameterizedTest(name = "a failure at {0} leaves no partial approval, and a later approval converges")
    @EnumSource(OnboardingStep.class)
    @DisplayName("R6 · a failure at each of the six steps compensates every step before it, in reverse")
    void aFailureAtAnyStepCompensatesInReverse(OnboardingStep failing) {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);
        state.failAt = failing;

        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)))
                .satisfies(error -> {
                    if (failing != OnboardingStep.ACTIVATE) {
                        assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ORGANIZATION_STATE_INVALID.name());
                        assertThat(Refusals.messageOf(error)).contains("step " + failing.marker());
                    }
                });

        assertThat(state.status).isEqualTo(OrganizationStatus.PENDING_REVIEW);
        assertThat(state.step).isZero();
        assertThat(state.membershipCreated).as("the membership step 2 created").isFalse();
        assertThat(state.ownerOrganizer).as("the ORGANIZER user type").isFalse();
        assertThat(state.realmRole).as("the ORGANIZER realm role").isFalse();
        assertThat(state.inOwners).as("the owners group").isFalse();
        assertThat(state.envelopes).as("no OrganizationApproved for a reversed approval").isZero();
        assertThat(state.compensations).containsExactlyElementsOf(expectedCompensations(failing));
        if (failing != OnboardingStep.ACTIVATE) {
            assertThat(state.failure).contains("step " + failing.marker());
        }
        if (failing == OnboardingStep.APPROVED) {
            assertThat(state.groupTree).as("the group tree is left in place, harmless and reusable").isTrue();
        }
        assertThat(workflow.step().status()).isEqualTo(OrganizationStatus.PENDING_REVIEW);

        state.failAt = null;
        View approved = workflow.approve(new Approval(ORG, REVIEWER));
        awaitClosed(workflow);
        assertThat(approved.status()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(state.envelopes).isEqualTo(1);
        assertThat(state.realmRole && state.inOwners && state.ownerOrganizer).isTrue();
    }

    @Test
    @DisplayName("§4 · a Keycloak step is tried three times before compensation begins")
    void keycloakRetriesThreeTimes() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);
        state.failAt = OnboardingStep.REALM_ROLE;

        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)));

        assertThat(state.grantAttempts).isEqualTo(OnboardingRules.KEYCLOAK_ATTEMPTS);
    }

    @Test
    @DisplayName("R6 · a role the owner already held is not revoked by a compensation")
    void aPriorRoleSurvivesCompensation() {
        state.ownerOrganizer = true;
        state.realmRole = true;
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);
        state.failAt = OnboardingStep.GROUP_TREE;

        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)));

        assertThat(state.ownerOrganizer).isTrue();
        assertThat(state.realmRole).isTrue();
        assertThat(state.status).isEqualTo(OrganizationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("R5 · a rejection needs a reason, and the validator refuses it before it enters the history")
    void aRejectionNeedsAReason() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);
        workflow.step();

        assertThatThrownBy(() -> workflow.reject(new Decision(ORG, REVIEWER, " ")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));

        assertThat(acceptedUpdates()).isZero();
        assertThat(state.status).isEqualTo(OrganizationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("R5 · a rejection records the decision and closes the execution")
    void aRejectionCloses() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);

        assertThat(workflow.reject(new Decision(ORG, REVIEWER, "business not found")).status())
                .isEqualTo(OrganizationStatus.REJECTED);
        awaitClosed(workflow);
        assertThat(state.steps).isEmpty();
    }

    @Test
    @DisplayName("R5 · changes requested, resubmitted, then approved — one execution throughout")
    void changesRequestedThenResubmittedThenApproved() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);

        assertThat(workflow.requestChanges(new Decision(ORG, REVIEWER, "tax certificate illegible")).status())
                .isEqualTo(OrganizationStatus.CHANGES_REQUESTED);
        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ORGANIZATION_STATE_INVALID.name()));

        assertThat(workflow.resubmit(new Submission(ORG, OWNER)).status()).isEqualTo(OrganizationStatus.PENDING_REVIEW);
        assertThat(workflow.approve(new Approval(ORG, REVIEWER)).status()).isEqualTo(OrganizationStatus.ACTIVE);
        awaitClosed(workflow);
    }

    @Test
    @DisplayName("R5 · approving an organization that is not under review is refused, and the execution closes")
    void approvingADraftIsRefused() {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.DRAFT);

        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ORGANIZATION_STATE_INVALID.name()));
        awaitClosed(workflow);
        assertThat(state.steps).isEmpty();
    }

    @Test
    @DisplayName("an approval of an organization that does not exist is refused as unknown")
    void anUnknownOrganizationIsRefused() {
        state.exists = false;
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);

        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN.name()));
    }

    @Test
    @DisplayName("an execution started with no command closes when its window passes and nothing waits on a person")
    void anIdleStartCloses() {
        state.status = OrganizationStatus.DRAFT;
        OrganizerOnboardingWorkflow workflow = start();

        awaitClosed(workflow);

        assertThat(env.currentTimeMillis()).isGreaterThanOrEqualTo(OnboardingRules.FIRST_COMMAND_WINDOW.toMillis());
        assertThat(state.loads).isEqualTo(1);
    }

    @Test
    @DisplayName("PLT-015 R3 · a compensated approval followed by a completed one replays against the implementation")
    void theHistoryReplays() throws Exception {
        OrganizerOnboardingWorkflow workflow = open(OrganizationStatus.PENDING_REVIEW);
        state.failAt = OnboardingStep.GROUP_TREE;
        assertThatThrownBy(() -> workflow.approve(new Approval(ORG, REVIEWER)));
        state.failAt = null;
        workflow.approve(new Approval(ORG, REVIEWER));
        awaitClosed(workflow);

        assertReplays(WorkflowIds.organizerOnboarding(ORG), OrganizerOnboardingWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private static List<String> expectedCompensations(OnboardingStep failing) {
        if (failing == OnboardingStep.ACTIVATE) {
            return List.of();
        }
        int registered = Math.min(failing.marker(), COMPENSATIONS_IN_ORDER.size());
        return COMPENSATIONS_IN_ORDER.subList(COMPENSATIONS_IN_ORDER.size() - registered, COMPENSATIONS_IN_ORDER.size());
    }

    private OrganizerOnboardingWorkflow start() {
        OrganizerOnboardingWorkflow starter = client.newWorkflowStub(OrganizerOnboardingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.organizerOnboarding(ORG))
                        .setTaskQueue(TaskQueues.ONBOARDING)
                        .build());
        WorkflowClient.start(starter::run, new Start(ORG));
        return client.newWorkflowStub(OrganizerOnboardingWorkflow.class, WorkflowIds.organizerOnboarding(ORG));
    }

    private OrganizerOnboardingWorkflow open(OrganizationStatus status) {
        state.status = status;
        return start();
    }

    private static void awaitClosed(OrganizerOnboardingWorkflow workflow) {
        WorkflowStub.fromTyped(workflow).getResult(Void.class);
    }

    private long acceptedUpdates() {
        WorkflowExecution execution = WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.organizerOnboarding(ORG)).build();
        return env.getWorkflowExecutionHistory(execution).getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_ACCEPTED)
                .count();
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    /** What MongoDB and Keycloak hold for one organization and its owner. */
    static final class State {
        volatile boolean exists = true;
        volatile OrganizationStatus status;
        volatile int step;
        volatile String failure;
        volatile OnboardingStep failAt;
        volatile boolean membershipCreated;
        volatile Boolean ownerWasOrganizer;
        volatile boolean ownerOrganizer;
        volatile boolean realmRole;
        volatile boolean groupTree;
        volatile boolean inOwners;
        volatile int envelopes;
        volatile int grantAttempts;
        volatile int loads;
        final List<String> steps = new CopyOnWriteArrayList<>();
        final List<String> compensations = new CopyOnWriteArrayList<>();

        View view() {
            return exists ? new View(ORG, status, OWNER, step, failure) : new View(ORG, null, null, 0, null);
        }

        void failIf(OnboardingStep candidate) {
            if (failAt == candidate) {
                throw new RuntimeException("forced failure at " + candidate);
            }
        }

        void reached(OnboardingStep reached, String name) {
            step = Math.max(step, reached.marker());
            if (!steps.contains(name)) {
                steps.add(name);
            }
        }
    }

    static final class FakeRecords implements OnboardingActivities {
        private final State state;

        FakeRecords(State state) {
            this.state = state;
        }

        @Override
        public synchronized View load(String organizationId) {
            state.loads++;
            return state.view();
        }

        @Override
        public synchronized View submit(Submission submission) {
            state.status = OrganizationStatus.PENDING_REVIEW;
            return state.view();
        }

        @Override
        public synchronized View reject(Decision decision) {
            state.status = OrganizationStatus.REJECTED;
            return state.view();
        }

        @Override
        public synchronized View requestChanges(Decision decision) {
            state.status = OrganizationStatus.CHANGES_REQUESTED;
            return state.view();
        }

        @Override
        public synchronized View activate(Approval approval) {
            state.failIf(OnboardingStep.ACTIVATE);
            state.status = OrganizationStatus.ACTIVE;
            state.reached(OnboardingStep.ACTIVATE, "activate");
            return state.view();
        }

        @Override
        public synchronized void revertToPendingReview(String organizationId, String reason) {
            state.compensations.add("revertToPendingReview");
            if (state.status == OrganizationStatus.ACTIVE && state.step > 0 && state.step < 6) {
                state.status = OrganizationStatus.PENDING_REVIEW;
                state.step = 0;
                state.failure = reason;
                state.ownerWasOrganizer = null;
                state.steps.clear();
            }
        }

        @Override
        public synchronized void ensureOwnerMembership(String organizationId) {
            state.failIf(OnboardingStep.OWNER_MEMBERSHIP);
            state.membershipCreated = true;
            state.reached(OnboardingStep.OWNER_MEMBERSHIP, "ensureOwnerMembership");
        }

        @Override
        public synchronized void removeOwnerMembership(String organizationId) {
            state.compensations.add("removeOwnerMembership");
            state.membershipCreated = false;
        }

        @Override
        public synchronized void promoteOwner(String organizationId) {
            state.failIf(OnboardingStep.USER_TYPE);
            if (state.ownerWasOrganizer == null) {
                state.ownerWasOrganizer = state.ownerOrganizer;
            }
            state.ownerOrganizer = true;
            state.reached(OnboardingStep.USER_TYPE, "promoteOwner");
        }

        @Override
        public synchronized void restoreOwnerUserType(String organizationId) {
            state.compensations.add("restoreOwnerUserType");
            if (Boolean.FALSE.equals(state.ownerWasOrganizer)) {
                state.ownerOrganizer = false;
            }
        }

        @Override
        public synchronized View markApproved(String organizationId, String keycloakGroupId) {
            state.failIf(OnboardingStep.APPROVED);
            if (state.step < 6) {
                state.envelopes++;
                state.failure = null;
            }
            state.reached(OnboardingStep.APPROVED, "markApproved");
            return state.view();
        }
    }

    static final class FakeKeycloak implements OnboardingKeycloakActivities {
        private final State state;

        FakeKeycloak(State state) {
            this.state = state;
        }

        @Override
        public synchronized void grantOrganizerRole(String organizationId) {
            state.grantAttempts++;
            state.failIf(OnboardingStep.REALM_ROLE);
            state.realmRole = true;
            state.reached(OnboardingStep.REALM_ROLE, "grantOrganizerRole");
        }

        @Override
        public synchronized void revokeOrganizerRole(String organizationId) {
            state.compensations.add("revokeOrganizerRole");
            if (Boolean.FALSE.equals(state.ownerWasOrganizer)) {
                state.realmRole = false;
            }
        }

        @Override
        public synchronized String ensureGroupTree(String organizationId) {
            state.groupTree = true;
            state.failIf(OnboardingStep.GROUP_TREE);
            state.inOwners = true;
            state.reached(OnboardingStep.GROUP_TREE, "ensureGroupTree");
            return "group-kabwe";
        }

        @Override
        public synchronized void removeOwnerFromGroups(String organizationId) {
            state.compensations.add("removeOwnerFromGroups");
            state.inOwners = false;
        }
    }
}
