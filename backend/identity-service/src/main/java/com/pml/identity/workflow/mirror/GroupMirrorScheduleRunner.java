package com.pml.identity.workflow.mirror;

import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Creates the group-mirror repair Schedule at boot, once per namespace.
 *
 * <p>Idempotent: every pod runs this and one Schedule results. A Schedule that already exists takes its
 * interval, action and overlap policy from {@link GroupMirrorSchedule}; its paused state and notes are
 * kept, so an operator's pause survives a release. A boot that cannot reach Temporal logs
 * and continues; the next boot tries again.
 */
@Slf4j
@Component
public class GroupMirrorScheduleRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofSeconds(30);

    private final ScheduleClient schedules;
    private final Duration interval;

    public GroupMirrorScheduleRunner(ScheduleClient schedules,
                                     @Value("${identity.mirror.sweep-interval:PT60S}") Duration interval) {
        this.schedules = schedules;
        this.interval = interval;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Boolean created = Mono.fromCallable(this::ensure).subscribeOn(Schedulers.boundedElastic()).block(BUDGET);
            log.info(Boolean.TRUE.equals(created)
                    ? "Group mirror repair Schedule created, every {}"
                    : "Group mirror repair Schedule updated to every {}", interval);
        } catch (RuntimeException error) {
            log.error("Group mirror repair Schedule could not be ensured; drift waits for the next boot: {}",
                    error.getMessage());
        }
    }

    boolean ensure() {
        try {
            schedules.createSchedule(GroupMirrorSchedule.SCHEDULE_ID, GroupMirrorSchedule.schedule(interval),
                    ScheduleOptions.newBuilder().build());
            return true;
        } catch (ScheduleAlreadyRunningException exists) {
            Schedule desired = GroupMirrorSchedule.schedule(interval);
            schedules.getHandle(GroupMirrorSchedule.SCHEDULE_ID).update(input -> new ScheduleUpdate(
                    Schedule.newBuilder(input.getDescription().getSchedule())
                            .setAction(desired.getAction())
                            .setSpec(desired.getSpec())
                            .setPolicy(desired.getPolicy())
                            .build()));
            return false;
        }
    }
}
