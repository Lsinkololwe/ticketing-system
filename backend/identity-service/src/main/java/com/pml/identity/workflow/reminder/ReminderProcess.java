package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.model.EventReminder;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.service.EventReminderService;
import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Cancel;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Start;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * How the reminder mutations reach a reminder's workflow.
 *
 * <p>The row is written by the workflow's activity, never here, so a reminder always has the process
 * that fires it. Answers are read back from {@code identity_event_reminders}.
 */
@Service
public class ReminderProcess {

    private final TemporalGateway temporal;
    private final EventReminderService reminders;

    public ReminderProcess(TemporalGateway temporal, EventReminderService reminders) {
        this.temporal = temporal;
        this.reminders = reminders;
    }

    /**
     * One reminder per holder and ticket; setting it again moves it.
     *
     * <p>The input carries the event's real start, so the workflow's two fixed offsets fire
     * against it rather than against the moment this call was made.
     */
    public Mono<EventReminder> set(String userId, SetEventReminderInput input) {
        return reminders.findByUserIdAndTicketId(userId, input.ticketId())
                .map(EventReminder::getId)
                .switchIfEmpty(Mono.fromSupplier(() -> new ObjectId().toHexString()))
                .flatMap(reminderId -> {
                    Reschedule reschedule = new Reschedule(reminderId, userId, input.ticketId(),
                            input.eventStartsAt().toEpochMilli());
                    return command(reminderId, workflow -> WorkflowClient.startUpdateWithStart(workflow::reschedule,
                            reschedule, completed(), new WithStartWorkflowOperation<>(workflow::run, new Start(reminderId))));
                })
                .flatMap(view -> reminders.findById(view.reminderId()));
    }

    public Mono<Boolean> cancel(String reminderId, String actorId) {
        return command(reminderId, workflow -> WorkflowClient.startUpdateWithStart(workflow::cancel,
                        new Cancel(reminderId, actorId), completed(),
                        new WithStartWorkflowOperation<>(workflow::run, new Start(reminderId))))
                .thenReturn(Boolean.TRUE);
    }

    private Mono<View> command(String reminderId, Function<ReminderWorkflow, WorkflowUpdateHandle<View>> update) {
        return temporal.call(() -> update.apply(temporal.newWorkflow(ReminderWorkflow.class,
                                WorkflowIds.reminder(reminderId), TaskQueues.NOTIFY,
                                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                                ProcessSearchAttributes.of("Reminder", reminderId).build()))
                        .getResult())
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.COMMAND_NOT_WELL_FORMED));
    }

    private static UpdateOptions<View> completed() {
        return UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build();
    }
}
