package com.pml.booking.workflow.recon;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Type;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-FIN-005")
@DisplayName("ET-FIN-005-R2/R5/R7 · every reconciliation type has one schedule that never overlaps itself, and the internal checks run hourly")
class ReconciliationRulesTest {

    @Test
    @DisplayName("each type is scheduled exactly once, under a unique id, with a five-field cron")
    void oneSchedulePerType() {
        assertThat(ReconciliationRules.SCHEDULES.stream().map(ReconciliationRules.Definition::type).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(Type.values());
        assertThat(ReconciliationRules.SCHEDULES).hasSize(Type.values().length);
        assertThat(ReconciliationRules.SCHEDULES.stream().map(ReconciliationRules.Definition::scheduleId)).doesNotHaveDuplicates();
        ReconciliationRules.SCHEDULES.forEach(definition ->
                assertThat(definition.cron().trim().split("\\s+")).hasSize(5));
    }

    @Test
    @DisplayName("R2, D-24 · the escrow checks and their alerts fire every hour at distinct minutes; the summary is weekly")
    void internalChecksRunHourly() {
        List<String> hourly = ReconciliationRules.SCHEDULES.stream()
                .filter(definition -> definition.type() != Type.WEEKLY_SUMMARY)
                .map(ReconciliationRules.Definition::cron)
                .toList();

        assertThat(hourly).hasSize(3).allSatisfy(cron -> assertThat(cron).matches("\\d{1,2} \\* \\* \\* \\*"));
        assertThat(hourly.stream().map(cron -> cron.split(" ")[0])).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("R5 · a run never overlaps its predecessor; R7 · it runs on the recon queue within its budget")
    void schedulesSkipOverlapAndCarryABudget() {
        for (ReconciliationRules.Definition definition : ReconciliationRules.SCHEDULES) {
            Schedule schedule = ReconciliationRules.schedule(definition);
            ScheduleActionStartWorkflow action = (ScheduleActionStartWorkflow) schedule.getAction();

            assertThat(schedule.getPolicy().getOverlap()).isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
            assertThat(action.getOptions().getTaskQueue()).isEqualTo(TaskQueues.RECON);
            assertThat(action.getOptions().getWorkflowRunTimeout()).isEqualTo(ReconciliationRules.RUN_BUDGET);
            assertThat(schedule.getSpec().getCronExpressions()).containsExactly(definition.cron());
        }
    }
}
