package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.repository.EventReminderRepository;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Start;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Every {@code SCHEDULED} reminder has a workflow owning its timers.
 *
 * <p>At boot each one is started under {@code USE_EXISTING}: a reminder that already has its
 * execution is untouched, and one written before reminders ran on workflows gets one that fires it
 * exactly as a new reminder would. Every pod runs this; the conflict policy makes the starts one.
 */
@Slf4j
@Component
public class ReminderAdoptionRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofSeconds(60);

    private final EventReminderRepository reminders;
    private final TemporalGateway temporal;

    public ReminderAdoptionRunner(EventReminderRepository reminders, TemporalGateway temporal) {
        this.reminders = reminders;
        this.temporal = temporal;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adopted = reminders.findByStatus(ReminderStatus.SCHEDULED)
                    .concatMap(reminder -> temporal.call(() -> {
                                ReminderWorkflow workflow = temporal.newWorkflow(ReminderWorkflow.class,
                                        WorkflowIds.reminder(reminder.getId()), TaskQueues.NOTIFY,
                                        WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("Reminder", reminder.getId()).build());
                                return WorkflowClient.start(workflow::run, new Start(reminder.getId()));
                            })
                            .onErrorResume(error -> {
                                log.warn("Reminder {} could not be adopted: {}", reminder.getId(), error.getMessage());
                                return Mono.empty();
                            }))
                    .count()
                    .block(BUDGET);
            log.info("Reminder adoption: {} scheduled reminder(s) have a workflow", adopted);
        } catch (RuntimeException error) {
            log.error("Reminder adoption did not complete; reminders without a workflow fire after the next boot: {}",
                    error.getMessage());
        }
    }
}
