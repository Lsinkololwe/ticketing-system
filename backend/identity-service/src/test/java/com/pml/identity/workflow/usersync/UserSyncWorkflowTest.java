package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Change;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Progress;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Start;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.BatchRequest;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A user's Keycloak changes, applied in order, once each, with retries.
 */
@Tag("L3")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-002-R2 · Keycloak changes reach identity_users once each, in order, and survive failures")
class UserSyncWorkflowTest {

    private static final String USER = "kc-user-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeUsers users;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        users = new FakeUsers();
        Worker worker = env.newWorker(TaskQueues.ONBOARDING);
        worker.registerWorkflowImplementationTypes(UserSyncWorkflowImpl.class);
        worker.registerActivitiesImplementations(users);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R2 · signal-with-start and later signals are applied in the order they arrived")
    void changesApplyInOrder() {
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-2", Kind.LOGIN));
        workflow().keycloakEvent(change("e-3", Kind.DELETE));

        until(progress -> progress.applied() == 3);

        assertThat(users.applied).containsExactly("SYNC", "LOGIN", "DELETE");
    }

    @Test
    @DisplayName("PLT-015 R6 · a change delivered twice with one originating id is applied once")
    void aRedeliveryIsDropped() {
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-2", Kind.LOGIN));

        until(progress -> progress.applied() == 2);

        assertThat(users.applied).containsExactly("SYNC", "LOGIN");
        assertThat(workflow().progress().duplicates()).isEqualTo(1);
    }

    @Test
    @DisplayName("R2 · a failing write is retried in the workflow rather than lost")
    void failuresAreRetried() {
        users.syncFailuresRemaining = 2;
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));

        until(progress -> progress.applied() == 1);

        assertThat(users.syncAttempts).isEqualTo(3);
    }

    @Test
    @DisplayName("a refusal is written down as a fact, and the next change still applies")
    void aRefusedChangeDoesNotBlockTheNext() {
        users.syncRefusal = true;
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-2", Kind.LOGIN));

        until(progress -> progress.failed() == 1 && progress.applied() == 1);

        assertThat(users.syncAttempts).as("a refusal is not retried").isEqualTo(1);
        assertThat(users.failures).hasSize(1);
        assertThat(users.failures.get(0).change()).isEqualTo("SYNC");
        assertThat(users.applied).containsExactly("LOGIN");
    }

    @Test
    @DisplayName("a change that exhausts its retries is recorded and ends the run as failed - it is not swallowed")
    void anExhaustedChangeIsSurfaced() {
        users.syncFailuresRemaining = Integer.MAX_VALUE;
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow()).getResult(Void.class))
                .isInstanceOf(io.temporal.client.WorkflowFailedException.class);

        assertThat(users.syncAttempts).isEqualTo(UserSyncRules.ATTEMPTS);
        assertThat(users.failures).hasSize(1);
    }

    @Test
    @DisplayName("an execution with nothing to apply for a day closes")
    void anIdleExecutionCloses() {
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));
        until(progress -> progress.applied() == 1);

        env.sleep(UserSyncRules.IDLE_CLOSE.plusMinutes(1));

        WorkflowStub.fromTyped(workflow()).getResult(Void.class);
    }

    @Test
    @DisplayName("Continue-As-New carries the remembered ids and unapplied changes; a redelivery across it is still dropped")
    void continueAsNewCarriesItsMemory() throws Exception {
        signalWithStart(2, change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-2", Kind.LOGIN));
        workflow().keycloakEvent(change("e-3", Kind.SYNC));

        until(progress -> users.applied.size() == 3);
        workflow().keycloakEvent(change("e-1", Kind.SYNC));
        until(progress -> progress.duplicates() == 1);

        assertThat(users.applied).containsExactly("SYNC", "LOGIN", "SYNC");
        assertThat(workflow().progress().recentEventIds()).contains("e-1", "e-2", "e-3");

        env.sleep(UserSyncRules.IDLE_CLOSE.plusMinutes(1));
        WorkflowStub.fromTyped(workflow()).getResult(Void.class);
        assertReplays(WorkflowIds.userSync(USER), UserSyncWorkflowImpl.class);
    }

    @Test
    @DisplayName("PLT-015 R3 · a run's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        signalWithStart(UserSyncRules.MAX_CHANGES_PER_RUN, change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-1", Kind.SYNC));
        workflow().keycloakEvent(change("e-2", Kind.DELETE));
        until(progress -> progress.applied() == 2);
        env.sleep(UserSyncRules.IDLE_CLOSE.plusMinutes(1));
        WorkflowStub.fromTyped(workflow()).getResult(Void.class);

        assertReplays(WorkflowIds.userSync(USER), UserSyncWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private static Change change(String eventId, Kind kind) {
        return new Change(eventId, kind, false, 1L, null);
    }

    private void signalWithStart(int maxChangesPerRun, Change first) {
        UserSyncWorkflow workflow = client.newWorkflowStub(UserSyncWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.userSync(USER))
                .setTaskQueue(TaskQueues.ONBOARDING)
                .build());
        BatchRequest request = client.newSignalWithStartRequest();
        request.add(workflow::run, new Start(USER, List.of(), List.of(), maxChangesPerRun));
        request.add(workflow::keycloakEvent, first);
        client.signalWithStart(request);
    }

    private UserSyncWorkflow workflow() {
        return client.newWorkflowStub(UserSyncWorkflow.class, WorkflowIds.userSync(USER));
    }

    private void until(Predicate<Progress> condition) {
        for (int step = 0; step < 2_000; step++) {
            if (condition.test(workflow().progress())) {
                return;
            }
            env.sleep(Duration.ofSeconds(5));
        }
        assertThat(condition.test(workflow().progress())).as("condition reached").isTrue();
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    static final class FakeUsers implements UserSyncActivities {
        final List<String> applied = new CopyOnWriteArrayList<>();
        final List<Failure> failures = new CopyOnWriteArrayList<>();
        volatile int syncFailuresRemaining;
        volatile int syncAttempts;
        volatile boolean syncRefusal;

        @Override
        public synchronized void syncIn(Target target) {
            syncAttempts++;
            if (syncRefusal) {
                throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure("conflict", "USER_SYNC_CONFLICT");
            }
            if (syncFailuresRemaining > 0) {
                syncFailuresRemaining--;
                throw new RuntimeException("mongodb unavailable");
            }
            applied.add("SYNC");
        }

        @Override
        public synchronized void recordLoginIn(Target target) {
            applied.add("LOGIN");
        }

        @Override
        public synchronized void deleteIn(Target target) {
            applied.add("DELETE");
        }

        @Override
        public void recordFailure(Failure failure) {
            failures.add(failure);
        }

        // the pre-realm activities: a version-1 run never calls them
        @Override
        public void sync(String keycloakUserId) {
            throw new AssertionError("version 1 calls syncIn");
        }

        @Override
        public void recordLogin(String keycloakUserId) {
            throw new AssertionError("version 1 calls recordLoginIn");
        }

        @Override
        public void delete(String keycloakUserId) {
            throw new AssertionError("version 1 calls deleteIn");
        }
    }
}
