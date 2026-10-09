package com.pml.booking.workflow.recon;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.ReconciliationService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Result;
import com.pml.booking.workflow.recon.ReconciliationWorkflow.Type;
import com.pml.shared.constants.PlatformTime;
import com.pml.booking.domain.enums.ReconciliationType;
import io.temporal.spring.boot.ActivityImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

/**
 * The scheduled reconciliation runs, on {@code booking-recon}'s two activity slots.
 */
@Slf4j
@Component
@ActivityImpl(taskQueues = TaskQueues.RECON)
public class ReconciliationActivitiesImpl implements ReconciliationWorkflow.Activities {

    private static final Duration AWAIT = Duration.ofMinutes(19);

    private final ReconciliationService reconciliation;
    private final Clock clock;
    private final String systemUser;

    public ReconciliationActivitiesImpl(ReconciliationService reconciliation, Clock clock,
                                        @Value("${reconciliation.scheduler.system-user:SYSTEM_SCHEDULER}") String systemUser) {
        this.reconciliation = reconciliation;
        this.clock = clock;
        this.systemUser = systemUser;
    }

    @Override
    public Result escrow() {
        return await(reconciliation.startEscrowReconciliation(today(), systemUser)
                .map(run -> result(Type.ESCROW, run.getId(), run.getUnmatchedCount())));
    }

    @Override
    public Result escrowJournal() {
        return await(reconciliation.startEscrowJournalReconciliation(today(), systemUser)
                .map(run -> result(Type.ESCROW_JOURNAL, run.getId(), run.getUnmatchedCount())));
    }

    @Override
    public Result alerts() {
        return await(reconciliation.sendReconciliationAlerts()
                .map(sent -> result(Type.ALERTS, null, sent)));
    }

    @Override
    public Result weeklySummary() {
        LocalDate end = today().minusDays(1);
        LocalDate start = end.minusDays(6);
        return await(Flux.fromArray(ReconciliationType.values())
                .concatMap(type -> reconciliation.generateReport(type, start, end)
                        .doOnNext(report -> log.info("Weekly {} reconciliation {}..{}: {} runs, {} items, {} unresolved, variance K{}",
                                type, start, end, report.runCount(), report.itemCount(), report.unresolvedItems().size(),
                                report.variance()))
                        .map(report -> (long) report.unresolvedItems().size()))
                .reduce(0L, Long::sum)
                .map(unresolved -> result(Type.WEEKLY_SUMMARY, null, unresolved)));
    }

    private LocalDate today() {
        return PlatformTime.dateAt(clock.instant());
    }

    private static Result result(Type type, String runId, Number unresolved) {
        return new Result(type, runId, unresolved == null ? 0L : unresolved.longValue());
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
