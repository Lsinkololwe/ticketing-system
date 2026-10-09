package com.pml.booking.infrastructure.temporal;

import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.TemporalDevServer;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * A worker registers, polls and completes a run against a real Temporal server,
 * and the server itself enforces a workflow id's conflict policy.
 *
 * <p>The time-skipping environment proves a workflow's logic; it does not prove that the namespace
 * exists, that gRPC reaches the frontend, or that {@code USE_EXISTING} and {@code FAIL} behave as the
 * workflow-id conflict policies rely on. Those are properties of the server, so they are asserted against one.
 */
@Tag("L2")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015-R8 · a workflow round-trips against the Temporal development server")
class TemporalRoundTripTest {

    @WorkflowInterface
    public interface Echo {
        @WorkflowMethod
        String run(String input);
    }

    @ActivityInterface
    public interface Shout {
        String shout(String input);
    }

    public static class EchoImpl implements Echo {
        private final Shout shout = Workflow.newActivityStub(Shout.class, ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .build());

        @Override
        public String run(String input) {
            Workflow.await(Duration.ofMillis(300), () -> false);
            return shout.shout(input);
        }
    }

    @WorkflowInterface
    public interface Tally {
        @WorkflowMethod
        void run(String owner);

        @UpdateMethod
        int add(int amount);
    }

    public static class TallyImpl implements Tally {
        private int total;

        @Override
        public void run(String owner) {
            Workflow.await(Duration.ofSeconds(5), () -> total >= 100);
        }

        @Override
        public int add(int amount) {
            total += amount;
            return total;
        }
    }

    private static WorkflowServiceStubs service;
    private static WorkflowClient client;
    private static WorkerFactory factory;
    private static final String QUEUE = TaskQueues.CHECKOUT + "-roundtrip-" + UUID.randomUUID();

    @BeforeAll
    static void connect() {
        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(TemporalDevServer.target())
                .build());
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().until(() ->
                service.blockingStub().describeNamespace(DescribeNamespaceRequest.newBuilder()
                        .setNamespace(TemporalDevServer.NAMESPACE).build()) != null);
        client = WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder()
                .setNamespace(TemporalDevServer.NAMESPACE)
                .build());
        factory = WorkerFactory.newInstance(client);
        factory.newWorker(QUEUE).registerWorkflowImplementationTypes(EchoImpl.class, TallyImpl.class);
        factory.getWorker(QUEUE).registerActivitiesImplementations((Shout) input -> input.toUpperCase());
        factory.start();
    }

    @AfterAll
    static void disconnect() {
        factory.shutdownNow();
        service.shutdownNow();
    }

    @Test
    @DisplayName("a started workflow runs its activity on the worker and returns the result")
    void aWorkflowCompletes() {
        Echo echo = client.newWorkflowStub(Echo.class, options(WorkflowIds.purchase("rt-" + UUID.randomUUID()),
                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL));

        assertThat(echo.run("paid")).isEqualTo("PAID");
    }

    @Test
    @DisplayName("USE_EXISTING: a second start under the same business id reaches the running execution")
    void useExistingReachesTheSameRun() {
        String id = WorkflowIds.purchase("rt-" + UUID.randomUUID());
        WorkflowOptions options = options(id, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING);

        String first = WorkflowClient.start(client.newWorkflowStub(Echo.class, options)::run, "a").getRunId();
        String second = WorkflowClient.start(client.newWorkflowStub(Echo.class, options)::run, "b").getRunId();

        assertThat(second).as("one execution per business id").isEqualTo(first);
        assertThat(WorkflowStub.fromTyped(client.newWorkflowStub(Echo.class, id)).getResult(String.class))
                .as("the first start's input is the one that ran").isEqualTo("A");
    }

    @Test
    @DisplayName("FAIL: a second start while the first is open is refused by the server")
    void failRefusesASecondStart() {
        String id = WorkflowIds.payout("rt-" + UUID.randomUUID());
        WorkflowOptions options = options(id, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL);

        WorkflowClient.start(client.newWorkflowStub(Echo.class, options)::run, "first");

        assertThatThrownBy(() -> WorkflowClient.start(client.newWorkflowStub(Echo.class, options)::run, "second"))
                .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
    }

    @Test
    @DisplayName("a workflow started with ProcessSearchAttributes carries BusinessId, EventId and ProcessKind")
    void startCarriesSearchAttributes() {
        String reservationId = "rt-" + UUID.randomUUID();
        String id = WorkflowIds.purchase(reservationId);
        Echo echo = new TemporalGateway(client).newWorkflow(Echo.class, id, QUEUE,
                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("Purchase", reservationId).eventId("ev-1").tenantId(null).build());

        WorkflowClient.start(echo::run, "x");

        var described = WorkflowStub.fromTyped(client.newWorkflowStub(Echo.class, id)).describe();
        var attributes = described.getTypedSearchAttributes();
        assertThat(attributes.get(ProcessSearchAttributes.BUSINESS_ID)).isEqualTo(reservationId);
        assertThat(attributes.get(ProcessSearchAttributes.EVENT_ID)).isEqualTo("ev-1");
        assertThat(attributes.get(ProcessSearchAttributes.PROCESS_KIND)).isEqualTo("Purchase");
        assertThat(attributes.containsKey(ProcessSearchAttributes.TENANT_ID)).isFalse();
    }

    private static WorkflowOptions options(String id, WorkflowIdConflictPolicy policy) {
        return WorkflowOptions.newBuilder()
                .setWorkflowId(id)
                .setTaskQueue(QUEUE)
                .setWorkflowIdConflictPolicy(policy)
                .build();
    }
    @Test
    @DisplayName("Update-with-Start under USE_EXISTING: two calls reach one execution and both updates apply to it")
    void updateWithStartReachesOneExecution() {
        String id = WorkflowIds.purchase("rt-" + UUID.randomUUID());
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(id)
                .setTaskQueue(QUEUE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build();

        int first = updateWithStart(options, 30);
        int second = updateWithStart(options, 12);

        assertThat(first).isEqualTo(30);
        assertThat(second).as("the second call reached the running execution, not a new one").isEqualTo(42);
    }

    private static int updateWithStart(WorkflowOptions options, int amount) {
        Tally tally = client.newWorkflowStub(Tally.class, options);
        return WorkflowClient.startUpdateWithStart(tally::add, amount,
                        UpdateOptions.<Integer>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                        new WithStartWorkflowOperation<>(tally::run, "owner"))
                .getResult();
    }
}
