package com.pml.catalog.workflow.approval;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.service.EventService;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Start;
import com.pml.shared.constants.EventStatus;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Every undecided review has a workflow running its SLA clock.
 *
 * <p>At boot, each event in PENDING_APPROVAL or CHANGES_REQUESTED is started in adopt mode under
 * {@code USE_EXISTING}. The clock of an adopted review is reconstructed from the event's submission
 * and change-request stamps, and a level already crossed fires once, in order.
 */
@Slf4j
@Component
public class EventApprovalAdoptionRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofMinutes(2);

    private final EventService events;
    private final TemporalGateway temporal;

    public EventApprovalAdoptionRunner(EventService events, TemporalGateway temporal) {
        this.events = events;
        this.temporal = temporal;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adopted = Flux.concat(events.findEventsByStatus(EventStatus.PENDING_APPROVAL),
                            events.findEventsByStatus(EventStatus.CHANGES_REQUESTED))
                    .concatMap(event -> temporal.call(() -> {
                                EventApprovalWorkflow workflow = temporal.newWorkflow(EventApprovalWorkflow.class,
                                        WorkflowIds.eventApproval(event.getId()), TaskQueues.LIFECYCLE,
                                        WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("EventApproval", event.getId()).eventId(event.getId()).build());
                                return WorkflowClient.start(workflow::run, new Start(event.getId(), true));
                            })
                            .onErrorResume(error -> {
                                log.warn("Review of event {} could not be adopted: {}", event.getId(), error.getMessage());
                                return Mono.empty();
                            }))
                    .count()
                    .block(BUDGET);
            log.info("Approval adoption: {} undecided review(s) have a workflow", adopted);
        } catch (RuntimeException error) {
            log.error("Approval adoption did not complete; undecided reviews without a workflow do not escalate "
                    + "until the next boot: {}", error.getMessage());
        }
    }
}
