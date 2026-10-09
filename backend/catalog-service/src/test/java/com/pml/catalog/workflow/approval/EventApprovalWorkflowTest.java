package com.pml.catalog.workflow.approval;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Claim;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Snapshot;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Start;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Submission;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.View;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The review workflow end to end, with time skipped.
 *
 * <p>The review store is an in-memory stand-in recording each escalation level, claim and release,
 * so each timer is asserted by what it wrote and when. The MongoDB side of the same steps is proven
 * against a replica set in {@code EventReviewServiceTest}.
 */
@Tag("L3")
@Tag("ET-ADM-001")
@DisplayName("ET-ADM-001-R2/R3/R5 · a review escalates once per level, pauses for the applicant, and is decided by its holder")
class EventApprovalWorkflowTest {

    private static final String EVENT = "event-1";
    private static final String ORGANIZER = "organizer-1";
    private static final String ALICE = "reviewer-alice";
    private static final String BOB = "reviewer-bob";
    private static final String REASON = "The venue capacity does not match the tiers";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeReviews reviews;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        reviews = new FakeReviews();
        Worker worker = env.newWorker(TaskQueues.LIFECYCLE);
        worker.registerWorkflowImplementationTypes(EventApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(reviews);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R3 · levels 1, 2 and 3 fire at 24 h, 48 h and 96 h, each exactly once")
    void escalationsFireOncePerLevel() {
        EventApprovalWorkflow review = open();

        env.sleep(Duration.ofHours(24).minusMinutes(1));
        assertThat(reviews.levels).isEmpty();

        env.sleep(Duration.ofMinutes(2));
        eventually("level 1 fires at the SLA", () -> reviews.levels.equals(List.of(1)));
        assertThat(reviews.holders).containsExactly("none");

        env.sleep(Duration.ofHours(24).minusMinutes(5));
        assertThat(reviews.levels).containsExactly(1);

        env.sleep(Duration.ofMinutes(10));
        eventually("level 2 fires at twice the SLA", () -> reviews.levels.equals(List.of(1, 2)));

        env.sleep(Duration.ofHours(48));
        eventually("level 3 fires at four times the SLA", () -> reviews.levels.equals(List.of(1, 2, 3)));

        env.sleep(Duration.ofDays(10));
        assertThat(reviews.escalateCalls.get()).isEqualTo(3);
        assertThat(review.current().escalationLevel()).isEqualTo(3);

        review.approve(new Decision(EVENT, ALICE, null));
        awaitClosed(review);
    }

    @Test
    @DisplayName("R3 · the clock stops while changes are requested and resumes with only the unspent budget")
    void theClockPausesForTheApplicant() {
        EventApprovalWorkflow review = open();
        env.sleep(Duration.ofHours(20));
        review.requestChanges(new Decision(EVENT, ALICE, REASON));

        env.sleep(Duration.ofHours(100));
        assertThat(reviews.levels).as("100 hours awaiting the applicant is not the platform's delay").isEmpty();

        long resubmittedAt = env.currentTimeMillis();
        View resubmitted = review.resubmit(new Submission(EVENT, ORGANIZER));
        assertThat(resubmitted.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(reviews.lastDeadline - resubmittedAt)
                .as("the deadline carries the four hours left of the SLA")
                .isBetween(Duration.ofHours(4).minusMinutes(1).toMillis(), Duration.ofHours(4).plusMinutes(1).toMillis());

        env.sleep(Duration.ofHours(4).minusMinutes(5));
        assertThat(reviews.levels).isEmpty();

        env.sleep(Duration.ofMinutes(10));
        eventually("level 1 fires once the resumed clock reaches the SLA", () -> reviews.levels.equals(List.of(1)));
    }

    @Test
    @DisplayName("R2 · a claim expires after thirty minutes and the item keeps its place")
    void aClaimExpires() {
        EventApprovalWorkflow review = open();
        long submittedAt = reviews.submittedAt;
        review.claim(new Claim(EVENT, ALICE, ALICE));

        env.sleep(Duration.ofMinutes(29));
        assertThat(reviews.releases).isEmpty();
        assertThat(review.current().claimHolderId()).isEqualTo(ALICE);

        env.sleep(Duration.ofMinutes(2));
        eventually("the lapsed claim is released", () -> reviews.releases.equals(List.of(ALICE + ":expired")));
        eventually("nobody holds the item", () -> review.current().claimHolderId() == null);
        assertThat(reviews.submittedAt).as("expiry does not send the applicant to the back of the queue").isEqualTo(submittedAt);
    }

    @Test
    @DisplayName("R2 · a second reviewer is refused while the claim is live, and may claim once it lapses")
    void aHeldClaimRefusesAnotherReviewer() {
        EventApprovalWorkflow review = open();
        review.claim(new Claim(EVENT, ALICE, ALICE));

        assertThatThrownBy(() -> review.claim(new Claim(EVENT, BOB, BOB)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.RESOURCE_CONFLICT.name()));
        assertThat(reviews.claims).containsExactly(ALICE);

        env.sleep(Duration.ofMinutes(31));
        eventually("the lapsed claim is released", () -> reviews.releases.size() == 1);
        review.claim(new Claim(EVENT, BOB, BOB));
        assertThat(reviews.claims).containsExactly(ALICE, BOB);
    }

    @Test
    @DisplayName("R2 · two claims racing for one item yield exactly one holder")
    void racingClaimsYieldOneHolder() {
        EventApprovalWorkflow review = open();
        reviews.claimDelayMillis = 500;
        WorkflowStub stub = WorkflowStub.fromTyped(review);

        // Both are sent before the first claim's write completes; a refused update comes back as a
        // completed handle whose result is the refusal.
        List<WorkflowUpdateHandle<View>> sent = new ArrayList<>();
        for (String reviewer : List.of(ALICE, BOB)) {
            sent.add(stub.startUpdate("claim", WorkflowUpdateStage.ACCEPTED, View.class,
                    new Claim(EVENT, reviewer, reviewer)));
        }

        List<String> holders = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        for (WorkflowUpdateHandle<View> handle : sent) {
            try {
                holders.add(handle.getResult().claimHolderId());
            } catch (RuntimeException refused) {
                refusals.add(Refusals.typeOf(refused, null));
            }
        }

        assertThat(holders).containsExactly(ALICE);
        assertThat(refusals).containsExactly(ErrorCode.RESOURCE_CONFLICT.name());
        assertThat(reviews.claims).containsExactly(ALICE);
        assertThat(review.current().claimHolderId()).isEqualTo(ALICE);
    }

    @Test
    @DisplayName("R5 · a second decision while the first is still writing is refused, and the item is decided once")
    void oneDecisionAtATime() {
        EventApprovalWorkflow review = open();
        reviews.approveDelayMillis = 500;
        WorkflowUpdateHandle<View> approving = WorkflowStub.fromTyped(review)
                .startUpdate("approve", WorkflowUpdateStage.ACCEPTED, View.class, new Decision(EVENT, ALICE, null));

        assertThatThrownBy(() -> review.reject(new Decision(EVENT, BOB, REASON)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));

        assertThat(approving.getResult().status()).isEqualTo(EventStatus.APPROVED);
        awaitClosed(review);
        assertThat(reviews.approvals.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("R2 · the holder re-claiming extends the lease rather than failing")
    void reclaimingExtends() {
        EventApprovalWorkflow review = open();
        review.claim(new Claim(EVENT, ALICE, ALICE));
        env.sleep(Duration.ofMinutes(20));
        review.claim(new Claim(EVENT, ALICE, ALICE));

        env.sleep(Duration.ofMinutes(20));
        assertThat(reviews.releases).as("forty minutes after the first claim, twenty after the second").isEmpty();

        env.sleep(Duration.ofMinutes(11));
        eventually("the extended claim lapses", () -> reviews.releases.equals(List.of(ALICE + ":expired")));
    }

    @Test
    @DisplayName("R5 · deciding another reviewer's claim is refused; the holder's decision closes the review and releases the claim")
    void onlyTheHolderDecides() {
        EventApprovalWorkflow review = open();
        review.claim(new Claim(EVENT, ALICE, ALICE));

        assertThatThrownBy(() -> review.approve(new Decision(EVENT, BOB, null)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));
        assertThat(reviews.approvals.get()).isZero();
        assertThat(acceptedUpdates()).as("the submission and the claim").isEqualTo(2);

        View approved = review.approve(new Decision(EVENT, ALICE, null));
        awaitClosed(review);

        assertThat(approved.status()).isEqualTo(EventStatus.APPROVED);
        assertThat(approved.claimHolderId()).isNull();
        assertThat(reviews.approvals.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("R5 · a rejection or change request with a reason under twenty characters is refused")
    void aShortReasonIsRefused() {
        EventApprovalWorkflow review = open();

        assertThatThrownBy(() -> review.reject(new Decision(EVENT, ALICE, "No")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
        assertThatThrownBy(() -> review.requestChanges(new Decision(EVENT, ALICE, "Fix the tiers")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));

        env.sleep(Duration.ofHours(2));
        View rejected = review.reject(new Decision(EVENT, ALICE, REASON));
        awaitClosed(review);

        assertThat(rejected.status()).isEqualTo(EventStatus.REJECTED);
        assertThat(reviews.lastReviewMillis).as("time to decision is the SLA clock's reading")
                .isGreaterThanOrEqualTo(Duration.ofHours(2).toMillis());
    }

    @Test
    @DisplayName("a resubmission is refused unless changes were requested; a submission while they were is a resubmission")
    void resubmissionStates() {
        EventApprovalWorkflow review = open();

        assertThatThrownBy(() -> review.resubmit(new Submission(EVENT, ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));

        review.requestChanges(new Decision(EVENT, ALICE, REASON));
        View resubmitted = review.submit(new Submission(EVENT, ORGANIZER));

        assertThat(resubmitted.status()).isEqualTo(EventStatus.PENDING_APPROVAL);
        assertThat(reviews.resubmits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("R3 · an adopted review past two thresholds records levels 1 and 2 in order, then 3 on time")
    void anAdoptedReviewCatchesUp() {
        reviews.status = EventStatus.PENDING_APPROVAL;
        reviews.submittedAt = env.currentTimeMillis() - Duration.ofHours(50).toMillis();

        WorkflowClient.start(starter()::run, new Start(EVENT, true));
        eventually("the crossed levels fire in order", () -> reviews.levels.equals(List.of(1, 2)));

        env.sleep(Duration.ofHours(47));
        eventually("level 3 fires at 96 hours", () -> reviews.levels.equals(List.of(1, 2, 3)));
    }

    @Test
    @DisplayName("ET-PLT-015 R3 · a claimed, expired, paused, escalated and approved review replays against the current implementation")
    void aDecidedReviewReplays() throws Exception {
        EventApprovalWorkflow review = open();
        review.claim(new Claim(EVENT, ALICE, ALICE));
        env.sleep(Duration.ofMinutes(31));
        eventually("the lapsed claim is released", () -> reviews.releases.size() == 1);
        review.requestChanges(new Decision(EVENT, BOB, REASON));
        env.sleep(Duration.ofHours(10));
        review.resubmit(new Submission(EVENT, ORGANIZER));
        env.sleep(Duration.ofHours(25));
        eventually("level 1 fires on the resumed clock", () -> reviews.levels.equals(List.of(1)));
        review.approve(new Decision(EVENT, BOB, null));
        awaitClosed(review);

        assertReplays(WorkflowIds.eventApproval(EVENT), EventApprovalWorkflowImpl.class);
    }

    @Test
    @DisplayName("Submitting, requesting changes, resubmitting and approving each announce once, in order")
    void reviewStepsAreAnnounced() {
        EventApprovalWorkflow review = open();
        review.requestChanges(new Decision(EVENT, ALICE, REASON));
        review.resubmit(new Submission(EVENT, ORGANIZER));
        review.submit(new Submission(EVENT, ORGANIZER));
        review.approve(new Decision(EVENT, BOB, null));
        awaitClosed(review);

        assertThat(reviews.announced).containsExactly(EventStatus.PENDING_APPROVAL, EventStatus.CHANGES_REQUESTED,
                EventStatus.PENDING_APPROVAL, EventStatus.APPROVED);
    }

    @Test
    @DisplayName("A rejection is announced, and the execution closes only after the announcement is handed over")
    void rejectionIsAnnounced() {
        EventApprovalWorkflow review = open();
        review.reject(new Decision(EVENT, BOB, REASON));
        awaitClosed(review);

        assertThat(reviews.announced).containsExactly(EventStatus.PENDING_APPROVAL, EventStatus.REJECTED);
    }

    @Test
    @DisplayName("An announcement that keeps failing is retried a few times and dropped; the review is decided regardless")
    void failingAnnouncementDoesNotBlockTheReview() {
        reviews.announcementsFail = true;
        EventApprovalWorkflow review = open();
        review.approve(new Decision(EVENT, BOB, null));
        awaitClosed(review);

        assertThat(reviews.status).isEqualTo(EventStatus.APPROVED);
        assertThat(reviews.announced).isEmpty();
        assertThat(reviews.announceAttempts.get()).as("five attempts for each of the two announcements").isEqualTo(10);
    }

    @Test
    @DisplayName("Reviews recorded before announcements existed replay against the current implementation")
    void reviewsRecordedBeforeAnnouncementsReplay() throws Exception {
        for (String recorded : List.of("event-approval-approved.json", "event-approval-rejected.json")) {
            String history = new String(java.util.Objects.requireNonNull(getClass().getResourceAsStream(
                    "/workflow-histories/" + recorded)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            WorkflowReplayer.replayWorkflowExecution(history, EventApprovalWorkflowImpl.class);
        }
    }

    // ---- harness -------------------------------------------------------------------------------

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    private EventApprovalWorkflow starter() {
        return client.newWorkflowStub(EventApprovalWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.eventApproval(EVENT))
                .setTaskQueue(TaskQueues.LIFECYCLE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build());
    }

    private EventApprovalWorkflow open() {
        WorkflowClient.start(starter()::run, new Start(EVENT, false));
        EventApprovalWorkflow review = client.newWorkflowStub(EventApprovalWorkflow.class, WorkflowIds.eventApproval(EVENT));
        review.submit(new Submission(EVENT, ORGANIZER));
        return review;
    }

    private static void awaitClosed(EventApprovalWorkflow review) {
        WorkflowStub.fromTyped(review).getResult(Void.class);
    }

    /** Lets a timer's activity finish: advances time in small steps until the condition holds. */
    private void eventually(String what, BooleanSupplier condition) {
        for (int step = 0; step < 300 && !condition.getAsBoolean(); step++) {
            env.sleep(Duration.ofMillis(100));
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    private long acceptedUpdates() {
        WorkflowExecution execution = WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.eventApproval(EVENT)).build();
        return env.getWorkflowExecutionHistory(execution).getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_ACCEPTED)
                .count();
    }

    /** The review store, in memory: the event's status and a record of every escalation, claim and release. */
    static final class FakeReviews implements EventApprovalActivities {
        volatile EventStatus status = EventStatus.DRAFT;
        volatile long submittedAt;
        volatile long changesRequestedAt;
        volatile long lastDeadline;
        volatile long lastReviewMillis;
        volatile long claimDelayMillis;
        volatile long approveDelayMillis;
        final List<Integer> levels = new CopyOnWriteArrayList<>();
        final List<String> holders = new CopyOnWriteArrayList<>();
        final List<String> claims = new CopyOnWriteArrayList<>();
        final List<String> releases = new CopyOnWriteArrayList<>();
        final AtomicInteger escalateCalls = new AtomicInteger();
        final AtomicInteger approvals = new AtomicInteger();
        final AtomicInteger resubmits = new AtomicInteger();
        final List<EventStatus> announced = new CopyOnWriteArrayList<>();
        final AtomicInteger announceAttempts = new AtomicInteger();
        volatile boolean announcementsFail;

        private Snapshot snapshot() {
            return new Snapshot(EVENT, status, submittedAt, changesRequestedAt, levels.isEmpty() ? 0 : levels.get(levels.size() - 1));
        }

        @Override
        public synchronized Snapshot current(String eventId) {
            return snapshot();
        }

        @Override
        public synchronized Snapshot submit(String eventId, String actorId, long slaDeadlineMillis) {
            if (status == EventStatus.DRAFT || status == EventStatus.REJECTED) {
                status = EventStatus.PENDING_APPROVAL;
                submittedAt = slaDeadlineMillis - ApprovalRules.SLA.toMillis();
            }
            lastDeadline = slaDeadlineMillis;
            return snapshot();
        }

        @Override
        public synchronized Snapshot resubmit(String eventId, String actorId, long slaDeadlineMillis) {
            if (status == EventStatus.CHANGES_REQUESTED) {
                status = EventStatus.PENDING_APPROVAL;
                resubmits.incrementAndGet();
            }
            lastDeadline = slaDeadlineMillis;
            return snapshot();
        }

        @Override
        public void claim(String eventId, String reviewerId, String actorId, long expiresAtMillis) {
            slow(claimDelayMillis);
            if (claims.isEmpty() || !claims.get(claims.size() - 1).equals(reviewerId)) {
                claims.add(reviewerId);
            }
        }

        @Override
        public void releaseClaim(String eventId, String reviewerId, boolean expired) {
            releases.add(reviewerId + (expired ? ":expired" : ":released"));
        }

        @Override
        public synchronized void escalate(String eventId, int level, String holderId) {
            escalateCalls.incrementAndGet();
            if (!levels.contains(level)) {
                levels.add(level);
                holders.add(holderId != null ? holderId : "none");
            }
        }

        /** A write that takes real time, so a second update arrives while the first is still in flight. */
        private static void slow(long millis) {
            if (millis <= 0) {
                return;
            }
            try {
                Thread.sleep(millis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public Snapshot approve(Decision decision, long reviewMillis) {
            slow(approveDelayMillis);
            synchronized (this) {
                status = EventStatus.APPROVED;
                lastReviewMillis = reviewMillis;
                approvals.incrementAndGet();
                return snapshot();
            }
        }

        @Override
        public synchronized Snapshot reject(Decision decision, long reviewMillis) {
            status = EventStatus.REJECTED;
            lastReviewMillis = reviewMillis;
            return snapshot();
        }

        @Override
        public synchronized Snapshot requestChanges(Decision decision, long reviewMillis) {
            status = EventStatus.CHANGES_REQUESTED;
            lastReviewMillis = reviewMillis;
            return snapshot();
        }

        @Override
        public void announce(String eventId, EventStatus reached) {
            announceAttempts.incrementAndGet();
            if (announcementsFail) {
                throw new IllegalStateException("identity is unreachable");
            }
            announced.add(reached);
        }
    }
}
