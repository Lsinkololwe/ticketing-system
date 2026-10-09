package com.pml.identity.workflow.repair;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R4 · the repair Schedule: PT15M, overlap SKIP, the workflow type and id the registry names")
class AccountRepairScheduleTest {

    @Test
    @DisplayName("the Schedule starts AccountRepairWorkflow on identity-account every interval and skips an overlapping run")
    void shape() {
        Schedule schedule = AccountRepairSchedule.schedule(Duration.ofMinutes(15));

        ScheduleActionStartWorkflow action = (ScheduleActionStartWorkflow) schedule.getAction();
        assertThat(action.getWorkflowType()).isEqualTo("AccountRepairWorkflow");
        assertThat(action.getOptions().getTaskQueue()).isEqualTo(TaskQueues.ACCOUNT);
        assertThat(action.getOptions().getWorkflowId()).isEqualTo(WorkflowIds.accountRepair()).isEqualTo("account-repair/scheduled");
        assertThat(schedule.getSpec().getIntervals()).extracting(ScheduleIntervalSpec::getEvery).containsExactly(Duration.ofMinutes(15));
        assertThat(schedule.getPolicy().getOverlap()).isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
        assertThat(AccountRepairSchedule.SCHEDULE_ID).isEqualTo("identity-account-repair");
        assertThat(AccountRepairSchedule.RUN_TIMEOUT).as("a run ends before the next is due").isLessThan(Duration.ofMinutes(15));
    }
}
