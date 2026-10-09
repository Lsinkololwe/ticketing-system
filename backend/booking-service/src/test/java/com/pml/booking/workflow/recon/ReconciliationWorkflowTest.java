package com.pml.booking.workflow.recon;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Job;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Result;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Type;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L3")
@Tag("ET-FIN-005")
@DisplayName("ET-FIN-005 · a scheduled run performs exactly the reconciliation its type names")
class ReconciliationWorkflowTest {

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private final List<Type> performed = new ArrayList<>();

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        env.newWorker(TaskQueues.RECON).registerWorkflowImplementationTypes(ReconciliationWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.RECON).registerActivitiesImplementations(new ReconciliationWorkflow.Activities() {
            @Override
            public Result escrow() {
                return performed(Type.ESCROW);
            }

            @Override
            public Result escrowJournal() {
                return performed(Type.ESCROW_JOURNAL);
            }

            @Override
            public Result alerts() {
                return performed(Type.ALERTS);
            }

            @Override
            public Result weeklySummary() {
                return performed(Type.WEEKLY_SUMMARY);
            }
        });
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("each type runs its own activity and nothing else")
    void eachTypeRunsItsActivity() {
        for (Type type : Type.values()) {
            ReconciliationWorkflow run = client.newWorkflowStub(ReconciliationWorkflow.class, WorkflowOptions.newBuilder()
                    .setWorkflowId("recon/" + type + "/" + UUID.randomUUID())
                    .setTaskQueue(TaskQueues.RECON)
                    .build());

            assertThat(run.run(new Job(type)).type()).isEqualTo(type);
        }

        assertThat(performed).containsExactly(Type.values());
    }

    private synchronized Result performed(Type type) {
        performed.add(type);
        return new Result(type, "run-" + type, 0L);
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a reconciliation run's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        String id = "recon/ESCROW/replay";
        client.newWorkflowStub(ReconciliationWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(id).setTaskQueue(TaskQueues.RECON).build()).run(new Job(Type.ESCROW));

        assertReplays(id, ReconciliationWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
