package com.pml.shared.security.tenancy;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Keeps one platform-wide reach moving to identity's audit trail until identity has it.
 *
 * <p>Addressed as {@code audit-delivery/{recordId}}: the id is the row's random identifier, so two
 * starts for one row meet one execution and the id holds nothing about who did what. Each service
 * registers the implementation on its own audit queue, so a backlog behind an identity outage
 * never queues ahead of checkout, finance or lifecycle work.
 *
 * <p>The input is the row's id only. The facts of the reach are read from the service's outbox
 * when the activity runs, so no actor or operation enters the workflow history.
 */
@WorkflowInterface
public interface PlatformWideAuditDeliveryWorkflow {

    @WorkflowMethod
    void deliver(String recordId);
}
