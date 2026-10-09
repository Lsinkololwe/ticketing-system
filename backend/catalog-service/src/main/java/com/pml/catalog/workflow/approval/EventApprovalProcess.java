package com.pml.catalog.workflow.approval;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.catalog.service.EventService;
import com.pml.shared.workflow.Refusals;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Claim;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Start;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Submission;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * How the organizer's submission and the reviewer's claims and decisions reach an
 * event's review workflow.
 *
 * <p>A submission is an Update-with-Start, so the first write happens in the workflow's activity and
 * no event waits in PENDING_APPROVAL without a clock running on it. Claims and decisions address the
 * open execution; one that has none is refused as {@code EVENT_STATE_INVALID}. Answers are read back
 * from {@code catalog_events} and {@code catalog_approval_timelines}.
 */
@Service
public class EventApprovalProcess {

    private final TemporalGateway temporal;
    private final EventService events;
    private final ApprovalTimelineService timelines;

    public EventApprovalProcess(TemporalGateway temporal, EventService events, ApprovalTimelineService timelines) {
        this.temporal = temporal;
        this.events = events;
        this.timelines = timelines;
    }

    public Mono<Event> submit(String eventId, String actorId) {
        return temporal.call(() -> {
                    EventApprovalWorkflow workflow = temporal.newWorkflow(EventApprovalWorkflow.class,
                            WorkflowIds.eventApproval(eventId), TaskQueues.LIFECYCLE,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("EventApproval", eventId).eventId(eventId).build());
                    return WorkflowClient.startUpdateWithStart(workflow::submit, new Submission(eventId, actorId),
                                    UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                    new WithStartWorkflowOperation<>(workflow::run, new Start(eventId, false)))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.EVENT_STATE_INVALID))
                .flatMap(view -> events.findById(view.eventId()));
    }

    public Mono<Event> approve(String eventId, String reviewerId, String reason) {
        return update(eventId, workflow -> workflow.approve(new Decision(eventId, reviewerId, reason)))
                .flatMap(view -> events.findById(eventId));
    }

    public Mono<Event> reject(String eventId, String reviewerId, String reason) {
        return update(eventId, workflow -> workflow.reject(new Decision(eventId, reviewerId, reason)))
                .flatMap(view -> events.findById(eventId));
    }

    public Mono<Event> requestChanges(String eventId, String reviewerId, String reason) {
        return update(eventId, workflow -> workflow.requestChanges(new Decision(eventId, reviewerId, reason)))
                .flatMap(view -> events.findById(eventId));
    }

    public Mono<ApprovalTimeline> claim(String eventId, String reviewerId, String actorId) {
        return update(eventId, workflow -> workflow.claim(new Claim(eventId, reviewerId, actorId)))
                .flatMap(view -> timelines.findByEventId(eventId));
    }

    public Mono<ApprovalTimeline> release(String eventId, String actorId) {
        return update(eventId, workflow -> workflow.release(new Claim(eventId, null, actorId)))
                .flatMap(view -> timelines.findByEventId(eventId));
    }

    private Mono<View> update(String eventId, Function<EventApprovalWorkflow, View> update) {
        return temporal.call(() -> update.apply(temporal.existingWorkflow(EventApprovalWorkflow.class,
                        WorkflowIds.eventApproval(eventId))))
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.EVENT_STATE_INVALID));
    }
}
