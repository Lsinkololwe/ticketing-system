package com.pml.shared.security.tenancy;

import io.temporal.workflow.Workflow;

/**
 * Sends the row and, if identity cannot be reached, keeps asking with a capped backoff until it can.
 *
 * <p>The waiting is the activity's retry policy, so the workflow itself is one call and its history
 * stays small however long identity is away. A service subclasses this and names its own task
 * queue; there is no behaviour to change.
 */
public class PlatformWideAuditDeliveryWorkflowImpl implements PlatformWideAuditDeliveryWorkflow {

    private final PlatformWideAuditDeliveryActivities activities = Workflow.newActivityStub(
            PlatformWideAuditDeliveryActivities.class, PlatformWideAuditDeliveryRules.activityOptions());

    @Override
    public void deliver(String recordId) {
        Workflow.getVersion(PlatformWideAuditDeliveryRules.STEPS_VERSION, Workflow.DEFAULT_VERSION, 1);
        activities.forward(recordId);
    }
}
