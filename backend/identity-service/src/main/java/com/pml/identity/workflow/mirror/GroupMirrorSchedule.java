package com.pml.identity.workflow.mirror;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.List;

/**
 * The Temporal Schedule that repairs the Keycloak group mirror.
 *
 * <p>Every interval, the Schedule starts one repair run. Overlap policy {@code SKIP} is the lock:
 * a pass still running when the next is due makes the next one a no-op, so two passes never work the
 * same backlog, on one pod or many.
 */
public final class GroupMirrorSchedule {

    public static final String SCHEDULE_ID = "identity-group-mirror-repair";

    /** The workflow type the Schedule starts; served by {@link GroupMirrorRepairWorkflowImpl}. */
    public static final String WORKFLOW_TYPE = "GroupMirrorRepair";

    /** {@code identity.mirror.sweep-interval}. */
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60);

    /** A pass that has not finished in this long is abandoned; the next interval starts afresh. */
    public static final Duration RUN_TIMEOUT = Duration.ofMinutes(5);

    private GroupMirrorSchedule() {
    }

    public static Schedule schedule(Duration interval) {
        return Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(WORKFLOW_TYPE)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId(WorkflowIds.groupMirrorRepair())
                                .setTaskQueue(TaskQueues.ONBOARDING)
                                .setWorkflowRunTimeout(RUN_TIMEOUT)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder()
                        .setIntervals(List.of(new ScheduleIntervalSpec(interval)))
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build())
                .build();
    }

    /** One pass is one bounded batch; a failed pass is retried briefly and otherwise left to the next interval. */
    static ActivityOptions repairOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofMinutes(4))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(5))
                        .build())
                .build();
    }
}
