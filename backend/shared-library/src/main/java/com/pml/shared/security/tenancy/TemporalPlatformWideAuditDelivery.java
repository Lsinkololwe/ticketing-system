package com.pml.shared.security.tenancy;

import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;

/**
 * Starts {@code audit-delivery/{recordId}} on the service's audit queue.
 *
 * <p>{@code USE_EXISTING} makes a second start (the start-up re-drive meeting a delivery that is
 * already running) join the first. {@code ALLOW_DUPLICATE} lets a row that is still {@code PENDING}
 * be started again after an execution was closed from outside; the activity itself does nothing
 * for a row that is already settled, so a repeat can never deliver twice.
 */
public class TemporalPlatformWideAuditDelivery implements PlatformWideAuditDelivery {

    private final TemporalGateway temporal;
    private final String taskQueue;

    public TemporalPlatformWideAuditDelivery(TemporalGateway temporal, String taskQueue) {
        this.temporal = temporal;
        this.taskQueue = taskQueue;
    }

    @Override
    public void start(String recordId) {
        PlatformWideAuditDeliveryWorkflow workflow = temporal.newWorkflow(PlatformWideAuditDeliveryWorkflow.class,
                PlatformWideAuditDeliveryRules.workflowId(recordId), taskQueue,
                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE);
        WorkflowClient.start(workflow::deliver, recordId);
    }
}
