package com.pml.catalog.workflow.schedule;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.workflow.schedule.EventPublishScheduleWorkflow.Start;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.temporal.client.BatchRequest;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * How the organizer's schedule reaches the wait. Starting and moving it are one call: a
 * signal-with-start, so the first schedule opens the wait and a later one moves it.
 */
@Service
public class EventPublishScheduleProcess {

    private final TemporalGateway temporal;

    public EventPublishScheduleProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    /** Opens the wait for {@code publishAt}, or moves the one already open. */
    public Mono<Void> schedule(String eventId, Instant publishAt) {
        return temporal.run(() -> {
            EventPublishScheduleWorkflow workflow = temporal.signalWithStartWorkflow(
                    EventPublishScheduleWorkflow.class, WorkflowIds.eventPublishSchedule(eventId), TaskQueues.LIFECYCLE,
                    ProcessSearchAttributes.of("EventPublishSchedule", eventId).eventId(eventId).build());
            BatchRequest request = temporal.client().newSignalWithStartRequest();
            request.add(workflow::run, new Start(eventId, publishAt.toEpochMilli()));
            request.add(workflow::reschedule, publishAt.toEpochMilli());
            temporal.client().signalWithStart(request);
        });
    }

    /** Moves the open wait to {@code publishAt}; nothing happens when no wait is open. */
    public Mono<Void> move(String eventId, Instant publishAt) {
        return temporal.run(() -> {
            try {
                temporal.existingWorkflow(EventPublishScheduleWorkflow.class, WorkflowIds.eventPublishSchedule(eventId))
                        .reschedule(publishAt.toEpochMilli());
            } catch (WorkflowNotFoundException closed) {
                // The wait already ended; the event's own flags decide what applies.
            }
        });
    }

    /** Ends the wait; nothing happens when no wait is open. */
    public Mono<Void> cancel(String eventId) {
        return temporal.run(() -> {
            try {
                temporal.existingWorkflow(EventPublishScheduleWorkflow.class, WorkflowIds.eventPublishSchedule(eventId))
                        .cancel();
            } catch (WorkflowNotFoundException closed) {
                // Nothing to stop.
            }
        });
    }
}
