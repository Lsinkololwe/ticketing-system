package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Change;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Start;
import io.temporal.client.BatchRequest;
import io.temporal.client.WorkflowClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * A Keycloak change becomes one signal-with-start carrying its originating id.
 *
 * <p>The returned {@code Mono} completes once Temporal has recorded the signal, which is the point
 * at which the change can no longer be lost.
 */
@Service
public class UserSyncProcess {

    private final TemporalGateway temporal;

    public UserSyncProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    public Mono<Void> accept(String keycloakUserId, Change change) {
        return temporal.run(() -> {
            WorkflowClient client = temporal.client();
            UserSyncWorkflow workflow = temporal.signalWithStartWorkflow(UserSyncWorkflow.class,
                    WorkflowIds.userSync(keycloakUserId), TaskQueues.ONBOARDING,
                    ProcessSearchAttributes.of("UserSync", keycloakUserId).build());
            BatchRequest request = client.newSignalWithStartRequest();
            request.add(workflow::run, new Start(keycloakUserId, List.of(), List.of(), UserSyncRules.MAX_CHANGES_PER_RUN));
            request.add(workflow::keycloakEvent, change);
            client.signalWithStart(request);
        });
    }
}
