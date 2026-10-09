package com.pml.booking.workflow.recon;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

/**
 * A scheduled run dispatches to the one activity its type names.
 */
@WorkflowImpl(taskQueues = TaskQueues.RECON)
public class ReconciliationWorkflowImpl implements ReconciliationWorkflow {

    private final Activities reconciliation = Workflow.newActivityStub(Activities.class, ReconciliationRules.activityOptions());

    @Override
    public Result run(Job job) {
        return switch (job.type()) {
            case ESCROW -> reconciliation.escrow();
            case ESCROW_JOURNAL -> reconciliation.escrowJournal();
            case ALERTS -> reconciliation.alerts();
            case WEEKLY_SUMMARY -> reconciliation.weeklySummary();
        };
    }
}
