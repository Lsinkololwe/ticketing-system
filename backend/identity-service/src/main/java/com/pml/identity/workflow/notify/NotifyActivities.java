package com.pml.identity.workflow.notify;

import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.activity.ActivityInterface;

/**
 * A workflow asking for a message: starts {@code NotificationWorkflow} on {@code identity-notify}.
 */
@ActivityInterface(namePrefix = "Notify")
public interface NotifyActivities {

    void request(Request request);
}
