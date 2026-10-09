package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-002-R4 · the nightly user-reconciliation Schedule: its cadence, its lock, its run")
class UserReconciliationScheduleTest {

    @Test
    @DisplayName("R4 · overlap policy SKIP is the lock: a backfill still paging makes the next fire a no-op")
    void overlapIsSkip() {
        Schedule schedule = UserReconciliationSchedule.schedule();
        assertThat(schedule.getPolicy().getOverlap()).isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
    }

    @Test
    @DisplayName("R4 · the Schedule fires nightly at 03:30 UTC")
    void nightly() {
        Schedule schedule = UserReconciliationSchedule.schedule();
        assertThat(schedule.getSpec().getCronExpressions()).containsExactly("30 3 * * *");
    }

    @Test
    @DisplayName("each run is the backfill workflow on identity-onboarding, bounded by its execution timeout")
    void action() {
        ScheduleActionStartWorkflow action = (ScheduleActionStartWorkflow) UserReconciliationSchedule.schedule().getAction();
        assertThat(action.getWorkflowType()).isEqualTo("UserBackfillWorkflow");
        assertThat(action.getOptions().getTaskQueue()).isEqualTo(TaskQueues.ONBOARDING);
        assertThat(action.getOptions().getWorkflowId()).isEqualTo(WorkflowIds.userBackfill());
        assertThat(action.getOptions().getWorkflowExecutionTimeout()).isEqualTo(UserReconciliationSchedule.EXECUTION_TIMEOUT);
    }
}
