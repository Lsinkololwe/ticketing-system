package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Cancel;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Command;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Reschedule;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Start;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.View;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lifecycle workflow end to end, with time skipped.
 *
 * <p>The event store is an in-memory stand-in that counts transitions, so each test asserts how many
 * times an event completed, moved or ended rather than which calls were made. The MongoDB side of
 * the same steps is proven against a replica set in {@code EventLifecycleWritesTest}.
 */
@Tag("L3")
@Tag("ET-CAT-001")
@DisplayName("ET-CAT-001-R4/R5/R6/R7 · a published event completes at its end, once, unless it is moved or ended first")
class EventLifecycleWorkflowTest {

    private static final String EVENT = "event-1";
    private static final String ORGANIZER = "organizer-1";
    private static final Duration LENGTH = Duration.ofHours(4);

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeEvents events;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        events = new FakeEvents();
        Worker worker = env.newWorker(TaskQueues.LIFECYCLE);
        worker.registerWorkflowImplementationTypes(EventLifecycleWorkflowImpl.class);
        worker.registerActivitiesImplementations(events);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R6 · a published event completes at its end and not a moment before, exactly once")
    void completesAtItsEnd() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();

        env.sleep(Duration.ofDays(1).plus(LENGTH).minusMinutes(5));
        assertThat(events.completions).isZero();
        assertThat(lifecycle.current().status()).isEqualTo(EventStatus.PUBLISHED);

        env.sleep(Duration.ofMinutes(10));
        awaitClosed(lifecycle);

