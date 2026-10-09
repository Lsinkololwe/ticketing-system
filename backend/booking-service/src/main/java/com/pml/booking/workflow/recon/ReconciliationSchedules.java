package com.pml.booking.workflow.recon;

import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.temporal.client.WorkflowClient;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes sure every reconciliation schedule exists and runs on the cadence in {@link ReconciliationRules}.
 *
 * <p>Each pod runs this at boot. A schedule that already exists takes its cadence, action and overlap
 * policy from the code; its paused state and notes are kept, so an operator's pause survives a deploy.
 */
@Slf4j
@Component
public class ReconciliationSchedules implements ApplicationRunner {

    private final TemporalGateway temporal;
    private final boolean enabled;

    public ReconciliationSchedules(TemporalGateway temporal,
                                   @Value("${reconciliation.scheduler.enabled:true}") boolean enabled) {
        this.temporal = temporal;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Reconciliation schedules are disabled by configuration");
            return;
        }
        try {
            WorkflowClient client = temporal.client();
            ScheduleClient schedules = ScheduleClient.newInstance(client.getWorkflowServiceStubs(),
                    ScheduleClientOptions.newBuilder().setNamespace(client.getOptions().getNamespace()).build());
            for (ReconciliationRules.Definition definition : ReconciliationRules.SCHEDULES) {
                try {
                    schedules.createSchedule(definition.scheduleId(), ReconciliationRules.schedule(definition),
                            ScheduleOptions.newBuilder().build());
                    log.info("Created schedule {} ({})", definition.scheduleId(), definition.cron());
                } catch (ScheduleAlreadyRunningException exists) {
                    Schedule desired = ReconciliationRules.schedule(definition);
                    schedules.getHandle(definition.scheduleId()).update(input -> new ScheduleUpdate(
                            Schedule.newBuilder(input.getDescription().getSchedule())
                                    .setAction(desired.getAction())
                                    .setSpec(desired.getSpec())
                                    .setPolicy(desired.getPolicy())
                                    .build()));
                    log.info("Updated schedule {} ({})", definition.scheduleId(), definition.cron());
                }
            }
        } catch (RuntimeException unavailable) {
            log.error("Reconciliation schedules could not be ensured; they are retried at the next boot: {}",
                    unavailable.getMessage());
        }
    }
}
