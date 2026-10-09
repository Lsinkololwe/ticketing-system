package com.pml.identity.workflow.repair;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;

/** The Temporal Schedule that repairs account drift. Overlap SKIP is the lock: one pass at a time, on any number of pods. */
public final class AccountRepairSchedule {

    public static final String SCHEDULE_ID = "identity-account-repair";
    public static final String WORKFLOW_TYPE = "AccountRepairWorkflow";
    public static final Duration RUN_TIMEOUT = Duration.ofMinutes(14);

    private AccountRepairSchedule() {
    }

    public static Schedule schedule(Duration interval) {
        return Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(WORKFLOW_TYPE)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId(WorkflowIds.accountRepair())
                                .setTaskQueue(TaskQueues.ACCOUNT)
                                .setWorkflowRunTimeout(RUN_TIMEOUT)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder().setIntervals(List.of(new ScheduleIntervalSpec(interval))).build())
                .setPolicy(SchedulePolicy.newBuilder().setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP).build())
                .build();
    }

    /** A pass that fails (Keycloak down) is retried briefly, then left to the next interval; nothing is left half done. */
    static ActivityOptions options() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ACCOUNT)
                .setStartToCloseTimeout(Duration.ofMinutes(4))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(5)).build())
                .build();
    }
}
