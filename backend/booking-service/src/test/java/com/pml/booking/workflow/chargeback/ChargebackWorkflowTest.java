package com.pml.booking.workflow.chargeback;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Decision;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Dispute;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Outcome;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Receive;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Start;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.View;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.error.ErrorCode;
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
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * A chargeback from receipt to a closed dispute, with time skipped.
 */
@Tag("L3")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004-R8 · a chargeback opens a dispute once, is decided by its deadline, and closes the dispute once")
class ChargebackWorkflowTest {

    private static final String CHARGEBACK = "cb-provider-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeChargebacks chargebacks;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        chargebacks = new FakeChargebacks(() -> env.currentTimeMillis());
        env.newWorker(TaskQueues.FINANCE).registerWorkflowImplementationTypes(ChargebackWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.FINANCE).registerActivitiesImplementations(chargebacks);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("an accepted chargeback runs the recovery waterfall and closes its dispute")
    void anAcceptedChargebackRecovers() {
        ChargebackWorkflow chargeback = received();

        chargeback.accept(new Decision("admin-1", "customer did not attend"));
        awaitClosed(chargeback);

        assertThat(chargebacks.disputesOpened).isEqualTo(1);
        assertThat(chargebacks.recoveries).isEqualTo(1);
        assertThat(chargebacks.disputesClosed).isEqualTo(1);
        assertThat(chargebacks.escalations).isZero();
    }

    @Test
    @DisplayName("no decision by the response deadline accepts it automatically")
    void theDeadlineAccepts() {
        ChargebackWorkflow chargeback = received();

        env.sleep(Duration.ofDays(4));
        awaitClosed(chargeback);

        assertThat(chargebacks.acceptedBy).containsExactly(ChargebackRules.SYSTEM_ACTOR);
        assertThat(chargebacks.escalations).isEqualTo(1);
        assertThat(chargebacks.recoveries).isEqualTo(1);
        assertThat(chargebacks.disputesClosed).isEqualTo(1);
    }

    @Test
    @DisplayName("D-25 · an undecided chargeback is escalated to finance 24 hours before its deadline, once, and still waits for a decision")
    void anUndecidedChargebackIsEscalated() {
        ChargebackWorkflow chargeback = received();

        env.sleep(Duration.ofHours(47));
        assertThat(chargebacks.escalations).isZero();

        env.sleep(Duration.ofHours(2));
        await().atMost(Duration.ofSeconds(10)).until(() -> chargebacks.escalations == 1);
        assertThat(chargebacks.accepted()).isEmpty();

        chargeback.accept(new Decision("finance-lead", "accepted after the escalation"));
        awaitClosed(chargeback);

        assertThat(chargebacks.escalations).isEqualTo(1);
        assertThat(chargebacks.accepted()).containsExactly("finance-lead");
    }

    @Test
    @DisplayName("a won dispute closes without recovering anything")
    void aWonDisputeRecoversNothing() {
        ChargebackWorkflow chargeback = received();

        chargeback.dispute(new Dispute("admin-1", "ticket was scanned at the gate", "scan-log", null, null, null, null));
        chargeback.recordOutcome(new Outcome("admin-1", true, "provider reversed it"));
        awaitClosed(chargeback);

        assertThat(chargebacks.wins).isEqualTo(1);
        assertThat(chargebacks.recoveries).isZero();
        assertThat(chargebacks.disputesClosed).isEqualTo(1);
    }

    @Test
    @DisplayName("a lost dispute records the loss, whose recovery the service starts, and closes the dispute")
    void aLostDisputeCloses() {
        ChargebackWorkflow chargeback = received();

        chargeback.dispute(new Dispute("admin-1", "ticket was scanned at the gate", "scan-log", null, null, null, null));
        chargeback.recordOutcome(new Outcome("admin-1", false, "provider upheld it"));
        awaitClosed(chargeback);

        assertThat(chargebacks.losses).isEqualTo(1);
        assertThat(chargebacks.recoveries).isZero();
        assertThat(chargebacks.disputesClosed).isEqualTo(1);
    }

