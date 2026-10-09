package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.usersync.UserBackfillWorkflow.Start;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * How an operator asks for a full Keycloak re-sync.
 *
 * <p>The returned {@code Mono} completes once Temporal has recorded the start. A backfill already
 * running is reached, not doubled; a closed one does not block a new request.
 */
@Service
public class UserBackfillProcess {

    private final TemporalGateway temporal;
    private final Clock clock;

    public UserBackfillProcess(TemporalGateway temporal, Clock clock) {
        this.temporal = temporal;
        this.clock = clock;
    }

    public Mono<Void> start() {
        return temporal.run(() -> {
            UserBackfillWorkflow workflow = temporal.newWorkflow(UserBackfillWorkflow.class,
                    WorkflowIds.userBackfill(), TaskQueues.ONBOARDING,
                    WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE,
                ProcessSearchAttributes.of("UserBackfill", "user-backfill").build());
            WorkflowClient.start(workflow::run, new Start(Long.toString(clock.millis()), 0, 0));
        });
    }
}
