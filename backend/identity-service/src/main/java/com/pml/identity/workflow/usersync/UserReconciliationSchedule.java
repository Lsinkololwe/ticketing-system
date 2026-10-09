package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.usersync.UserBackfillWorkflow.Start;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;

import java.time.Duration;
import java.util.List;

/**
 * The nightly Keycloak reconciliation: a Temporal Schedule that starts the user backfill.
 *
 * <p>Overlap policy {@code SKIP} is the lock: a backfill still paging when the next night comes makes
 * that night's fire a no-op. Each run starts with no backfill id and takes one from its own workflow
 * clock, so one night's changes are never dropped as repeats of the night before's.
 */
public final class UserReconciliationSchedule {

    public static final String SCHEDULE_ID = "identity-user-reconciliation";

    /** Daily at 03:30 UTC. */
    public static final String CRON = "30 3 * * *";

    /** A backfill that has not finished its last page in this long is abandoned; the next night starts afresh. */
    public static final Duration EXECUTION_TIMEOUT = Duration.ofHours(6);

    private UserReconciliationSchedule() {
    }

    public static Schedule schedule() {
        return Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(UserBackfillWorkflow.class)
                        .setArguments(new Start(null, 0, 0))
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId(WorkflowIds.userBackfill())
                                .setTaskQueue(TaskQueues.ONBOARDING)
                                .setWorkflowExecutionTimeout(EXECUTION_TIMEOUT)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder()
                        .setCronExpressions(List.of(CRON))
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build())
                .build();
    }
}
