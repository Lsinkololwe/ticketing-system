package com.pml.identity.workflow.contactchange;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.ChangeCommand;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.Outcome;
import com.pml.identity.workflow.contactchange.ContactChangeWorkflow.Status;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.workflow.Refusals;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * How the contact service reaches {@link ContactChangeWorkflow}: starts it under {@code contact-change/{accountId}},
 * sends it the confirmations, reads its state, and waits a little for its result.
 *
 * <p>A start while a change is open fails with {@code CONTACT_CHANGE_IN_PROGRESS}; the closed one's id is reusable.</p>
 */
@Service
public class ContactChangeProcess {

    private final TemporalGateway temporal;

    public ContactChangeProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    public Mono<Void> start(ChangeCommand command) {
        return temporal.run(() -> {
                    ContactChangeWorkflow workflow = temporal.newWorkflow(ContactChangeWorkflow.class,
                            WorkflowIds.contactChange(command.accountId()), TaskQueues.ACCOUNT,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL,
                            WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE,
                            ProcessSearchAttributes.of("ContactChange", command.accountId()).build());
                    WorkflowClient.start(workflow::run, command);
                })
                .onErrorMap(WorkflowExecutionAlreadyStarted.class,
                        error -> new TranslatedRefusal(ErrorCode.CONTACT_CHANGE_IN_PROGRESS, "a contact change is already open"));
    }

    /**
     * Waits (briefly) until the change that was just started has set its marker and is waiting for its codes, so a
     * caller that returns to the person can say truthfully "a change is open". If the change ended instead (a refusal
     * at the very first step), its failure is raised here.
     */
    public Mono<Void> awaitOpen(String accountId, Duration limit) {
        return temporal.call(() -> {
                    ContactChangeWorkflow stub = temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId));
                    long end = System.nanoTime() + limit.toNanos();
                    Status status = null;
                    while (System.nanoTime() < end) {
                        status = stub.status();
                        if (status != null && !"STARTING".equals(status.phase())) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    if (status != null && "DONE".equals(status.phase())) {
                        // it ended at its first step: raise why
                        WorkflowStub.fromTyped(stub).getResult(5, TimeUnit.SECONDS, Outcome.class);
                    }
                    return Boolean.TRUE;
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_UNKNOWN))
                .then();
    }

    /** The result of the account's latest change, or empty when it is still running after {@code wait}. */
    public Mono<Optional<Outcome>> await(String accountId, Duration wait) {
        return temporal.call(() -> {
                    WorkflowStub stub = WorkflowStub.fromTyped(
                            temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)));
                    try {
                        return Optional.of(stub.getResult(wait.toMillis(), TimeUnit.MILLISECONDS, Outcome.class));
                    } catch (TimeoutException stillRunning) {
                        return Optional.<Outcome>empty();
                    }
                })
                .onErrorMap(WorkflowFailedException.class, error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_UNKNOWN))
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_UNKNOWN));
    }

    /** The open change's state, empty when none is open (never started, or finished). */
    public Mono<Optional<Status>> status(String accountId) {
        return temporal.call(() -> {
            try {
                Status status = temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)).status();
                return Optional.ofNullable(status).filter(s -> !"DONE".equals(s.phase()));
            } catch (WorkflowNotFoundException none) {
                return Optional.<Status>empty();
            }
        });
    }

    public Mono<Void> authorise(String accountId) {
        return update(() -> temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)).authorise());
    }

    public Mono<Void> acceptNew(String accountId, String proofId) {
        return update(() -> temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)).acceptNew(proofId));
    }

    public Mono<Void> failedAttempt(String accountId) {
        return temporal.run(() -> temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)).failedAttempt())
                .onErrorResume(error -> Mono.empty());
    }

    public Mono<Void> codeResent(String accountId, String target, String challengeId) {
        return update(() -> temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId))
                .codeResent(target, challengeId));
    }

    public Mono<Void> cancel(String accountId) {
        return temporal.run(() -> temporal.existingWorkflow(ContactChangeWorkflow.class, WorkflowIds.contactChange(accountId)).cancel())
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_UNKNOWN));
    }

    private Mono<Void> update(Runnable call) {
        return temporal.run(call).onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_UNKNOWN));
    }
}
