package com.pml.identity.workflow.usersync;

import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Creates the nightly user-reconciliation Schedule at boot, once per namespace.
 *
 * <p>Idempotent: every pod runs this and one Schedule results. A Schedule that already exists takes its
 * cadence, action and overlap policy from {@link UserReconciliationSchedule}; its paused state and notes
 * are kept, so an operator's pause survives a release. A boot that cannot reach Temporal
 * logs and continues; the next boot tries again.
 */
@Slf4j
@Component
public class UserReconciliationScheduleRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofSeconds(30);

    private final ScheduleClient schedules;

    public UserReconciliationScheduleRunner(ScheduleClient schedules) {
        this.schedules = schedules;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean created = Mono.fromCallable(this::ensure).subscribeOn(Schedulers.boundedElastic()).block(BUDGET);
            log.info(Boolean.TRUE.equals(created)
                    ? "User reconciliation Schedule created, cron {} UTC"
                    : "User reconciliation Schedule updated to cron {} UTC", UserReconciliationSchedule.CRON);
        } catch (RuntimeException error) {
            log.error("User reconciliation Schedule could not be ensured; drift waits for the next boot: {}",
                    error.getMessage());
        }
    }

    boolean ensure() {
        try {
            schedules.createSchedule(UserReconciliationSchedule.SCHEDULE_ID, UserReconciliationSchedule.schedule(),
                    ScheduleOptions.newBuilder().build());
            return true;
        } catch (ScheduleAlreadyRunningException exists) {
            Schedule desired = UserReconciliationSchedule.schedule();
            schedules.getHandle(UserReconciliationSchedule.SCHEDULE_ID).update(input -> new ScheduleUpdate(
                    Schedule.newBuilder(input.getDescription().getSchedule())
                            .setAction(desired.getAction())
                            .setSpec(desired.getSpec())
                            .setPolicy(desired.getPolicy())
                            .build()));
            return false;
        }
    }
}
