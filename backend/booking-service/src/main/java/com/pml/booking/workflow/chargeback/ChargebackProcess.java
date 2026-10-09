package com.pml.booking.workflow.chargeback;

import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.web.graphql.dto.DisputeChargebackInput;
import com.pml.booking.web.graphql.dto.ReceiveChargebackInput;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Decision;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Dispute;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Outcome;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Receive;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Start;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * How GraphQL reaches a chargeback's workflow.
 */
@Service
public class ChargebackProcess {

    private final TemporalGateway temporal;
    private final ChargebackService chargebacks;

    public ChargebackProcess(TemporalGateway temporal, ChargebackService chargebacks) {
        this.temporal = temporal;
        this.chargebacks = chargebacks;
    }

    public Mono<ChargebackRecord> receive(ReceiveChargebackInput input) {
        Receive command = new Receive(input.chargebackId(), input.originalTransactionId(), input.ticketId(), input.eventId(),
                input.organizerId(), input.organizationId(), input.customerId(), input.originalAmount(),
                input.chargebackAmount(), input.chargebackFee(), input.currency(), input.reason().name(),
                input.responseDeadline().toEpochMilli());
        return temporal.call(() -> {
                    ChargebackWorkflow workflow = temporal.newWorkflow(ChargebackWorkflow.class,
                            WorkflowIds.chargeback(input.chargebackId()), TaskQueues.FINANCE,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("Chargeback", input.chargebackId()).eventId(input.eventId()).organizationId(input.organizationId()).build());
                    return WorkflowClient.startUpdateWithStart(workflow::receive, command,
                                    UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                    new WithStartWorkflowOperation<>(workflow::run, new Start(input.chargebackId())))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.RESOURCE_CONFLICT))
                .flatMap(view -> chargebacks.findById(view.recordId()));
    }

    public Mono<ChargebackRecord> startReview(String recordId, String actorId, String notes) {
        return decide(recordId, workflow -> workflow.startReview(new Decision(actorId, notes)));
    }

    public Mono<ChargebackRecord> accept(String recordId, String actorId, String reason) {
        return decide(recordId, workflow -> workflow.accept(new Decision(actorId, reason)));
    }

    public Mono<ChargebackRecord> dispute(String recordId, String actorId, DisputeChargebackInput input) {
        return decide(recordId, workflow -> workflow.dispute(new Dispute(actorId, input.notes(), input.ticketValidationProof(),
                input.customerCommunicationLog(), input.deliveryConfirmation(), input.termsAcceptanceProof(),
                input.additionalDocuments())));
    }

    public Mono<ChargebackRecord> recordOutcome(String recordId, String actorId, boolean won, String notes) {
        return decide(recordId, workflow -> workflow.recordOutcome(new Outcome(actorId, won, notes)));
    }

    private Mono<ChargebackRecord> decide(String recordId, Function<ChargebackWorkflow, View> update) {
        return chargebacks.findById(recordId)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.RESOURCE_CONFLICT, "no chargeback record " + recordId)))
                .flatMap(record -> temporal.call(() -> update.apply(temporal.existingWorkflow(ChargebackWorkflow.class,
                                WorkflowIds.chargeback(record.getChargebackId()))))
                        .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.RESOURCE_CONFLICT)))
                .flatMap(view -> chargebacks.findById(view.recordId()));
    }
}
