package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Cancel;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Start;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reminder workflow's two timers, under time skipping.
 */
@Tag("L3")
@Tag("ET-NTF-002")
@DisplayName("ET-NTF-002-R5 · reminders fire at minus 24 h and minus 1 h, and follow the event when it moves")
class ReminderWorkflowTest {

    private static final String REMINDER = "reminder-1";
    private static final String HOLDER = "user-1";
    private static final String TICKET = "ticket-1";
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeReminders reminders;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        reminders = new FakeReminders(env);
        Worker worker = env.newWorker(TaskQueues.NOTIFY);
        worker.registerWorkflowImplementationTypes(ReminderWorkflowImpl.class);
        worker.registerActivitiesImplementations(reminders);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R5 · the 24-hour reminder fires at start minus 24 h, the 1-hour one at minus 1 h, then the row is SENT")
    void bothRemindersFireOnTime() {
        long start = env.currentTimeMillis() + Duration.ofHours(48).toMillis();
        ReminderWorkflow reminder = schedule(start);

        env.sleep(Duration.ofHours(24).plusMinutes(1));
        assertThat(reminders.dispatched).containsExactly(Offset.T_MINUS_24H);
        assertThat(reminders.dispatchedAt.get(0)).isBetween(start - Duration.ofHours(24).toMillis(),
                start - Duration.ofHours(24).toMillis() + MINUTE);

        awaitClosed(reminder);
        assertThat(reminders.dispatched).containsExactly(Offset.T_MINUS_24H, Offset.T_MINUS_1H);
        assertThat(reminders.dispatchedAt.get(1)).isBetween(start - Duration.ofHours(1).toMillis(),
                start - Duration.ofHours(1).toMillis() + MINUTE);
        assertThat(reminders.status).isEqualTo(ReminderStatus.SENT);
    }

    @Test
    @DisplayName("R5 · an event moved later moves both reminders with it")
    void aRescheduleForwardMovesTheTimers() {
        long start = env.currentTimeMillis() + Duration.ofHours(48).toMillis();
        ReminderWorkflow reminder = schedule(start);
        env.sleep(Duration.ofHours(1));

        long moved = start + Duration.ofHours(24).toMillis();
        reminder.reschedule(new Reschedule(REMINDER, HOLDER, TICKET, moved));
        env.sleep(Duration.ofHours(24));
        assertThat(reminders.dispatched).as("the old 24-hour moment passed without a send").isEmpty();

        awaitClosed(reminder);
        assertThat(reminders.dispatched).containsExactly(Offset.T_MINUS_24H, Offset.T_MINUS_1H);
        assertThat(reminders.dispatchedAt.get(0)).isGreaterThanOrEqualTo(moved - Duration.ofHours(24).toMillis());
    }

    @Test
    @DisplayName("R5 · an event moved earlier drops the reminder whose moment has passed, and sends the other on time")
    void aRescheduleBackwardDropsThePast() {
        long start = env.currentTimeMillis() + Duration.ofHours(48).toMillis();
        ReminderWorkflow reminder = schedule(start);
        env.sleep(Duration.ofHours(1));

        long moved = env.currentTimeMillis() + Duration.ofHours(10).toMillis();
        reminder.reschedule(new Reschedule(REMINDER, HOLDER, TICKET, moved));
        awaitClosed(reminder);

        assertThat(reminders.dispatched).containsExactly(Offset.T_MINUS_1H);
        assertThat(reminders.dispatchedAt.get(0)).isGreaterThanOrEqualTo(moved - Duration.ofHours(1).toMillis());
    }

    @Test
    @DisplayName("R5 · a reminder created after both moments passed sends nothing late")
    void pastDueAtCreationSendsNothing() {
        ReminderWorkflow reminder = schedule(env.currentTimeMillis() + Duration.ofMinutes(30).toMillis());

        awaitClosed(reminder);

        assertThat(reminders.dispatched).isEmpty();
        assertThat(reminders.status).isEqualTo(ReminderStatus.SENT);
    }

