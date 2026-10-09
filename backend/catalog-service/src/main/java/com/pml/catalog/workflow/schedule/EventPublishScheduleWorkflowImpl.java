package com.pml.catalog.workflow.schedule;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * The wait, and the signals that move or end it.
 *
 * <pre>
 *   run ── wait until publishAt ──▶ publishDue ──▶ closes
 *            │ reschedule(newTime) ─▶ the wait restarts
 *            └ cancel ──────────────▶ closes
 * </pre>
 */
@WorkflowImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventPublishScheduleWorkflowImpl implements EventPublishScheduleWorkflow {

    private final EventPublishScheduleActivities activities = Workflow.newActivityStub(
            EventPublishScheduleActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.LIFECYCLE)
                    .setStartToCloseTimeout(Duration.ofSeconds(60))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setMaximumInterval(Duration.ofMinutes(2))
                            .build())
                    .build());

    private long publishAtMillis;
    private boolean moved;
    private boolean cancelled;

    @Override
    public void run(Start start) {
        if (publishAtMillis == 0L) {
            publishAtMillis = start.publishAtMillis();
        }
        while (!cancelled) {
            moved = false;
            Duration wait = Duration.ofMillis(Math.max(0L, publishAtMillis - Workflow.currentTimeMillis()));
            boolean woken = Workflow.await(wait, () -> moved || cancelled);
            if (cancelled) {
                return;
            }
            if (woken) {
                continue;
            }
            activities.publishDue(start.eventId(), publishAtMillis);
            return;
        }
    }

    @Override
    public void reschedule(long newPublishAtMillis) {
        publishAtMillis = newPublishAtMillis;
        moved = true;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
