package com.pml.booking.workflow.latepayment;

import com.pml.booking.exception.ProviderUnavailableException;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Answer;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Outcome;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Result;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Stage;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Start;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.State;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.workflow.Refusals;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The automatic refund of late money (ROADMAP D-22), end to end with time skipped.
 *
 * <p>The activities are an in-memory stand-in with the real ones' contract: each transition is a
 * compare-and-set, so a second execution changes nothing; the provider's answers are scripted.
 */
@Tag("L3")
@Tag("ET-TKT-001")
@Tag("ET-PAY-001")
@Tag("ET-PLT-015")
@DisplayName("ET-TKT-001 · D-22 · late money is refunded in full automatically, and escalated only if that fails")
class LatePaymentRefundWorkflowTest {

    private static final String RESERVATION = "3a6f4c1e-5b7d-4e8f-9a0b-1c2d3e4f5a6b";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeLateRefund refunds;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        refunds = new FakeLateRefund();
        env.newWorker(TaskQueues.CHECKOUT).registerWorkflowImplementationTypes(LatePaymentRefundWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.CHECKOUT).registerActivitiesImplementations(refunds);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(refunds);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("the provider accepts, answers COMPLETED, and the money is returned with nothing escalated")
    void aRefundCompletes() {
        refunds.answers.add(new Answer(Answer.Outcome.PENDING, null, null));
        refunds.answers.add(new Answer(Answer.Outcome.COMPLETED, "REF-1", null));

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.REFUNDED);
        assertThat(refunds.state).isEqualTo(State.COMPLETED);
        assertThat(refunds.submissions).isEqualTo(1);
        assertThat(refunds.statusChecks).isEqualTo(2);
        assertThat(refunds.escalations).isEmpty();
    }

    @Test
    @DisplayName("a payment that is not owed back is refused and goes to the operator")
    void aRefusedOpenIsEscalated() {
        refunds.refuseOpen = true;

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(refunds.submissions).isZero();
        assertThat(refunds.escalations).hasSize(1);
    }

    @Test
    @DisplayName("a refusal by the provider is escalated, and nothing is polled")
    void aRefusedSubmissionIsEscalated() {
        refunds.refuseSubmission = true;

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(refunds.state).isEqualTo(State.FAILED);
        assertThat(refunds.statusChecks).isZero();
        assertThat(refunds.escalations).hasSize(1);
    }

    @Test
    @DisplayName("a provider that never answers is asked again under the same id, then escalated")
    void aSilentProviderIsEscalated() {
        refunds.unreachable = true;

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(refunds.submitCalls).isEqualTo(6);
        assertThat(refunds.submissions).isZero();
        assertThat(refunds.escalations).hasSize(1);
    }

    @Test
    @DisplayName("a refund the provider later fails is recorded failed once and escalated")
    void aFailedRefundIsEscalated() {
        refunds.answers.add(new Answer(Answer.Outcome.FAILED, null, "RECIPIENT_BLOCKED"));

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(refunds.state).isEqualTo(State.FAILED);
        assertThat(refunds.escalations).hasSize(1);
    }

    @Test
    @DisplayName("a provider that stays pending for 72 hours is escalated, not assumed refunded")
    void anUnansweredRefundIsEscalated() {
        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(refunds.state).isEqualTo(State.PROCESSING);
        assertThat(refunds.escalations).hasSize(1);
    }

    @Test
    @DisplayName("ET-PLT-015 R4 · a worker lost after the provider took the refund submits once and still completes")
    void aLostWorkerAfterSubmission() {
        refunds.loseWorkerAfterSubmission = true;
        refunds.answers.add(new Answer(Answer.Outcome.COMPLETED, "REF-1", null));

        Result result = run();

        assertThat(result.outcome()).isEqualTo(Outcome.REFUNDED);
        assertThat(refunds.submitCalls).isEqualTo(2);
        assertThat(refunds.submissions).isEqualTo(1);
    }

    @Test
    @DisplayName("ET-PLT-015 R2 · a second start reaches the one refund, and a finished refund refuses a rerun")
    void aRepeatedStartRefundsOnce() {
        refunds.answers.add(new Answer(Answer.Outcome.COMPLETED, "REF-1", null));
        WorkflowClient.start(stub()::run, new Start(RESERVATION));
        WorkflowClient.start(stub()::run, new Start(RESERVATION));
        WorkflowStub.fromTyped(existing()).getResult(Result.class);

        assertThatThrownBy(() -> WorkflowClient.start(stub()::run, new Start(RESERVATION)))
                .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
        assertThat(refunds.submissions).isEqualTo(1);
        assertThat(refunds.state).isEqualTo(State.COMPLETED);
    }

    @Test
    @DisplayName("ET-PLT-015 R3 · a completed refund's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        refunds.answers.add(new Answer(Answer.Outcome.PENDING, null, null));
        refunds.answers.add(new Answer(Answer.Outcome.COMPLETED, "REF-1", null));
        run();

        replays();
    }

    @Test
    @DisplayName("ET-PLT-015 R3 · an escalated refund's recorded history replays against the current implementation")
    void anEscalatedHistoryReplays() throws Exception {
        refunds.refuseSubmission = true;
        run();

        replays();
    }

    // ---- harness -------------------------------------------------------------------------------

    private void replays() throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.lateRefund(RESERVATION)).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, LatePaymentRefundWorkflowImpl.class);
    }

    private Result run() {
        return stub().run(new Start(RESERVATION));
    }

    private LatePaymentRefundWorkflow stub() {
        return client.newWorkflowStub(LatePaymentRefundWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.lateRefund(RESERVATION))
                .setTaskQueue(TaskQueues.CHECKOUT)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build());
    }

    private LatePaymentRefundWorkflow existing() {
        return client.newWorkflowStub(LatePaymentRefundWorkflow.class, WorkflowIds.lateRefund(RESERVATION));
    }

    /** The intent's late-refund state and the provider, reduced to what the workflow can observe. */
    static final class FakeLateRefund implements LatePaymentRefundActivities {
        State state;
        int submitCalls;
        int submissions;
        int statusChecks;
        final List<String> escalations = new ArrayList<>();
        final Deque<Answer> answers = new ArrayDeque<>();
        volatile boolean refuseOpen;
        volatile boolean refuseSubmission;
        volatile boolean unreachable;
        volatile boolean loseWorkerAfterSubmission;

        private View view() {
            return new View(RESERVATION, "REFUND-ID", state);
        }

        @Override
        public synchronized View open(String reservationId) {
            if (refuseOpen) {
                throw Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID, "the payment is not late");
            }
            if (state == null) {
                state = State.REQUESTED;
            }
            return view();
        }

        @Override
        public synchronized View submit(String reservationId) {
            submitCalls++;
            if (unreachable) {
                throw new ProviderUnavailableException("no answer");
            }
            if (state == State.REQUESTED) {
                state = refuseSubmission ? State.FAILED : State.PROCESSING;
                submissions++;
            }
            if (loseWorkerAfterSubmission) {
                loseWorkerAfterSubmission = false;
                throw new RuntimeException("worker lost after the provider took the refund");
            }
            return view();
        }

        @Override
        public synchronized Answer providerStatus(String reservationId) {
            statusChecks++;
            Answer next = answers.poll();
            return next != null ? next : new Answer(Answer.Outcome.PENDING, null, null);
        }

        @Override
        public synchronized View complete(String reservationId, String reference) {
            if (state == State.PROCESSING || state == State.REQUESTED) {
                state = State.COMPLETED;
            }
            return view();
        }

        @Override
        public synchronized View fail(String reservationId, String failureCode) {
            if (state == State.PROCESSING || state == State.REQUESTED) {
                state = State.FAILED;
            }
            return view();
        }

        @Override
        public synchronized void escalate(String reservationId, String reason) {
            if (escalations.isEmpty()) {
                escalations.add(reason);
            }
        }
    }
}
