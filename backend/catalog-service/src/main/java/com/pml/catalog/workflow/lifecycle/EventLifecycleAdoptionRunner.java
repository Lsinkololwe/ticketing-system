package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.catalog.infrastructure.temporal.WorkflowIds;
import com.pml.catalog.service.EventService;
import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.Start;
import com.pml.shared.constants.EventStatus;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Every PUBLISHED event has a workflow holding its completion timer.
 *
 * <p>At boot, each PUBLISHED event is started in adopt mode under {@code USE_EXISTING}: one that
 * already has its execution is untouched, and one published before the lifecycle ran on workflows
 * gets an execution that completes it at its end exactly as a new one would — at once, when that end
 * has already passed. Every pod runs this; the conflict policy makes the concurrent starts one.
 */
@Slf4j
@Component
public class EventLifecycleAdoptionRunner implements ApplicationRunner {

    private static final Duration BUDGET = Duration.ofMinutes(2);

    private final EventService events;
    private final TemporalGateway temporal;

    public EventLifecycleAdoptionRunner(EventService events, TemporalGateway temporal) {
        this.events = events;
        this.temporal = temporal;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adopted = events.findEventsByStatus(EventStatus.PUBLISHED)
                    .concatMap(event -> temporal.call(() -> {
                                EventLifecycleWorkflow workflow = temporal.newWorkflow(EventLifecycleWorkflow.class,
                                        WorkflowIds.eventLifecycle(event.getId()), TaskQueues.LIFECYCLE,
                                        WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("EventLifecycle", event.getId()).eventId(event.getId()).build());
                                return WorkflowClient.start(workflow::run, new Start(event.getId(), true));
                            })
                            .onErrorResume(error -> {
                                log.warn("Event {} could not be adopted: {}", event.getId(), error.getMessage());
                                return Mono.empty();
                            }))
                    .count()
                    .block(BUDGET);
            log.info("Lifecycle adoption: {} PUBLISHED event(s) have a workflow", adopted);
        } catch (RuntimeException error) {
            log.error("Lifecycle adoption did not complete; PUBLISHED events without a workflow are not completed "
                    + "until the next boot: {}", error.getMessage());
        }
    }
}
