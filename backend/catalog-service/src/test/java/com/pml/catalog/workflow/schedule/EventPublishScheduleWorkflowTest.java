package com.pml.catalog.workflow.schedule;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.workflow.schedule.EventPublishScheduleWorkflow.Start;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** The scheduled-publication wait, with time skipped: it fires once, at the right time, unless moved or ended first. */
@Tag("L3")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R7 · a scheduled event is published once, at its go-live time, unless the wait is moved or ended")
class EventPublishScheduleWorkflowTest {

    private static final String EVENT = "event-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private Recorder activities;

    @BeforeEach
    void start() {
        env = TestWorkflowEnvironment.newInstance();
        activities = new Recorder();
        Worker worker = env.newWorker(TaskQueues.LIFECYCLE);
        worker.registerWorkflowImplementationTypes(EventPublishScheduleWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void close() {
        env.close();
    }

    private EventPublishScheduleWorkflow open(long publishAtMillis) {
        EventPublishScheduleWorkflow workflow = client.newWorkflowStub(EventPublishScheduleWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.eventPublishSchedule(EVENT))
                        .setTaskQueue(TaskQueues.LIFECYCLE)
                        .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                        .build());
        WorkflowClient.start(workflow::run, new Start(EVENT, publishAtMillis));
        return client.newWorkflowStub(EventPublishScheduleWorkflow.class, WorkflowIds.eventPublishSchedule(EVENT));
    }

    private long in(Duration duration) {
        return env.currentTimeMillis() + duration.toMillis();
    }

    private static void awaitClosed(EventPublishScheduleWorkflow workflow) {
        WorkflowStub.fromTyped(workflow).getResult(Void.class);
    }

    @Test
    @DisplayName("it publishes at the go-live time and not a moment before, exactly once, then closes")
    void publishesAtTheTime() {
        long at = in(Duration.ofDays(1));
        EventPublishScheduleWorkflow workflow = open(at);

        env.sleep(Duration.ofDays(1).minusMinutes(1));
        assertThat(activities.calls).isEmpty();

        env.sleep(Duration.ofMinutes(2));
        awaitClosed(workflow);

        assertThat(activities.calls).containsExactly(EVENT + "@" + at);
    }

    @Test
    @DisplayName("a go-live time already past publishes at once")
    void pastPublishesNow() {
        long at = in(Duration.ofSeconds(-30));
        EventPublishScheduleWorkflow workflow = open(at);

        awaitClosed(workflow);

        assertThat(activities.calls).containsExactly(EVENT + "@" + at);
    }

    @Test
    @DisplayName("moving the time restarts the wait for the new time, and the activity is told the new time")
    void rescheduleMovesIt() {
        EventPublishScheduleWorkflow workflow = open(in(Duration.ofDays(1)));
        long later = in(Duration.ofDays(3));

        workflow.reschedule(later);
        env.sleep(Duration.ofDays(2));
        assertThat(activities.calls).as("the original time has passed and nothing fired").isEmpty();

        env.sleep(Duration.ofDays(1).plusMinutes(1));
        awaitClosed(workflow);

        assertThat(activities.calls).containsExactly(EVENT + "@" + later);
    }

    @Test
    @DisplayName("moving the time earlier brings the wait forward")
    void rescheduleEarlier() {
        EventPublishScheduleWorkflow workflow = open(in(Duration.ofDays(5)));
        long sooner = in(Duration.ofHours(2));

        workflow.reschedule(sooner);
        env.sleep(Duration.ofHours(3));
        awaitClosed(workflow);

        assertThat(activities.calls).containsExactly(EVENT + "@" + sooner);
    }

    @Test
    @DisplayName("cancelling ends the wait and publishes nothing, however long the clock runs")
    void cancelEnds() {
        EventPublishScheduleWorkflow workflow = open(in(Duration.ofDays(1)));

        workflow.cancel();
        awaitClosed(workflow);
        env.sleep(Duration.ofDays(3));

        assertThat(activities.calls).isEmpty();
    }

    @Test
    @DisplayName("a signal-with-start opens the wait and moves an open one, as the organizer's schedule does")
    void signalWithStart() {
        long first = in(Duration.ofDays(1));
        long second = in(Duration.ofDays(2));

        signalWithStart(first);
        signalWithStart(second);
        env.sleep(Duration.ofDays(1).plusHours(1));
        assertThat(activities.calls).as("moved by the second call").isEmpty();
        env.sleep(Duration.ofDays(1));
        awaitClosed(client.newWorkflowStub(EventPublishScheduleWorkflow.class, WorkflowIds.eventPublishSchedule(EVENT)));

        assertThat(activities.calls).containsExactly(EVENT + "@" + second);
    }

    private void signalWithStart(long at) {
        EventPublishScheduleWorkflow workflow = client.newWorkflowStub(EventPublishScheduleWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.eventPublishSchedule(EVENT))
                        .setTaskQueue(TaskQueues.LIFECYCLE)
                        .build());
        BatchRequest request = client.newSignalWithStartRequest();
        request.add(workflow::run, new Start(EVENT, at));
        request.add(workflow::reschedule, at);
        client.signalWithStart(request);
    }

    @Test
    @DisplayName("the history replays against the current code after a move and a publication")
    void replays() throws Exception {
        EventPublishScheduleWorkflow workflow = open(in(Duration.ofDays(1)));
        workflow.reschedule(in(Duration.ofDays(2)));
        env.sleep(Duration.ofDays(2).plusMinutes(1));
        awaitClosed(workflow);

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.eventPublishSchedule(EVENT)).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, EventPublishScheduleWorkflowImpl.class);
    }

    /** Records each publication it is asked for. */
    static final class Recorder implements EventPublishScheduleActivities {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public String publishDue(String eventId, long publishAtMillis) {
            calls.add(eventId + "@" + publishAtMillis);
            return "PUBLISHED";
        }
    }
}
