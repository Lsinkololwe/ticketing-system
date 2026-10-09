package com.pml.identity.workflow.mirror;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002-R8 · the group-mirror repair Schedule: its interval, its lock, its run")
class GroupMirrorScheduleTest {

    @Test
    @DisplayName("R8 · overlap policy SKIP is the lock: a slow pass makes the next a no-op")
    void overlapIsSkip() {
        Schedule schedule = GroupMirrorSchedule.schedule(GroupMirrorSchedule.DEFAULT_INTERVAL);
        assertThat(schedule.getPolicy().getOverlap()).isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
    }

    @Test
    @DisplayName("the Schedule runs at the configured interval")
    void interval() {
        Schedule schedule = GroupMirrorSchedule.schedule(Duration.ofSeconds(90));
        assertThat(schedule.getSpec().getIntervals()).hasSize(1);
        assertThat(schedule.getSpec().getIntervals().get(0).getEvery()).isEqualTo(Duration.ofSeconds(90));
        assertThat(GroupMirrorSchedule.DEFAULT_INTERVAL).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("each run is the repair workflow type, on identity-onboarding, bounded by its run timeout")
    void action() {
        ScheduleActionStartWorkflow action = (ScheduleActionStartWorkflow)
                GroupMirrorSchedule.schedule(GroupMirrorSchedule.DEFAULT_INTERVAL).getAction();
        assertThat(action.getWorkflowType()).isEqualTo(GroupMirrorSchedule.WORKFLOW_TYPE);
        assertThat(action.getOptions().getTaskQueue()).isEqualTo(TaskQueues.ONBOARDING);
        assertThat(action.getOptions().getWorkflowId()).isEqualTo(WorkflowIds.groupMirrorRepair());
        assertThat(action.getOptions().getWorkflowRunTimeout()).isEqualTo(GroupMirrorSchedule.RUN_TIMEOUT);
    }
}
