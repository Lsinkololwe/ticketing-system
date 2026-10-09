package com.pml.booking.workflow.recon;

import io.temporal.activity.ActivityInterface;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One reconciliation run, started by a Temporal Schedule.
 *
 * <p>The schedule's overlap policy SKIP is what stops two runs of one type at once, and the
 * run timeout is the budget past which a run is abandoned and the next schedule tries again.
 */
@WorkflowInterface
public interface ReconciliationWorkflow {

    @WorkflowMethod
    Result run(Job job);

    enum Type { ESCROW, ESCROW_JOURNAL, ALERTS, WEEKLY_SUMMARY }

    record Job(Type type) {
    }

    record Result(Type type, String runId, long unresolved) {
    }

    @ActivityInterface(namePrefix = "Reconciliation")
    interface Activities {

        Result escrow();

        Result escrowJournal();

        Result alerts();

        Result weeklySummary();
    }
}
