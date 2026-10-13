package com.pml.booking.workflow.audit;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.security.tenancy.PlatformWideAuditDeliveryWorkflow;
import com.pml.shared.security.tenancy.PlatformWideAuditDeliveryWorkflowImpl;
import io.temporal.spring.boot.WorkflowImpl;

/**
 * Carries one platform-wide reach from this service's outbox to identity's audit trail, on this
 * service's own audit queue so an identity outage cannot queue ahead of any other work.
 */
@WorkflowImpl(taskQueues = TaskQueues.AUDIT)
public class BookingAuditDeliveryWorkflowImpl extends PlatformWideAuditDeliveryWorkflowImpl
        implements PlatformWideAuditDeliveryWorkflow {
}
