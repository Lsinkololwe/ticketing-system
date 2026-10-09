package com.pml.identity.workflow.notify;

import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * WhatsApp, then SMS, each tried three times with backoff, then failed with a reason.
 *
 * <p>The record is written first, so a message that never delivers is still a {@code FAILED} row
 * rather than an absence. A channel's retries run inside its activity's retry policy; exhausting them
 * advances to the next channel, and a missing destination advances at once.
 */
@WorkflowImpl(taskQueues = TaskQueues.NOTIFY)
public class NotificationWorkflowImpl implements NotificationWorkflow {

    static final String HONOUR_PREFERENCES = "honour-notification-preferences";

    private final NotificationActivities records =
            Workflow.newActivityStub(NotificationActivities.class, NotificationRules.recordOptions());
    private final NotificationActivities sender =
            Workflow.newActivityStub(NotificationActivities.class, NotificationRules.sendOptions());

    private String notificationId;
    private NotificationChannel deliveredVia;
    private final List<Attempt> attempts = new ArrayList<>();
    private boolean failed;

    @Override
    public void run(Request request) {
        notificationId = records.record(request);
        // Version 1 asks the recipient's preferences first. Executions started before it replay
        // without the call, on the whole chain, exactly as they ran.
        List<NotificationChannel> chain = Workflow.getVersion(HONOUR_PREFERENCES, Workflow.DEFAULT_VERSION, 1)
                == Workflow.DEFAULT_VERSION
                ? NotificationRules.LEGACY_CHAIN
                : records.channelsFor(notificationId);
        if (chain.isEmpty()) {
            return;
        }
        for (NotificationChannel channel : chain) {
            try {
                sender.send(notificationId, channel);
            } catch (ActivityFailure failure) {
                attempts.add(new Attempt(channel, "FAILED", Refusals.typeOf(failure, "UNKNOWN")));
                continue;
            }
            attempts.add(new Attempt(channel, "DELIVERED", null));
            deliveredVia = channel;
            records.markDelivered(notificationId, channel);
            return;
        }
        failed = true;
        records.markFailed(notificationId, NotificationRules.exhausted(attempts));
    }

    @Override
    public Delivery delivery() {
        return new Delivery(notificationId, deliveredVia, List.copyOf(attempts), failed);
    }
}
