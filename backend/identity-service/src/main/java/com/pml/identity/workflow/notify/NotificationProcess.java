package com.pml.identity.workflow.notify;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * How anything in identity asks for a message.
 *
 * <p>Starts {@code notify/{deduplicationKey}}. A start that finds the key already used — running, or
 * closed within retention — is a duplicate, dropped, and completes quietly. A service calls
 * {@link #request} after its transaction has committed and treats a failure as the message's problem,
 * never the business step's.
 */
@Slf4j
@Service
public class NotificationProcess {

    private final TemporalGateway temporal;

    public NotificationProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    /** Off the event loop; a failure to start is logged and swallowed, so the caller's operation stands. */
    public Mono<Void> request(Request request) {
        return temporal.run(() -> startNow(request))
                .onErrorResume(error -> {
                    log.warn("Notification {} could not be requested: {}", request.deduplicationKey(), error.getMessage());
                    return Mono.empty();
                });
    }

    /** The blocking start, for an activity thread. */
    public void startNow(Request request) {
        start(request);
    }

    /**
     * The blocking start, answering whether this call began the message; false when the key had
     * already been used and the request was dropped.
     */
    public boolean start(Request request) {
        NotificationWorkflow workflow = temporal.newWorkflow(NotificationWorkflow.class,
                WorkflowIds.notification(request.deduplicationKey()), TaskQueues.NOTIFY,
                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE,
                ProcessSearchAttributes.of("Notification", request.deduplicationKey()).build());
        try {
            WorkflowClient.start(workflow::run, request);
            return true;
        } catch (WorkflowExecutionAlreadyStarted duplicate) {
            log.debug("Notification {} was already requested", request.deduplicationKey());
            return false;
        }
    }
}
