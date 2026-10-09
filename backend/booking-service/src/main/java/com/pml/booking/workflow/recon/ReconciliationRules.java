package com.pml.booking.workflow.recon;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Job;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Type;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;

/**
 * When each reconciliation runs, and the budget it runs under.
 */
public final class ReconciliationRules {

    /** A run past this is abandoned; the next scheduled run tries again. */
    public static final Duration RUN_BUDGET = Duration.ofMinutes(30);

    /**
     * The internal checks and the alerts on what they find run every hour, staggered so
     * no two start together; the summary is weekly, Monday 05:00 UTC.
     */
    public static final List<Definition> SCHEDULES = List.of(
            new Definition("booking-recon-escrow", Type.ESCROW, "5 * * * *"),
            new Definition("booking-recon-escrow-journal", Type.ESCROW_JOURNAL, "20 * * * *"),
            new Definition("booking-recon-alerts", Type.ALERTS, "35 * * * *"),
            new Definition("booking-recon-weekly-summary", Type.WEEKLY_SUMMARY, "0 5 * * 1"));

    public record Definition(String scheduleId, Type type, String cron) {
    }

    private ReconciliationRules() {
    }

    public static Schedule schedule(Definition definition) {
        return Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(ReconciliationWorkflow.class)
                        .setArguments(new Job(definition.type()))
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId(WorkflowIds.reconciliation(definition.type().name(), "scheduled"))
                                .setTaskQueue(TaskQueues.RECON)
                                .setWorkflowRunTimeout(RUN_BUDGET)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder()
                        .setCronExpressions(List.of(definition.cron()))
                        .setTimeZoneName("UTC")
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build())
                .build();
    }

    static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.RECON)
                .setStartToCloseTimeout(Duration.ofMinutes(20))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(10))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }
}
