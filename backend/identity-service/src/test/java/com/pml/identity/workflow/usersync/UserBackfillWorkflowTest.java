package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.usersync.UserBackfillWorkflow.Start;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A backfill hands every Keycloak user to their own sync workflow, one page per run.
 */
@Tag("L3")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-002 · a Keycloak backfill reaches every user once, survives outages and replays")
class UserBackfillWorkflowTest {

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeBackfill backfill;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        backfill = new FakeBackfill();
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(UserBackfillWorkflowImpl.class);
        worker.registerActivitiesImplementations(backfill);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("every user is handed on exactly once, in Keycloak's order, across pages and Continue-As-New")
    void everyUserIsEnqueuedOnce() {
        backfill.users(250);

        runToCompletion("b-1");

        assertThat(backfill.enqueued).hasSize(250).doesNotHaveDuplicates().containsExactlyElementsOf(backfill.users);
        assertThat(backfill.offsets).containsExactly(0, 100, 200);
        assertThat(backfill.backfillIds).containsExactly("b-1");
    }

    @Test
    @DisplayName("a realm whose user count is a multiple of the page ends on an empty page")
    void anExactMultipleEndsOnAnEmptyPage() {
        backfill.users(200);

        runToCompletion("b-1");

        assertThat(backfill.enqueued).hasSize(200);
        assertThat(backfill.offsets).containsExactly(0, 100, 200);
    }

    @Test
    @DisplayName("an empty realm finishes at once and hands nothing on")
    void anEmptyRealmFinishes() {
        runToCompletion("b-1");

        assertThat(backfill.enqueued).isEmpty();
        assertThat(backfill.offsets).containsExactly(0);
    }

    @Test
    @DisplayName("a Keycloak outage is retried inside the workflow, never skipped")
    void anOutageIsRetried() {
        backfill.users(30);
        backfill.pageFailuresRemaining = 2;

        runToCompletion("b-1");

        assertThat(backfill.pageAttempts).isEqualTo(3);
        assertThat(backfill.enqueued).hasSize(30);
    }

    @Test
    @DisplayName("R4 · a scheduled run with no backfill id takes one from its clock and keeps it across pages")
    void aScheduledRunNamesItself() {
        backfill.users(150);

        runToCompletion(null);

        assertThat(backfill.backfillIds).hasSize(1);
        assertThat(backfill.backfillIds.iterator().next()).isNotBlank().containsOnlyDigits();
        assertThat(backfill.enqueued).hasSize(150);
    }

    @Test
    @DisplayName("each change's originating id is unique to its backfill and stable within it")
    void eventIds() {
        assertThat(UserBackfillRules.eventId("b-1", "kc-1")).isEqualTo(UserBackfillRules.eventId("b-1", "kc-1"));
        assertThat(UserBackfillRules.eventId("b-1", "kc-1")).isNotEqualTo(UserBackfillRules.eventId("b-2", "kc-1"));
        assertThat(UserBackfillRules.lastPage(UserBackfillRules.PAGE_SIZE)).isFalse();
        assertThat(UserBackfillRules.lastPage(UserBackfillRules.PAGE_SIZE - 1)).isTrue();
    }

    @Test
    @DisplayName("PLT-015 R3 · the final run's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        backfill.users(120);
        runToCompletion("b-1");

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.userBackfill()).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, UserBackfillWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private void runToCompletion(String backfillId) {
        UserBackfillWorkflow workflow = client.newWorkflowStub(UserBackfillWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.userBackfill())
                .setTaskQueue(TaskQueues.ONBOARDING)
                .build());
        WorkflowClient.start(workflow::run, new Start(backfillId, 0, 0));
        client.newUntypedWorkflowStub(WorkflowIds.userBackfill()).getResult(Void.class);
    }

    static final class FakeBackfill implements UserBackfillActivities {
        volatile List<String> users = List.of();
        final List<String> enqueued = new CopyOnWriteArrayList<>();
        final List<Integer> offsets = new CopyOnWriteArrayList<>();
        final Set<String> backfillIds = ConcurrentHashMap.newKeySet();
        volatile int pageFailuresRemaining;
        volatile int pageAttempts;

        void users(int count) {
            users = IntStream.range(0, count).mapToObj(index -> "kc-" + index).toList();
        }

        @Override
        public synchronized List<String> page(int offset, int size) {
            pageAttempts++;
            if (pageFailuresRemaining > 0) {
                pageFailuresRemaining--;
                throw new RuntimeException("keycloak unavailable");
            }
            offsets.add(offset);
            int from = Math.min(offset, users.size());
            return List.copyOf(users.subList(from, Math.min(from + size, users.size())));
        }

        @Override
        public synchronized void enqueue(String backfillId, List<String> keycloakUserIds) {
            backfillIds.add(backfillId);
            enqueued.addAll(keycloakUserIds);
        }
    }
}