    @Test
    @DisplayName("an outcome cannot be recorded for a chargeback that was never disputed")
    void noOutcomeWithoutDispute() {
        ChargebackWorkflow chargeback = received();

        assertThatThrownBy(() -> chargeback.recordOutcome(new Outcome("admin-1", true, "premature")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.RESOURCE_CONFLICT.name()));
        assertThat(chargebacks.wins).isZero();
    }

    @Test
    @DisplayName("a provider notifying twice reaches the same record")
    void aRepeatedNotificationReachesOneRecord() {
        ChargebackWorkflow chargeback = received();

        View again = chargeback.receive(receive());

        assertThat(again.recordId()).isEqualTo("record-1");
        assertThat(chargebacks.receives).isEqualTo(1);
    }

    private ChargebackWorkflow received() {
        ChargebackWorkflow starter = client.newWorkflowStub(ChargebackWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.chargeback(CHARGEBACK))
                .setTaskQueue(TaskQueues.FINANCE)
                .build());
        WorkflowClient.start(starter::run, new Start(CHARGEBACK));
        ChargebackWorkflow chargeback = client.newWorkflowStub(ChargebackWorkflow.class, WorkflowIds.chargeback(CHARGEBACK));
        chargeback.receive(receive());
        return chargeback;
    }

    private Receive receive() {
        return new Receive(CHARGEBACK, "txn-1", "ticket-1", "event-1", "organizer-1", "org-1", "customer-1",
                new BigDecimal("450.00"), new BigDecimal("450.00"), new BigDecimal("15.00"), "ZMW", "FRAUD",
                env.currentTimeMillis() + Duration.ofDays(3).toMillis());
    }

    private static void awaitClosed(ChargebackWorkflow chargeback) {
        WorkflowStub.fromTyped(chargeback).getResult(Void.class);
    }

    static final class FakeChargebacks implements ChargebackActivities {
        private final LongSupplier now;
        private ChargebackStatus status;
        private long deadline;
        int receives;
        int disputesOpened;
        int disputesClosed;
        int recoveries;
        int wins;
        int losses;
        volatile int escalations;
        final List<String> acceptedBy = new ArrayList<>();

        FakeChargebacks(LongSupplier now) {
            this.now = now;
        }

        private View view() {
            return new View("record-1", CHARGEBACK, "event-1", status, deadline);
        }

        @Override
        public synchronized View receive(Receive command) {
            if (status == null) {
                receives++;
                status = ChargebackStatus.RECEIVED;
                deadline = command.responseDeadlineMillis();
            }
            return view();
        }

        @Override
        public synchronized View openDispute(String recordId) {
            disputesOpened++;
            return view();
        }

        @Override
        public synchronized View startReview(String recordId, String actorId, String note) {
            status = ChargebackStatus.UNDER_REVIEW;
            return view();
        }

        @Override
        public synchronized View accept(String recordId, String actorId, String note) {
            acceptedBy.add(actorId);
            status = ChargebackStatus.ACCEPTED;
            return view();
        }

        @Override
        public synchronized View dispute(String recordId, Dispute command) {
            status = ChargebackStatus.DISPUTED;
            return view();
        }

        @Override
        public synchronized View recordWin(String recordId, String actorId, String notes) {
            wins++;
            status = ChargebackStatus.WON;
            return view();
        }

        @Override
        public synchronized View recordLoss(String recordId, String actorId, String notes) {
            losses++;
            status = ChargebackStatus.LOST;
            return view();
        }

        @Override
        public synchronized View recover(String recordId) {
            recoveries++;
            return view();
        }

        @Override
        public synchronized void closeDispute(String recordId) {
            disputesClosed++;
        }

        @Override
        public synchronized void escalate(String recordId) {
            if (ChargebackRules.canDecide(status)) {
                escalations++;
            }
        }

        synchronized List<String> accepted() {
            return List.copyOf(acceptedBy);
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · an accepted chargeback's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        ChargebackWorkflow chargeback = received();
        chargeback.accept(new Decision("admin-1", "customer did not attend"));
        awaitClosed(chargeback);

        assertReplays(WorkflowIds.chargeback(CHARGEBACK), ChargebackWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
