package com.pml.identity.workflow.notify;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

/**
 * The start runs on an activity thread, so a workflow never holds a client itself.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.NOTIFY)
public class NotifyActivitiesImpl implements NotifyActivities {

    private final NotificationProcess notifications;

    public NotifyActivitiesImpl(NotificationProcess notifications) {
        this.notifications = notifications;
    }

    @Override
    public void request(Request request) {
        notifications.startNow(request);
    }
}