        assertThat(events.completions).isEqualTo(1);
        assertThat(events.status).isEqualTo(EventStatus.COMPLETED);
    }

    @Test
    @DisplayName("R5 · a reschedule moves the completion timer to the new end")
    void aRescheduleMovesTheTimer() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();
        long newStart = events.startsAt + Duration.ofDays(2).toMillis();

        View moved = lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER, newStart, "Headliner delayed"));
        assertThat(moved.endsAtMillis()).isEqualTo(newStart + LENGTH.toMillis());

        env.sleep(Duration.ofDays(1).plus(LENGTH).plusHours(1));
        assertThat(events.completions).as("the original end has passed without completing").isZero();

        env.sleep(Duration.ofDays(2));
        awaitClosed(lifecycle);
        assertThat(events.completions).isEqualTo(1);
        assertThat(events.reschedules).isEqualTo(1);
    }

    @Test
    @DisplayName("§4 · a fourth reschedule is refused, and the refusal never enters the history")
    void theFourthRescheduleIsRefused() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();
        for (int round = 1; round <= LifecycleRules.MAX_RESCHEDULES; round++) {
            lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER, events.startsAt + Duration.ofDays(1).toMillis(),
                    "Venue change number " + round));
        }

        assertThatThrownBy(() -> lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER,
                events.startsAt + Duration.ofDays(1).toMillis(), "Venue change number 4")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));

        assertThat(events.reschedules).isEqualTo(LifecycleRules.MAX_RESCHEDULES);
        assertThat(acceptedUpdates()).as("the publication and three reschedules").isEqualTo(4);
    }

    @Test
    @DisplayName("R5 · a reschedule into the past, or without a reason, is refused as malformed")
    void aMalformedRescheduleIsRefused() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();

        assertThatThrownBy(() -> lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER,
                env.currentTimeMillis() - 1_000L, "Headliner delayed")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
        assertThatThrownBy(() -> lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER,
                events.startsAt + Duration.ofDays(1).toMillis(), " ")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));

        assertThat(events.reschedules).isZero();
        assertThat(acceptedUpdates()).isEqualTo(1);
    }

    @Test
    @DisplayName("R7 · a cancellation ends the execution and the event never completes")
    void aCancellationEndsTheExecution() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();

        View cancelled = lifecycle.cancel(new Cancel(EVENT, ORGANIZER, "Venue flooded"));
        awaitClosed(lifecycle);

        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(events.cancels).isEqualTo(1);
        assertThat(events.completions).isZero();
    }

    @Test
    @DisplayName("R7 · a cancellation without a reason is refused")
    void aCancellationNeedsAReason() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();

        assertThatThrownBy(() -> lifecycle.cancel(new Cancel(EVENT, ORGANIZER, "")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
        assertThat(events.cancels).isZero();
    }

    @Test
    @DisplayName("R4 · one sold ticket blocks unpublishing; with none, the event returns to APPROVED and the execution ends")
    void unpublishingNeedsZeroSold() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();
        events.sold = 1;

        assertThatThrownBy(() -> lifecycle.unpublish(new Command(EVENT, ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));
        assertThat(lifecycle.current().status()).isEqualTo(EventStatus.PUBLISHED);

        events.sold = 0;
        View unpublished = lifecycle.unpublish(new Command(EVENT, ORGANIZER));
        awaitClosed(lifecycle);

        assertThat(unpublished.status()).isEqualTo(EventStatus.APPROVED);
        assertThat(events.completions).isZero();
    }

    @Test
    @DisplayName("R1 · publishing a published event is refused, and it is published once")
    void publishingTwiceIsRefused() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();

        assertThatThrownBy(() -> lifecycle.publish(new Command(EVENT, ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));
        assertThat(events.publishes).isEqualTo(1);
    }

    @Test
    @DisplayName("a refused publication closes the execution it opened")
    void aRefusedPublicationCloses() {
        scheduleIn(Duration.ofDays(1));
        events.refusePublish = true;
        WorkflowClient.start(starter()::run, new Start(EVENT, false));
        EventLifecycleWorkflow lifecycle = client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowIds.eventLifecycle(EVENT));

        assertThatThrownBy(() -> lifecycle.publish(new Command(EVENT, ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));
        awaitClosed(lifecycle);
        assertThat(events.publishes).isZero();
    }

    @Test
    @DisplayName("R7 · cancelling an approved event through Update-with-Start opens and closes its own execution")
    void cancellingAnApprovedEventThroughUpdateWithStart() {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow workflow = starter();

        View cancelled = WorkflowClient.startUpdateWithStart(workflow::cancel, new Cancel(EVENT, ORGANIZER, "Artist withdrew"),
                        UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                        new WithStartWorkflowOperation<>(workflow::run, new Start(EVENT, false)))
                .getResult();
        awaitClosed(client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowIds.eventLifecycle(EVENT)));

        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(events.cancels).isEqualTo(1);
    }

    @Test
    @DisplayName("R6 · an adopted event whose end has already passed completes at once")
    void anAdoptedEventAlreadyOverCompletes() {
        events.status = EventStatus.PUBLISHED;
        events.startsAt = env.currentTimeMillis() - Duration.ofDays(1).toMillis();
        events.endsAt = env.currentTimeMillis() - Duration.ofHours(20).toMillis();

        WorkflowClient.start(starter()::run, new Start(EVENT, true));
        awaitClosed(client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowIds.eventLifecycle(EVENT)));

        assertThat(events.completions).isEqualTo(1);
    }

    @Test
    @DisplayName("adopting an event that is not published closes quietly")
    void adoptingAnUnpublishedEventCloses() {
        scheduleIn(Duration.ofHours(1));

        WorkflowClient.start(starter()::run, new Start(EVENT, true));
        awaitClosed(client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowIds.eventLifecycle(EVENT)));

        assertThat(events.completions).isZero();
    }

    @Test
    @DisplayName("ET-PLT-015 R3 · a published, rescheduled and completed lifecycle replays against the current implementation")
    void aCompletedLifecycleReplays() throws Exception {
        scheduleIn(Duration.ofDays(1));
        EventLifecycleWorkflow lifecycle = open();
        lifecycle.reschedule(new Reschedule(EVENT, ORGANIZER, events.startsAt + Duration.ofDays(1).toMillis(),
                "Headliner delayed"));
        env.sleep(Duration.ofDays(3));
        awaitClosed(lifecycle);

        assertThat(events.completions).isEqualTo(1);
        assertReplays(WorkflowIds.eventLifecycle(EVENT), EventLifecycleWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    private void scheduleIn(Duration fromNow) {
        events.startsAt = env.currentTimeMillis() + fromNow.toMillis();
        events.endsAt = events.startsAt + LENGTH.toMillis();
    }

    private EventLifecycleWorkflow starter() {
        return client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.eventLifecycle(EVENT))
                .setTaskQueue(TaskQueues.LIFECYCLE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build());
    }

    private EventLifecycleWorkflow open() {
        WorkflowClient.start(starter()::run, new Start(EVENT, false));
        EventLifecycleWorkflow lifecycle = client.newWorkflowStub(EventLifecycleWorkflow.class, WorkflowIds.eventLifecycle(EVENT));
        lifecycle.publish(new Command(EVENT, ORGANIZER));
        return lifecycle;
    }

    private static void awaitClosed(EventLifecycleWorkflow lifecycle) {
        WorkflowStub.fromTyped(lifecycle).getResult(Void.class);
    }

    private long acceptedUpdates() {
        WorkflowExecution execution = WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.eventLifecycle(EVENT)).build();
        return env.getWorkflowExecutionHistory(execution).getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_ACCEPTED)
                .count();
    }

    /** The event store, in memory: a status, a schedule, and counts of every transition. */
    static final class FakeEvents implements EventLifecycleActivities {
        volatile EventStatus status = EventStatus.APPROVED;
        volatile long startsAt;
        volatile long endsAt;
        volatile int reschedules;
        volatile int completions;
        volatile int publishes;
        volatile int cancels;
        volatile int sold;
        volatile boolean refusePublish;

        private View view() {
            return new View(EVENT, status, startsAt, endsAt, reschedules);
        }

        @Override
        public synchronized View current(String eventId) {
            return view();
        }

        @Override
        public synchronized View publish(String eventId) {
            if (refusePublish) {
                throw Refusals.refusal(ErrorCode.EVENT_STATE_INVALID, "the organization is not active");
            }
            if (status == EventStatus.APPROVED) {
                status = EventStatus.PUBLISHED;
                publishes++;
            } else if (status != EventStatus.PUBLISHED) {
                throw Refusals.refusal(ErrorCode.EVENT_STATE_INVALID, "the event is " + status);
            }
            return view();
        }

        @Override
        public synchronized View reschedule(String eventId, long newStartsAtMillis, String reason) {
            if (status != EventStatus.PUBLISHED) {
                throw Refusals.refusal(ErrorCode.EVENT_STATE_INVALID, "the event is " + status);
            }
            if (newStartsAtMillis != startsAt) {
                long length = endsAt - startsAt;
                startsAt = newStartsAtMillis;
                endsAt = newStartsAtMillis + length;
                reschedules++;
            }
            return view();
        }

        @Override
        public synchronized View cancel(String eventId, String reason) {
            if (status != EventStatus.CANCELLED) {
                status = EventStatus.CANCELLED;
                cancels++;
            }
            return view();
        }

        @Override
        public synchronized View unpublish(String eventId) {
            if (sold > 0) {
                throw Refusals.refusal(ErrorCode.EVENT_STATE_INVALID, "tickets are sold");
            }
            if (status == EventStatus.PUBLISHED) {
                status = EventStatus.APPROVED;
            }
            return view();
        }

        @Override
        public synchronized View complete(String eventId) {
            if (status == EventStatus.PUBLISHED) {
                status = EventStatus.COMPLETED;
                completions++;
            }
            return view();
        }
    }
}
