package com.pml.identity.workflow.notify;

import java.util.List;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.notify.NotificationWorkflow.Delivery;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The delivery chain, its retries and its deduplication, with time skipped.
 */
@Tag("L3")
@Tag("ET-NTF-001")
@DisplayName("ET-NTF-001-R5/R6 · WhatsApp, then email, three attempts each, then a recorded failure")
class NotificationWorkflowTest {

    private static final String KEY = "team.accepted:inv-1";
    private static final Request REQUEST = new Request(KEY, "team.accepted", "user-1", NotificationRules.TEAM_INVITATION, "inv-1");

    enum Mode { ACCEPTS, UNAVAILABLE, NO_DESTINATION }

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeNotifications notifications;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        notifications = new FakeNotifications();
        Worker worker = env.newWorker(TaskQueues.NOTIFY);
        worker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        worker.registerActivitiesImplementations(notifications);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R5 · WhatsApp accepts: delivered on the first attempt, email never tried")
    void whatsAppDelivers() {
        Delivery delivery = send();

        assertThat(delivery.deliveredVia()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isEqualTo(1);
        assertThat(notifications.attempts(NotificationChannel.EMAIL)).isZero();
        assertThat(notifications.delivered).isEqualTo(NotificationChannel.WHATSAPP);
    }

    @Test
    @DisplayName("R6 · WhatsApp unavailable is tried three times, then email delivers")
    void fallsBackToSmsAfterThreeAttempts() {
        notifications.modes.put(NotificationChannel.WHATSAPP, Mode.UNAVAILABLE);

        Delivery delivery = send();

        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isEqualTo(NotificationRules.ATTEMPTS_PER_CHANNEL);
        assertThat(notifications.attempts(NotificationChannel.EMAIL)).isEqualTo(1);
        assertThat(delivery.deliveredVia()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("R5 · a recipient with no phone skips each channel at once, and the notification rests as failed")
    void noDestinationIsNotRetried() {
        notifications.modes.put(NotificationChannel.WHATSAPP, Mode.NO_DESTINATION);
        notifications.modes.put(NotificationChannel.EMAIL, Mode.NO_DESTINATION);

        Delivery delivery = send();

        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isEqualTo(1);
        assertThat(notifications.attempts(NotificationChannel.EMAIL)).isEqualTo(1);
        assertThat(delivery.failed()).isTrue();
        assertThat(notifications.failureReason).contains("NO_DESTINATION");
    }

    @Test
    @DisplayName("R6 · every channel unavailable: three attempts each, then FAILED with each channel's reason")
    void anExhaustedChainFails() {
        notifications.modes.put(NotificationChannel.WHATSAPP, Mode.UNAVAILABLE);
        notifications.modes.put(NotificationChannel.EMAIL, Mode.UNAVAILABLE);

        Delivery delivery = send();

        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isEqualTo(3);
        assertThat(notifications.attempts(NotificationChannel.EMAIL)).isEqualTo(3);
        assertThat(delivery.failed()).isTrue();
        assertThat(delivery.deliveredVia()).isNull();
        assertThat(notifications.failureReason).contains("WHATSAPP=CHANNEL_UNAVAILABLE").contains("EMAIL=CHANNEL_UNAVAILABLE");
    }

    @Test
    @DisplayName("R7 · a second request under the same key is refused by the server and sends nothing")
    void aDuplicateRequestIsRefused() {
        send();

        assertThatThrownBy(() -> WorkflowClient.start(starter()::run, REQUEST))
                .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
        assertThat(notifications.records).isEqualTo(1);
        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isEqualTo(1);
    }

    @Test
    @DisplayName("PLT-015 R3 · a fallback delivery's history replays against the implementation")
    void theHistoryReplays() throws Exception {
        notifications.modes.put(NotificationChannel.WHATSAPP, Mode.UNAVAILABLE);
        send();

        String history = env.getWorkflowExecutionHistory(
                WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.notification(KEY)).build()).toJson(true);
        WorkflowReplayer.replayWorkflowExecution(history, NotificationWorkflowImpl.class);
    }

    @Test
    @DisplayName("a message the recipient's preferences suppress is neither sent nor recorded as failed")
    void suppressedIsNotSent() {
        notifications.allowed = List.of();

        Delivery delivery = send();

        assertThat(notifications.preferenceChecks).isEqualTo(1);
        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isZero();
        assertThat(notifications.attempts(NotificationChannel.EMAIL)).isZero();
        assertThat(delivery.failed()).isFalse();
        assertThat(notifications.failureReason).isNull();
    }

    @Test
    @DisplayName("only the channels the recipient left on are tried")
    void chainIsNarrowed() {
        notifications.allowed = List.of(NotificationChannel.EMAIL);

        Delivery delivery = send();

        assertThat(notifications.attempts(NotificationChannel.WHATSAPP)).isZero();
        assertThat(delivery.deliveredVia()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("an execution recorded before preferences were consulted still replays after the deploy")
    void aPreviousDeploysHistoryReplays() throws Exception {
        String history;
        try (TestWorkflowEnvironment before = TestWorkflowEnvironment.newInstance()) {
            Worker worker = before.newWorker(TaskQueues.NOTIFY);
            worker.registerWorkflowImplementationTypes(PreviousNotificationWorkflow.class);
            worker.registerActivitiesImplementations(new FakeNotifications());
            before.start();
            NotificationWorkflow workflow = before.getWorkflowClient().newWorkflowStub(NotificationWorkflow.class,
                    WorkflowOptions.newBuilder().setWorkflowId(WorkflowIds.notification(KEY)).setTaskQueue(TaskQueues.NOTIFY).build());
            workflow.run(REQUEST);
            history = before.getWorkflowExecutionHistory(
                    WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.notification(KEY)).build()).toJson(true);
        }

        WorkflowReplayer.replayWorkflowExecution(history, NotificationWorkflowImpl.class);
    }

    /** The implementation as it was before it consulted preferences: the whole chain, every time. */
    public static class PreviousNotificationWorkflow implements NotificationWorkflow {
        private final NotificationActivities records =
                io.temporal.workflow.Workflow.newActivityStub(NotificationActivities.class, NotificationRules.recordOptions());
        private final NotificationActivities sender =
                io.temporal.workflow.Workflow.newActivityStub(NotificationActivities.class, NotificationRules.sendOptions());
        private String notificationId;
        private NotificationChannel deliveredVia;

        @Override
        public void run(Request request) {
            notificationId = records.record(request);
            for (NotificationChannel channel : NotificationRules.CHAIN) {
                try {
                    sender.send(notificationId, channel);
                } catch (io.temporal.failure.ActivityFailure failure) {
                    continue;
                }
                deliveredVia = channel;
                records.markDelivered(notificationId, channel);
                return;
            }
            records.markFailed(notificationId, "exhausted");
        }

        @Override
        public Delivery delivery() {
            return new Delivery(notificationId, deliveredVia, List.of(), deliveredVia == null);
        }
    }

    // ---- harness -------------------------------------------------------------------------------

    private NotificationWorkflow starter() {
        return client.newWorkflowStub(NotificationWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.notification(KEY))
                .setTaskQueue(TaskQueues.NOTIFY)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .build());
    }

    private Delivery send() {
        NotificationWorkflow workflow = starter();
        WorkflowClient.start(workflow::run, REQUEST);
        NotificationWorkflow started = client.newWorkflowStub(NotificationWorkflow.class, WorkflowIds.notification(KEY));
        WorkflowStub.fromTyped(started).getResult(Void.class);
        return started.delivery();
    }

    static final class FakeNotifications implements NotificationActivities {
        final Map<NotificationChannel, Mode> modes = new EnumMap<>(NotificationChannel.class);
        final Map<NotificationChannel, Integer> attempts = new ConcurrentHashMap<>();
        volatile int records;
        volatile List<NotificationChannel> allowed = NotificationRules.CHAIN;
        volatile int preferenceChecks;
        volatile NotificationChannel delivered;
        volatile String failureReason;

        int attempts(NotificationChannel channel) {
            return attempts.getOrDefault(channel, 0);
        }

        @Override
        public synchronized String record(Request request) {
            records++;
            return NotificationRules.notificationId(request.deduplicationKey());
        }

        @Override
        public synchronized void send(String notificationId, NotificationChannel channel) {
            attempts.merge(channel, 1, Integer::sum);
            switch (modes.getOrDefault(channel, Mode.ACCEPTS)) {
                case UNAVAILABLE -> throw ApplicationFailure.newFailure("provider refused", NotificationRules.CHANNEL_UNAVAILABLE);
                case NO_DESTINATION -> throw ApplicationFailure.newNonRetryableFailure("no phone", NotificationRules.NO_DESTINATION);
                default -> {
                }
            }
        }

        @Override
        public synchronized List<NotificationChannel> channelsFor(String notificationId) {
            preferenceChecks++;
            return allowed;
        }

        @Override
        public synchronized void markDelivered(String notificationId, NotificationChannel channel) {
            delivered = channel;
        }

        @Override
        public synchronized void markFailed(String notificationId, String reason) {
            failureReason = reason;
        }
    }
}
