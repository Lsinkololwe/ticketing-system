package com.pml.identity.workflow.usersync;

import com.pml.shared.testing.TemporalDevServer;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleSpec;
import io.temporal.client.schedules.ScheduleUpdate;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boot runner against a real Temporal server: a nightly Schedule left on
 * an older time is moved to the code's 03:30 UTC, and an operator's pause survives the move.
 *
 * <p>No worker polls here, so a fire starts nothing that runs; the assertions read the server's own next
 * action times, which is the cadence as the server understood it.
 */
@Tag("L2")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-002-R4 · the nightly reconciliation Schedule takes the code's time at boot and keeps an operator's pause")
class UserReconciliationScheduleRunnerTest {

    private static WorkflowServiceStubs service;
    private static ScheduleClient schedules;

    @BeforeAll
    static void connect() {
        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(TemporalDevServer.target())
                .build());
        eventually(Duration.ofSeconds(60), () -> assertThat(service.blockingStub().describeNamespace(
                DescribeNamespaceRequest.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build())).isNotNull());
        schedules = ScheduleClient.newInstance(service, ScheduleClientOptions.newBuilder()
                .setNamespace(TemporalDevServer.NAMESPACE)
                .build());
    }

    @AfterAll
    static void disconnect() {
        service.shutdownNow();
    }

    @Test
    @DisplayName("D-34 · a Schedule firing at 01:00 fires at 03:30 after a boot, and a pause set before the boot is kept")
    void anExistingScheduleTakesTheCodesTimeAndKeepsItsPause() {
        ScheduleHandle handle = onCron("0 1 * * *");
        eventually(Duration.ofSeconds(30), () -> assertThat(nextActionTimes()).isNotEmpty()
                .allSatisfy(time -> assertThat(time.atZone(ZoneOffset.UTC).getHour()).isEqualTo(1)));
        handle.pause("operator hold during an incident");

        new UserReconciliationScheduleRunner(schedules).run(null);

        assertThat(handle.describe().getSchedule().getState().isPaused()).as("the operator's pause is kept").isTrue();
        handle.unpause("incident closed");
        eventually(Duration.ofSeconds(30), () -> assertThat(nextActionTimes()).isNotEmpty().allSatisfy(time -> {
            assertThat(time.atZone(ZoneOffset.UTC).getHour()).isEqualTo(3);
            assertThat(time.atZone(ZoneOffset.UTC).getMinute()).isEqualTo(30);
        }));
    }

    /** Puts the Schedule on {@code cron}, creating it or overwriting whatever an earlier run left. */
    private static ScheduleHandle onCron(String cron) {
        ScheduleSpec older = ScheduleSpec.newBuilder().setCronExpressions(List.of(cron)).setTimeZoneName("UTC").build();
        try {
            return schedules.createSchedule(UserReconciliationSchedule.SCHEDULE_ID,
                    Schedule.newBuilder(UserReconciliationSchedule.schedule()).setSpec(older).build(),
                    ScheduleOptions.newBuilder().build());
        } catch (ScheduleAlreadyRunningException exists) {
            ScheduleHandle handle = schedules.getHandle(UserReconciliationSchedule.SCHEDULE_ID);
            handle.unpause();
            handle.update(input -> new ScheduleUpdate(Schedule.newBuilder(input.getDescription().getSchedule())
                    .setSpec(older)
                    .build()));
            return handle;
        }
    }

    private static List<Instant> nextActionTimes() {
        return schedules.getHandle(UserReconciliationSchedule.SCHEDULE_ID).describe().getInfo().getNextActionTimes();
    }

    private static void eventually(Duration limit, Runnable assertion) {
        Instant deadline = Instant.now().plus(limit);
        while (true) {
            try {
                assertion.run();
                return;
            } catch (AssertionError | RuntimeException notYet) {
                if (Instant.now().isAfter(deadline)) {
                    throw notYet;
                }
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
