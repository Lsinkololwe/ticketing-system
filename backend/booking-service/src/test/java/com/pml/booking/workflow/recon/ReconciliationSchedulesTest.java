package com.pml.booking.workflow.recon;

import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.workflow.recon.ReconciliationRules.Definition;
import com.pml.shared.testing.TemporalDevServer;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.ScheduleUpdate;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The boot runner against a real Temporal server: every Schedule exists on
 * the code's cadence, and one created on an older cadence is moved to it without losing an operator's pause.
 *
 * <p>No worker polls {@code booking-recon} here, so a fire starts nothing that runs; the assertions read
 * the server's own next action times, which is the cadence as the server understood it.
 */
@Tag("L2")
@Tag("ET-FIN-005")
@DisplayName("ET-FIN-005-R2 · reconciliation Schedules take the code's hourly cadence on a real server and keep an operator's pause")
class ReconciliationSchedulesTest {

    private static WorkflowServiceStubs service;
    private static WorkflowClient client;
    private static ScheduleClient schedules;

    @BeforeAll
    static void connect() {
        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(TemporalDevServer.target())
                .build());
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().until(() ->
                service.blockingStub().describeNamespace(DescribeNamespaceRequest.newBuilder()
                        .setNamespace(TemporalDevServer.NAMESPACE).build()) != null);
        client = WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder()
                .setNamespace(TemporalDevServer.NAMESPACE)
                .build());
        schedules = ScheduleClient.newInstance(service, ScheduleClientOptions.newBuilder()
                .setNamespace(TemporalDevServer.NAMESPACE)
                .build());
    }

    @AfterAll
    static void disconnect() {
        service.shutdownNow();
    }

    @Test
    @DisplayName("after a boot every reconciliation Schedule exists and fires at the minute its cron names")
    void everyScheduleRunsOnItsCadence() {
        boot();

        for (Definition definition : ReconciliationRules.SCHEDULES) {
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                    assertThat(nextActionMinutes(definition)).containsOnly(minuteOf(definition)));
        }
    }

    @Test
    @DisplayName("D-24 · a Schedule on a daily cadence becomes hourly at the next boot, and a pause set in the UI survives it")
    void anExistingScheduleTakesTheNewCadenceAndKeepsItsPause() {
        Definition escrow = ReconciliationRules.SCHEDULES.get(0);
        ScheduleHandle handle = onCadence(escrow, "0 2 * * *");
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                assertThat(nextActionMinutes(escrow)).containsOnly(0));
        handle.pause("operator hold during an incident");

        boot();

        assertThat(handle.describe().getSchedule().getState().isPaused()).as("the operator's pause is kept").isTrue();

        handle.unpause("incident closed");
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                assertThat(nextActionMinutes(escrow)).containsOnly(minuteOf(escrow)));
    }

    private static void boot() {
        new ReconciliationSchedules(new TemporalGateway(client), true).run(null);
    }

    /** Puts one Schedule on {@code cron}, creating it or overwriting whatever an earlier test left. */
    private static ScheduleHandle onCadence(Definition definition, String cron) {
        Schedule older = ReconciliationRules.schedule(new Definition(definition.scheduleId(), definition.type(), cron));
        try {
            return schedules.createSchedule(definition.scheduleId(), older, ScheduleOptions.newBuilder().build());
        } catch (ScheduleAlreadyRunningException exists) {
            ScheduleHandle handle = schedules.getHandle(definition.scheduleId());
            handle.unpause();
            handle.update(input -> new ScheduleUpdate(Schedule.newBuilder(input.getDescription().getSchedule())
                    .setSpec(older.getSpec())
                    .build()));
            return handle;
        }
    }

    private static Set<Integer> nextActionMinutes(Definition definition) {
        Set<Integer> minutes = schedules.getHandle(definition.scheduleId()).describe().getInfo().getNextActionTimes().stream()
                .map(time -> time.atZone(ZoneOffset.UTC).getMinute())
                .collect(Collectors.toSet());
        assertThat(minutes).isNotEmpty();
        return minutes;
    }

    private static int minuteOf(Definition definition) {
        return Integer.parseInt(definition.cron().trim().split("\\s+")[0]);
    }
}
