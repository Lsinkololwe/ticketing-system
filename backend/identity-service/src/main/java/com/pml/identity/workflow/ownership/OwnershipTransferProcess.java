package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.service.OwnershipConfirmationCodes;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Response;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Start;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/**
 * How the ownership-transfer mutations reach a transfer's workflow.
 *
 * <p>The nominee's confirmation code is checked here, before the update, so the secret never enters
 * the workflow's history. Every answer is read back from {@code identity_ownership_transfers}.
 */
@Service
public class OwnershipTransferProcess {

    private final TemporalGateway temporal;
    private final OwnershipTransferService transfers;
    private final OwnershipConfirmationCodes confirmationCodes;

    public OwnershipTransferProcess(TemporalGateway temporal, OwnershipTransferService transfers,
                                    OwnershipConfirmationCodes confirmationCodes) {
        this.temporal = temporal;
        this.transfers = transfers;
        this.confirmationCodes = confirmationCodes;
    }

    public Mono<OwnershipTransferRequest> initiate(String organizationId, String currentOwnerId, String newOwnerId, String reason) {
        String transferId = UUID.randomUUID().toString();
        Nomination nomination = new Nomination(transferId, organizationId, currentOwnerId, newOwnerId, reason);
        return command(transferId, workflow -> WorkflowClient.startUpdateWithStart(workflow::open, nomination,
                completed(), new WithStartWorkflowOperation<>(workflow::run, new Start(transferId))));
    }

    public Mono<OwnershipTransferRequest> cancel(String organizationId, String currentOwnerId) {
        return transfers.findPendingByOrganization(organizationId)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.TRANSFER_NOT_PENDING,
                        "no pending transfer for organization " + organizationId)))
                .flatMap(transfer -> respond(transfer.getId(), currentOwnerId, Command.CANCEL));
    }

    /** Sends the nominee the code {@link #accept} asks for. */
    public Mono<Void> requestConfirmationCode(String token, String callerId) {
        return byToken(token).flatMap(transfer -> confirmationCodes.issue(transfer, callerId));
    }

    public Mono<OwnershipTransferRequest> accept(String token, String newOwnerId, String confirmationCode) {
        return byToken(token)
                .flatMap(transfer -> confirmationCodes.verify(transfer, newOwnerId, confirmationCode)
                        .then(Mono.defer(() -> respond(transfer.getId(), newOwnerId, Command.ACCEPT))));
    }

    public Mono<OwnershipTransferRequest> decline(String token, String newOwnerId) {
        return byToken(token).flatMap(transfer -> respond(transfer.getId(), newOwnerId, Command.DECLINE));
    }

    private enum Command { ACCEPT, DECLINE, CANCEL }

    private Mono<OwnershipTransferRequest> respond(String transferId, String actorId, Command kind) {
        Response response = new Response(transferId, actorId);
        return command(transferId, workflow -> {
            WithStartWorkflowOperation<Void> start = new WithStartWorkflowOperation<>(workflow::run, new Start(transferId));
            return switch (kind) {
                case ACCEPT -> WorkflowClient.startUpdateWithStart(workflow::accept, response, completed(), start);
                case DECLINE -> WorkflowClient.startUpdateWithStart(workflow::decline, response, completed(), start);
                case CANCEL -> WorkflowClient.startUpdateWithStart(workflow::cancel, response, completed(), start);
            };
        });
    }

    private Mono<OwnershipTransferRequest> byToken(String token) {
        return transfers.findByToken(token)
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("Invalid transfer token")));
    }

    private Mono<OwnershipTransferRequest> command(String transferId,
                                                   Function<OwnershipTransferWorkflow, WorkflowUpdateHandle<View>> update) {
        return temporal.call(() -> update.apply(temporal.newWorkflow(OwnershipTransferWorkflow.class,
                                WorkflowIds.ownershipTransfer(transferId), TaskQueues.ONBOARDING,
                                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                                ProcessSearchAttributes.of("OwnershipTransfer", transferId).build()))
                        .getResult())
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.TRANSFER_NOT_PENDING))
                .flatMap(view -> transfers.findById(view.transferId()));
    }

    private static UpdateOptions<View> completed() {
        return UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build();
    }
}
