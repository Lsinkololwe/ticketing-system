package com.pml.booking.workflow.finance;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Cancelled;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Completed;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Published;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Rescheduled;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Stage;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Start;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflowImpl;
import com.pml.booking.workflow.refund.RefundWorkflowImpl;
import com.pml.booking.workflow.refund.RefundWorkflowTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An event's finances with time skipped.
 */
@Tag("L3")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001-R4 · an escrow holds seven days past completion, waits out disputes, then pays out and recognises commission once")
class EventFinanceWorkflowTest {

    private static final String EVENT = "event-finance-1";
    private static final long DAY = Duration.ofDays(1).toMillis();

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeFinance finance;
    private FakeCancellation cancellation;
    private RefundWorkflowTest.FakeRefunds refunds;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        finance = new FakeFinance(() -> env.currentTimeMillis());
        cancellation = new FakeCancellation();
        refunds = new RefundWorkflowTest.FakeRefunds();
        Worker worker = env.newWorker(TaskQueues.FINANCE);
        worker.registerWorkflowImplementationTypes(EventFinanceWorkflowImpl.class, CancellationRefundsWorkflowImpl.class, RefundWorkflowImpl.class);
        worker.registerActivitiesImplementations(finance, cancellation, refunds);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(refunds);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("published then completed: held seven days, then eligible and commission recognised once")
    void holdsThenBecomesEligible() {
        EventFinanceWorkflow workflow = started();
        workflow.published(new Published("env-1", "org-1", env.currentTimeMillis() + 2 * DAY));
        workflow.completed(new Completed("env-2", env.currentTimeMillis()));

        env.sleep(Duration.ofDays(6));
        assertThat(finance.eligible).isFalse();
        assertThat(workflow.stage()).isEqualTo(Stage.HOLDING);

        awaitClosed(workflow);
        assertThat(finance.escrowsOpened).isEqualTo(1);
        assertThat(finance.eligible).isTrue();
        assertThat(finance.recognitions).isEqualTo(1);
    }

    @Test
    @DisplayName("a redelivered fact carries the same envelope id and changes nothing")
    void redeliveredFactsAreIgnored() {
        EventFinanceWorkflow workflow = started();
        Published published = new Published("env-1", "org-1", env.currentTimeMillis());
        workflow.published(published);
        workflow.published(published);
        Completed completed = new Completed("env-2", env.currentTimeMillis());
        workflow.completed(completed);
        workflow.completed(completed);

        awaitClosed(workflow);

        assertThat(finance.escrowsOpened).isEqualTo(1);
        assertThat(finance.holdsStarted).isEqualTo(1);
    }

    @Test
    @DisplayName("R7 · a reschedule while holding opens the refund window and moves holdUntil")
    void aRescheduleMovesTheHold() {
        EventFinanceWorkflow workflow = started();
        long start = env.currentTimeMillis();
        workflow.completed(new Completed("env-1", start));
        env.sleep(Duration.ofDays(2));

        workflow.rescheduled(new Rescheduled("env-2", start, start + 5 * DAY));
        env.sleep(Duration.ofDays(7));
        assertThat(finance.eligible).as("the hold now runs to the new date plus seven days").isFalse();
        assertThat(finance.rescheduleWindows).isEqualTo(1);

        awaitClosed(workflow);
        assertThat(finance.eligible).isTrue();
    }

    @Test
    @DisplayName("ET-FIN-003 R1 · an open dispute holds eligibility until it closes")
    void anOpenDisputeHoldsEligibility() {
        finance.openDisputes = 1;
        EventFinanceWorkflow workflow = started();
        workflow.completed(new Completed("env-1", env.currentTimeMillis()));

        env.sleep(Duration.ofDays(9));
        assertThat(workflow.stage()).isEqualTo(Stage.AWAITING_DISPUTES);
        assertThat(finance.eligible).isFalse();

        finance.openDisputes = 0;
        workflow.disputeClosed("chargeback-1");
        awaitClosed(workflow);
        assertThat(finance.eligible).isTrue();
    }

