package com.pml.booking.workflow.refund;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.refund.RefundWorkflow.Answer;
import com.pml.booking.workflow.refund.RefundWorkflow.Decision;
import com.pml.booking.workflow.refund.RefundWorkflow.Start;
import com.pml.booking.workflow.refund.RefundWorkflow.Submit;
import com.pml.booking.workflow.refund.RefundWorkflow.View;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * A ticket's refund end to end, with time skipped.
 */
@Tag("L3")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004-R5 · only a cancellation's refund approves itself; every other waits for a person, is escalated at two and five days, and completes only on a verified answer")
public class RefundWorkflowTest {

    private static final String TICKET = "ticket-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeRefunds refunds;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        refunds = new FakeRefunds();
        env.newWorker(TaskQueues.FINANCE).registerWorkflowImplementationTypes(RefundWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.FINANCE).registerActivitiesImplementations(refunds);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(refunds);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("D-26 · a small refund still waits for a person, then completes when the provider confirms it")
    void aSmallRefundWaitsForAPerson() {
        refunds.answers.add(Answer.pending());
        refunds.answers.add(Answer.completed("prov-refund-1"));
        RefundWorkflow refund = submitted(new BigDecimal("50.00"));
        env.sleep(Duration.ofHours(1));
        assertThat(refund.current().status()).isEqualTo(RefundRequestStatus.PENDING);

        approve(refund);
        awaitClosed(refund);

        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.COMPLETED);
        assertThat(refunds.approvedBy).containsExactly("finance-1");
        assertThat(refunds.restores).isZero();
        assertThat(refunds.escalations()).isEmpty();
    }

    @Test
    @DisplayName("D-26 · a refund nobody decides is escalated after two days and again after five, and still waits for a person")
    void anUndecidedRefundIsEscalated() {
        refunds.answers.add(Answer.completed("prov-refund-1"));
        RefundWorkflow refund = submitted(new BigDecimal("1500.00"));

        env.sleep(Duration.ofDays(2).plusMinutes(1));
        await().atMost(Duration.ofSeconds(10)).until(() -> refunds.escalations().equals(List.of(1)));
        env.sleep(Duration.ofDays(3));
        await().atMost(Duration.ofSeconds(10)).until(() -> refunds.escalations().equals(List.of(1, 2)));
        env.sleep(Duration.ofDays(10));

        assertThat(refund.current().status()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(refunds.escalations()).containsExactly(1, 2);
        assertThat(refunds.approvedBy).isEmpty();

        approve(refund);
        awaitClosed(refund);

        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("R5 · a large refund waits for a person, then completes")
    void aLargeRefundWaitsForApproval() {
        refunds.answers.add(Answer.completed("prov-refund-1"));
        RefundWorkflow refund = submitted(new BigDecimal("1500.00"));
        assertThat(refund.current().status()).isEqualTo(RefundRequestStatus.PENDING);

        refund.approve(new Decision(refunds.idFor(TICKET), "finance-1", "within policy"));
        awaitClosed(refund);

        assertThat(refunds.approvedBy).containsExactly("finance-1");
        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("a rejection needs a reason, and a rejected refund sends nothing")
    void aRejectionSendsNothing() {
        RefundWorkflow refund = submitted(new BigDecimal("1500.00"));
        String id = refunds.idFor(TICKET);

        assertThatThrownBy(() -> refund.reject(new Decision(id, "finance-1", " ")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
        refund.reject(new Decision(id, "finance-1", "outside the refund window"));
        awaitClosed(refund);

        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.REJECTED);
        assertThat(refunds.processed).isZero();
    }

    @Test
    @DisplayName("R3 · a verified provider failure marks the refund FAILED, restores the escrow and reinstates the commission once")
    void aVerifiedFailureRestoresEscrow() {
        refunds.answers.add(Answer.failed("INSUFFICIENT_BALANCE", "provider float empty"));
        RefundWorkflow refund = submitted(new BigDecimal("200.00"));
        approve(refund);

        awaitClosed(refund);

        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.FAILED);
        assertThat(refunds.restores).isEqualTo(1);
        assertThat(refunds.reinstatements).isEqualTo(1);
    }

    @Test
    @DisplayName("a refund the provider does not accept is failed, its escrow restored and its commission reinstated")
    void anUnacceptedRefundRestoresEscrow() {
        refunds.rejectAtProvider = true;
        RefundWorkflow refund = submitted(new BigDecimal("200.00"));
        approve(refund);

        awaitClosed(refund);

        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.FAILED);
        assertThat(refunds.restores).isEqualTo(1);
        assertThat(refunds.reinstatements).isEqualTo(1);
    }

    @Test
    @DisplayName("R6 · an event cancellation's refund is created and approved without a person")
    void anAutomaticRefundNeedsNoPerson() {
        refunds.answers.add(Answer.completed("prov-refund-1"));
        RefundWorkflow starter = client.newWorkflowStub(RefundWorkflow.class, options());
        WorkflowClient.start(starter::run, new Start(TICKET, "Venue flooded"));

        awaitClosed(client.newWorkflowStub(RefundWorkflow.class, WorkflowIds.refund(TICKET)));

        assertThat(refunds.automatic).containsExactly(TICKET);
        assertThat(refunds.approvedBy).containsExactly(RefundRules.SYSTEM_ACTOR);
        assertThat(refunds.status(TICKET)).isEqualTo(RefundRequestStatus.COMPLETED);
    }

    @Test
    @DisplayName("a refund already sent to the provider cannot be cancelled")
    void noCancelAfterSending() {
        RefundWorkflow refund = submitted(new BigDecimal("200.00"));
        approve(refund);
        env.sleep(Duration.ofSeconds(10));

        assertThatThrownBy(() -> refund.cancel(new Decision(refunds.idFor(TICKET), "admin-1", "duplicate")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED.name()));
    }

    @Test
    @DisplayName("R4 · a second request for the ticket answers the open one")
    void aSecondRequestAnswersTheFirst() {
        RefundWorkflow refund = submitted(new BigDecimal("1500.00"));

        View again = refund.submit(new Submit(Submit.Kind.BUYER, TICKET, "again", "buyer-1", null, false));

        assertThat(again.refundRequestId()).isEqualTo(refunds.idFor(TICKET));
        assertThat(refunds.submits).isEqualTo(1);
    }

    private WorkflowOptions options() {
        return WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.refund(TICKET))
                .setTaskQueue(TaskQueues.FINANCE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build();
    }

    private RefundWorkflow submitted(BigDecimal amount) {
        refunds.amount = amount;
        RefundWorkflow starter = client.newWorkflowStub(RefundWorkflow.class, options());
        WorkflowClient.start(starter::run, new Start(TICKET, null));
        RefundWorkflow refund = client.newWorkflowStub(RefundWorkflow.class, WorkflowIds.refund(TICKET));
        refund.submit(new Submit(Submit.Kind.BUYER, TICKET, "cannot attend", "buyer-1", null, false));
        return refund;
    }

    private void approve(RefundWorkflow refund) {
        refund.approve(new Decision(refunds.idFor(TICKET), "finance-1", "within policy"));
    }

    private static void awaitClosed(RefundWorkflow refund) {
        WorkflowStub.fromTyped(refund).getResult(Void.class);
    }

    /** The refund service, per ticket; shared with the cancellation and event-finance tests. */
    public static final class FakeRefunds implements RefundActivities {
        private final Map<String, View> views = new HashMap<>();
        final Deque<Answer> answers = new ConcurrentLinkedDeque<>();
        final List<String> approvedBy = new ArrayList<>();
        final List<String> automatic = new ArrayList<>();
        private final List<Integer> escalationLevels = new ArrayList<>();
        volatile BigDecimal amount = new BigDecimal("200.00");
        volatile boolean rejectAtProvider;
        int submits;
        int processed;
        int restores;
        int reinstatements;

        public synchronized List<String> completedTickets() {
            return views.values().stream().filter(v -> v.status() == RefundRequestStatus.COMPLETED).map(View::ticketId).toList();
        }

        String idFor(String ticketId) {
            return "refund-" + ticketId;
        }

        synchronized RefundRequestStatus status(String ticketId) {
            return views.get(ticketId).status();
        }

        private View move(String refundRequestId, RefundRequestStatus status) {
            String ticketId = refundRequestId.substring("refund-".length());
            View moved = new View(refundRequestId, ticketId, status, views.get(ticketId).amount());
            views.put(ticketId, moved);
            return moved;
        }

        @Override
        public synchronized View submit(Submit command) {
            return views.computeIfAbsent(command.ticketId(), ticketId -> {
                submits++;
                return new View(idFor(ticketId), ticketId, RefundRequestStatus.PENDING, amount);
            });
        }

        @Override
        public synchronized View createAutomatic(String ticketId, String reason) {
            return views.computeIfAbsent(ticketId, id -> {
                automatic.add(id);
                return new View(idFor(id), id, RefundRequestStatus.PENDING, amount);
            });
        }

        @Override
        public synchronized View approve(Decision decision) {
            approvedBy.add(decision.actorId());
            return move(decision.refundRequestId(), RefundRequestStatus.APPROVED);
        }

        @Override
        public synchronized View reject(Decision decision) {
            return move(decision.refundRequestId(), RefundRequestStatus.REJECTED);
        }

        @Override
        public synchronized View cancel(Decision decision) {
            return move(decision.refundRequestId(), RefundRequestStatus.CANCELLED);
        }

        @Override
        public synchronized View process(String refundRequestId) {
            processed++;
            return move(refundRequestId, rejectAtProvider ? RefundRequestStatus.FAILED : RefundRequestStatus.PROCESSING);
        }

        @Override
        public Answer providerStatus(String refundRequestId) {
            if (answers.isEmpty()) {
                return Answer.completed("prov-" + refundRequestId);
            }
            return answers.poll();
        }

        @Override
        public synchronized View complete(String refundRequestId, String reference) {
            return move(refundRequestId, RefundRequestStatus.COMPLETED);
        }

        @Override
        public synchronized View fail(String refundRequestId, String failureCode, String reason) {
            return move(refundRequestId, RefundRequestStatus.FAILED);
        }

        @Override
        public synchronized void restoreEscrow(String refundRequestId) {
            restores++;
        }

        @Override
        public synchronized void reinstateCommission(String refundRequestId) {
            reinstatements++;
        }

        @Override
        public synchronized void escalateReview(String refundRequestId, int level) {
            escalationLevels.add(level);
        }

        synchronized List<Integer> escalations() {
            return List.copyOf(escalationLevels);
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a completed refund's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        refunds.answers.add(Answer.completed("prov-refund-1"));
        RefundWorkflow refund = submitted(new BigDecimal("200.00"));
        approve(refund);
        awaitClosed(refund);

        assertReplays(WorkflowIds.refund(TICKET), RefundWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
