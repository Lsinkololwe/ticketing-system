package com.pml.booking.workflow.refund;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow.Batch;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow.Start;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow.Summary;
import com.pml.shared.constants.RefundRequestStatus;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A cancelled event's mass refund, batched and continued as new.
 */
@Tag("L3")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004-R6 · a cancellation refunds every live ticket once, across batches, then closes the escrow")
class CancellationRefundsWorkflowTest {

    private static final String EVENT = "event-cancelled-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private RefundWorkflowTest.FakeRefunds refunds;
    private Tickets tickets;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        refunds = new RefundWorkflowTest.FakeRefunds();
        tickets = new Tickets();
        Worker worker = env.newWorker(TaskQueues.FINANCE);
        worker.registerWorkflowImplementationTypes(CancellationRefundsWorkflowImpl.class, RefundWorkflowImpl.class);
        worker.registerActivitiesImplementations(refunds, tickets);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(refunds);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("250 tickets: two batches, 250 completed refunds, the escrow closed once")
    void everyTicketIsRefundedAcrossBatches() {
        IntStream.range(0, 250).forEach(i -> tickets.live.add(String.format("ticket-%04d", i)));

        Summary summary = run();

        assertThat(summary.refundsStarted()).isEqualTo(250);
        assertThat(summary.escrowClosed()).isTrue();
        assertThat(refunds.completedTickets()).hasSize(250).doesNotHaveDuplicates();
        assertThat(tickets.batchCalls).as("a full batch, a partial batch").isEqualTo(2);
        assertThat(tickets.closes).isEqualTo(1);
    }

    @Test
    @DisplayName("R4 · a ticket whose refund is already open is not refunded a second time")
    void anOpenRefundIsNotDoubled() {
        tickets.live.addAll(List.of("ticket-a", "ticket-b"));
        refunds.amount = new BigDecimal("1500.00");
        RefundWorkflow buyerRefund = client.newWorkflowStub(RefundWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.refund("ticket-a"))
                .setTaskQueue(TaskQueues.FINANCE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build());
        WorkflowClient.start(buyerRefund::run, new RefundWorkflow.Start("ticket-a", null));
        client.newWorkflowStub(RefundWorkflow.class, WorkflowIds.refund("ticket-a"))
                .submit(new RefundWorkflow.Submit(RefundWorkflow.Submit.Kind.BUYER, "ticket-a", "cannot attend", "buyer-1", null, false));

        refunds.amount = new BigDecimal("200.00");
        Summary summary = run();

        assertThat(summary.refundsStarted()).isEqualTo(2);
        assertThat(refunds.automatic).containsExactly("ticket-b");
        assertThat(refunds.status("ticket-a")).as("the buyer's own refund still waits for its approver").isEqualTo(RefundRequestStatus.PENDING);
    }

    private Summary run() {
        CancellationRefundsWorkflow workflow = client.newWorkflowStub(CancellationRefundsWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.cancellationRefunds(EVENT))
                .setTaskQueue(TaskQueues.FINANCE)
                .build());
        WorkflowClient.start(workflow::run, new Start(EVENT, "Venue flooded", null, 0));
        return WorkflowStub.fromTyped(client.newWorkflowStub(CancellationRefundsWorkflow.class, WorkflowIds.cancellationRefunds(EVENT)))
                .getResult(Summary.class);
    }

    static final class Tickets implements CancellationRefundsWorkflow.Activities {
        final List<String> live = new ArrayList<>();
        int batchCalls;
        int closes;

        @Override
        public synchronized Batch nextBatch(String eventId, String afterTicketId, int limit) {
            batchCalls++;
            List<String> next = live.stream().sorted()
                    .filter(id -> afterTicketId == null || id.compareTo(afterTicketId) > 0)
                    .limit(limit)
                    .toList();
            return new Batch(next, next.isEmpty() ? afterTicketId : next.get(next.size() - 1));
        }

        @Override
        public synchronized boolean closeEscrow(String eventId) {
            closes++;
            return true;
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a cancellation's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        tickets.live.addAll(List.of("ticket-x", "ticket-y", "ticket-z"));
        run();

        assertReplays(WorkflowIds.cancellationRefunds(EVENT), CancellationRefundsWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
