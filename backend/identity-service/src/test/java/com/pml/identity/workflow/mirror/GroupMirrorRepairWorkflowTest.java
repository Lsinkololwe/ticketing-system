package com.pml.identity.workflow.mirror;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The run the group-mirror Schedule starts, as the Schedule starts it: by type name.
 */
@Tag("L3")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R8 · a Schedule run repairs one batch, and only its own type is served")
class GroupMirrorRepairWorkflowTest {

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeMirror mirror;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        mirror = new FakeMirror();
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(GroupMirrorRepairWorkflowImpl.class);
        worker.registerActivitiesImplementations(mirror);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R8 · a run calls the repair once and reports how many memberships it repaired")
    void aRunRepairsOnce() {
        mirror.repaired = 7;

        WorkflowStub run = start(GroupMirrorSchedule.WORKFLOW_TYPE);

        assertThat(run.getResult(Integer.class)).isEqualTo(7);
        assertThat(mirror.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failing pass is retried briefly, then the run fails and the next interval tries again")
    void aFailingPassIsBounded() {
        mirror.failing = true;

        WorkflowStub run = start(GroupMirrorSchedule.WORKFLOW_TYPE);

        assertThatThrownBy(() -> run.getResult(Integer.class)).isInstanceOf(WorkflowFailedException.class);
        assertThat(mirror.calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("a start of any other type on identity-onboarding fails loudly and repairs nothing")
    void anotherTypeIsRefused() {
        WorkflowStub run = start("PayoutWorkflow");

        assertThatThrownBy(() -> run.getResult(Integer.class)).isInstanceOf(WorkflowFailedException.class);
        assertThat(mirror.calls.get()).isZero();
    }

    @Test
    @DisplayName("PLT-015 R3 · a repair run's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        start(GroupMirrorSchedule.WORKFLOW_TYPE).getResult(Integer.class);

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.groupMirrorRepair()).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, GroupMirrorRepairWorkflowImpl.class);
    }

    private WorkflowStub start(String type) {
        WorkflowStub run = client.newUntypedWorkflowStub(type, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.groupMirrorRepair())
                .setTaskQueue(TaskQueues.ONBOARDING)
                .build());
        run.start();
        return run;
    }

    static final class FakeMirror implements GroupMirrorActivities {
        final AtomicInteger calls = new AtomicInteger();
        volatile int repaired;
        volatile boolean failing;

        @Override
        public int repairPending() {
            calls.incrementAndGet();
            if (failing) {
                throw new RuntimeException("keycloak unavailable");
            }
            return repaired;
        }
    }
}