    @Test
    @DisplayName("a cancelled reminder never fires; only its holder may cancel it")
    void cancellationStopsTheTimers() {
        ReminderWorkflow reminder = schedule(env.currentTimeMillis() + Duration.ofHours(48).toMillis());

        assertThatThrownBy(() -> reminder.cancel(new Cancel(REMINDER, "someone-else")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));
        assertThat(reminder.cancel(new Cancel(REMINDER, HOLDER)).status()).isEqualTo(ReminderStatus.CANCELLED);
        awaitClosed(reminder);

        env.sleep(Duration.ofHours(48));
        assertThat(reminders.dispatched).isEmpty();
    }

    @Test
    @DisplayName("an execution started for a reminder that does not exist closes when its window passes")
    void anAbsentReminderCloses() {
        ReminderWorkflow reminder = start();

        awaitClosed(reminder);

        assertThat(env.currentTimeMillis()).isGreaterThanOrEqualTo(ReminderRules.FIRST_COMMAND_WINDOW.toMillis());
        assertThat(reminders.dispatched).isEmpty();
    }

    @Test
    @DisplayName("PLT-015 R3 · a rescheduled reminder's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        long start = env.currentTimeMillis() + Duration.ofHours(48).toMillis();
        ReminderWorkflow reminder = schedule(start);
        env.sleep(Duration.ofHours(1));
        reminder.reschedule(new Reschedule(REMINDER, HOLDER, TICKET, start + Duration.ofHours(2).toMillis()));
        awaitClosed(reminder);

        assertReplays(WorkflowIds.reminder(REMINDER), ReminderWorkflowImpl.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private ReminderWorkflow start() {
        ReminderWorkflow starter = client.newWorkflowStub(ReminderWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.reminder(REMINDER))
                .setTaskQueue(TaskQueues.NOTIFY)
                .build());
        WorkflowClient.start(starter::run, new Start(REMINDER));
        return client.newWorkflowStub(ReminderWorkflow.class, WorkflowIds.reminder(REMINDER));
    }

    private ReminderWorkflow schedule(long eventStartsAtMillis) {
        ReminderWorkflow reminder = start();
        View view = reminder.reschedule(new Reschedule(REMINDER, HOLDER, TICKET, eventStartsAtMillis));
        assertThat(view.status()).isEqualTo(ReminderStatus.SCHEDULED);
        return reminder;
    }

    private static void awaitClosed(ReminderWorkflow reminder) {
        WorkflowStub.fromTyped(reminder).getResult(Void.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }

    static final class FakeReminders implements ReminderActivities {
        private final TestWorkflowEnvironment env;
        volatile ReminderStatus status;
        volatile String userId;
        volatile long start;
        final List<Offset> dispatched = new CopyOnWriteArrayList<>();
        final List<Long> dispatchedAt = new CopyOnWriteArrayList<>();

        FakeReminders(TestWorkflowEnvironment env) {
            this.env = env;
        }

        private View view() {
            return status == null ? new View(REMINDER, null, null, null, 0L) : new View(REMINDER, userId, TICKET, status, start);
        }

        @Override
        public synchronized View load(String reminderId) {
            return view();
        }

        @Override
        public synchronized View save(Reschedule reschedule) {
            userId = reschedule.userId();
            start = reschedule.eventStartsAtMillis();
            status = ReminderStatus.SCHEDULED;
            return view();
        }

        @Override
        public synchronized View cancel(String reminderId) {
            status = ReminderStatus.CANCELLED;
            return view();
        }

        @Override
        public synchronized View complete(String reminderId) {
            if (status == ReminderStatus.SCHEDULED) {
                status = ReminderStatus.SENT;
            }
            return view();
        }

        @Override
        public synchronized void dispatch(String reminderId, Offset offset) {
            dispatched.add(offset);
            dispatchedAt.add(env.currentTimeMillis());
        }
    }
}