    @Test
    @DisplayName("R6 · a cancellation refunds every live ticket and closes the escrow, without recognising commission")
    void aCancellationRefundsEveryone() {
        cancellation.tickets.addAll(List.of("ticket-a", "ticket-b"));
        EventFinanceWorkflow workflow = started();
        workflow.published(new Published("env-1", "org-1", env.currentTimeMillis()));
        workflow.cancelled(new Cancelled("env-2", "Venue flooded"));

        awaitClosed(workflow);

        assertThat(refunds.completedTickets()).containsExactlyInAnyOrder("ticket-a", "ticket-b");
        assertThat(cancellation.escrowClosed).isTrue();
        assertThat(finance.recognitions).isZero();
    }

    private EventFinanceWorkflow started() {
        EventFinanceWorkflow starter = client.newWorkflowStub(EventFinanceWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.eventFinance(EVENT))
                .setTaskQueue(TaskQueues.FINANCE)
                .build());
        WorkflowClient.start(starter::run, new Start(EVENT));
        return client.newWorkflowStub(EventFinanceWorkflow.class, WorkflowIds.eventFinance(EVENT));
    }

    private static void awaitClosed(EventFinanceWorkflow workflow) {
        WorkflowStub.fromTyped(workflow).getResult(Void.class);
    }

    static final class FakeFinance implements EventFinanceActivities {
        private final LongSupplier now;
        int escrowsOpened;
        int holdsStarted;
        int rescheduleWindows;
        int recognitions;
        volatile int openDisputes;
        boolean eligible;
        long holdUntil;

        FakeFinance(LongSupplier now) {
            this.now = now;
        }

        @Override
        public synchronized void openEscrow(String eventId, String organizationId, long startsAtMillis) {
            escrowsOpened++;
        }

        @Override
        public synchronized void openRescheduleWindow(String eventId, long previousStartsAtMillis, long newStartsAtMillis) {
            rescheduleWindows++;
        }

        @Override
        public synchronized long startHold(String eventId, long completedAtMillis) {
            if (holdsStarted++ == 0) {
                holdUntil = EventFinanceRules.holdUntil(completedAtMillis);
            }
            return holdUntil;
        }

        @Override
        public synchronized long rescheduleHold(String eventId, long newStartsAtMillis) {
            holdUntil = EventFinanceRules.holdUntil(newStartsAtMillis);
            return holdUntil;
        }

        @Override
        public int openDisputes(String eventId) {
            return openDisputes;
        }

        @Override
        public synchronized void makePayoutEligible(String eventId) {
            eligible = true;
        }

        @Override
        public synchronized long recogniseCommission(String eventId) {
            return ++recognitions;
        }
    }

    static final class FakeCancellation implements CancellationRefundsWorkflow.Activities {
        final List<String> tickets = new ArrayList<>();
        boolean escrowClosed;

        @Override
        public synchronized CancellationRefundsWorkflow.Batch nextBatch(String eventId, String afterTicketId, int limit) {
            List<String> next = tickets.stream().sorted()
                    .filter(id -> afterTicketId == null || id.compareTo(afterTicketId) > 0)
                    .limit(limit)
                    .toList();
            return new CancellationRefundsWorkflow.Batch(next, next.isEmpty() ? afterTicketId : next.get(next.size() - 1));
        }

        @Override
        public synchronized boolean closeEscrow(String eventId) {
            escrowClosed = true;
            return true;
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · an event's finance history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        EventFinanceWorkflow workflow = started();
        workflow.published(new Published("env-1", "org-1", env.currentTimeMillis()));
        workflow.completed(new Completed("env-2", env.currentTimeMillis()));
        awaitClosed(workflow);

        assertReplays(WorkflowIds.eventFinance(EVENT), EventFinanceWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
