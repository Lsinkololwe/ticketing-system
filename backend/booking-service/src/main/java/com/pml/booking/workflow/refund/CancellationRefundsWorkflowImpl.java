package com.pml.booking.workflow.refund;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.finance.EventFinanceRules;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch, refund each ticket in its own execution, continue as new, close the escrow.
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class CancellationRefundsWorkflowImpl implements CancellationRefundsWorkflow {

    private final Activities cancellation = Workflow.newActivityStub(Activities.class, EventFinanceRules.financeOptions());

    private Summary progress;

    @Override
    public Summary run(Start start) {
        progress = new Summary(start.eventId(), start.refundsStarted(), false);
        Batch batch = cancellation.nextBatch(start.eventId(), start.afterTicketId(), EventFinanceRules.REFUND_BATCH);

        List<Promise<Void>> refunds = new ArrayList<>();
        for (String ticketId : batch.ticketIds()) {
            RefundWorkflow refund = Workflow.newChildWorkflowStub(RefundWorkflow.class, ChildWorkflowOptions.newBuilder()
                    .setWorkflowId(WorkflowIds.refund(ticketId))
                    .setTaskQueue(TaskQueues.FINANCE)
                    .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
                    .build());
            refunds.add(Async.procedure(refund::run, new RefundWorkflow.Start(ticketId, start.reason())));
        }
        for (Promise<Void> refund : refunds) {
            try {
                refund.get();
            } catch (ChildWorkflowFailure alreadyRefunding) {
                // The ticket's own refund execution is already open, or its refund failed and that
                // execution recorded it; either way this batch moves on.
            }
        }

        int started = start.refundsStarted() + batch.ticketIds().size();
        progress = new Summary(start.eventId(), started, false);
        if (batch.ticketIds().size() == EventFinanceRules.REFUND_BATCH) {
            Workflow.continueAsNew(new Start(start.eventId(), start.reason(), batch.lastTicketId(), started));
        }

        progress = new Summary(start.eventId(), started, cancellation.closeEscrow(start.eventId()));
        return progress;
    }

    @Override
    public Summary progress() {
        return progress;
    }
}
