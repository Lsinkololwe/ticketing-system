package com.pml.identity.workflow.repair;

import com.pml.identity.config.IdentityAccountRepairProperties;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Creates the {@code identity-account-repair} Schedule at boot behind {@code identity.account.repair.enabled}
 * (on in prod). Idempotent on every pod: a Schedule that exists takes its interval, action and overlap policy from
 * {@link AccountRepairSchedule}; its paused state is kept so an operator's pause survives a release.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "identity.account.repair.enabled", havingValue = "true")
public class AccountRepairScheduleRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofSeconds(30);

    private final ScheduleClient schedules;
    private final Duration interval;

    public AccountRepairScheduleRunner(ScheduleClient schedules, IdentityAccountRepairProperties properties) {
        this.schedules = schedules;
        this.interval = properties.getInterval();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean created = Mono.fromCallable(this::ensure).subscribeOn(Schedulers.boundedElastic()).block(BUDGET);
            log.info(Boolean.TRUE.equals(created) ? "Account repair Schedule created, every {}" : "Account repair Schedule updated to every {}", interval);
        } catch (RuntimeException error) {
            log.error("Account repair Schedule could not be ensured; drift waits for the next boot: {}", error.getMessage());
        }
    }

    public boolean ensure() {
        try {
            schedules.createSchedule(AccountRepairSchedule.SCHEDULE_ID, AccountRepairSchedule.schedule(interval), ScheduleOptions.newBuilder().build());
            return true;
        } catch (ScheduleAlreadyRunningException exists) {
            Schedule desired = AccountRepairSchedule.schedule(interval);
            schedules.getHandle(AccountRepairSchedule.SCHEDULE_ID).update(input -> new ScheduleUpdate(
                    Schedule.newBuilder(input.getDescription().getSchedule())
                            .setAction(desired.getAction()).setSpec(desired.getSpec()).setPolicy(desired.getPolicy()).build()));
            return false;
        }
    }
}
