package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.domain.model.Event;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.service.EventService;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Cancel;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Command;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Reschedule;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Start;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.workflow.Functions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Function;

/**
 * How the organizer's lifecycle mutations reach an event's workflow.
 *
 * <p>Temporal first, MongoDB second: every command is an Update-with-Start under
 * {@code USE_EXISTING}, and the write happens in the workflow's activity, so there is never a
 * published event with no execution holding its completion timer. The answer is read back from
 * {@code catalog_events}.
 */
@Service
public class EventLifecycleProcess {

    private final TemporalGateway temporal;
    private final EventService events;

    public EventLifecycleProcess(TemporalGateway temporal, EventService events) {
        this.temporal = temporal;
        this.events = events;
    }

    public Mono<Event> publish(String eventId, String actorId) {
        return command(eventId, workflow -> workflow::publish, new Command(eventId, actorId));
    }

    public Mono<Event> unpublish(String eventId, String actorId) {
        return command(eventId, workflow -> workflow::unpublish, new Command(eventId, actorId));
    }

    public Mono<Event> reschedule(String eventId, String actorId, Instant newStartsAt, String reason) {
        if (newStartsAt == null) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a reschedule names the new start"));
        }
        return command(eventId, workflow -> workflow::reschedule,
                new Reschedule(eventId, actorId, newStartsAt.toEpochMilli(), reason));
    }

    public Mono<Event> cancel(String eventId, String actorId, String reason) {
        return command(eventId, workflow -> workflow::cancel, new Cancel(eventId, actorId, reason));
    }

    private <A> Mono<Event> command(String eventId,
                                    Function<EventLifecycleWorkflow, Functions.Func1<A, View>> update,
                                    A argument) {
        return temporal.call(() -> {
                    EventLifecycleWorkflow workflow = temporal.newWorkflow(EventLifecycleWorkflow.class,
                            WorkflowIds.eventLifecycle(eventId), TaskQueues.LIFECYCLE,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("EventLifecycle", eventId).eventId(eventId).build());
                    return WorkflowClient.startUpdateWithStart(update.apply(workflow), argument,
                                    UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                    new WithStartWorkflowOperation<>(workflow::run, new Start(eventId, false)))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.EVENT_STATE_INVALID))
                .flatMap(view -> events.findById(view.eventId()));
    }
}
