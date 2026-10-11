package com.pml.booking.workflow.payout;

import com.pml.booking.domain.model.PayoutRequest;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.service.PayoutRequestService;
import com.pml.booking.web.graphql.dto.CreatePayoutRequestInput;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Evidence;
import com.pml.booking.workflow.payout.PayoutWorkflow.Start;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/**
 * How GraphQL and the provider webhook reach a payout's workflow.
 *
 * <p>Temporal first, MongoDB second: a request is an Update-with-Start whose first
 * write happens in an activity, so there is never a saved request with no process driving it.
 * Every answer is read back from {@code booking_payout_requests}, the record GraphQL has always read.
 */
@Service
public class PayoutProcess {

    private final TemporalGateway temporal;
    private final PayoutRequestRepository payouts;
    private final PayoutRequestService payoutRequests;

    public PayoutProcess(TemporalGateway temporal, PayoutRequestRepository payouts, PayoutRequestService payoutRequests) {
        this.temporal = temporal;
        this.payouts = payouts;
        this.payoutRequests = payoutRequests;
    }

    public Mono<PayoutRequest> request(CreatePayoutRequestInput input, String requesterId, String organizationId) {
        if (input.escrowAccountId() == null || input.escrowAccountId().isBlank()) {
            return Mono.error(new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a payout request names its escrow account"));
        }
        Submit submit = new Submit(UUID.randomUUID().toString(), input.organizerId(), organizationId, input.eventId(),
                input.escrowAccountId(), input.bankAccountId(), input.requestedAmount(), input.currency(),
                input.payoutMethod(), input.notes(), input.metadata(), input.idempotencyKey(), requesterId);

        return temporal.call(() -> {
                    PayoutWorkflow workflow = temporal.newWorkflow(PayoutWorkflow.class,
                            WorkflowIds.payout(input.escrowAccountId()), TaskQueues.FINANCE,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL,
                ProcessSearchAttributes.of("Payout", input.escrowAccountId()).eventId(input.eventId()).build());
                    return WorkflowClient.startUpdateWithStart(workflow::submit, submit,
                            UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                            new WithStartWorkflowOperation<>(workflow::run, new Start(input.escrowAccountId())))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.PAYOUT_STATE_INVALID))
                .flatMap(view -> payoutRequests.findById(view.payoutRequestId()));
    }

    public Mono<PayoutRequest> approve(String payoutRequestId, String actorId, String note) {
        return command(payoutRequestId, workflow -> workflow.approve(new Decision(payoutRequestId, actorId, note)));
    }

    public Mono<PayoutRequest> reject(String payoutRequestId, String actorId, String reason) {
        return command(payoutRequestId, workflow -> workflow.reject(new Decision(payoutRequestId, actorId, reason)));
    }

    public Mono<PayoutRequest> cancel(String payoutRequestId, String actorId, String reason) {
        return command(payoutRequestId, workflow -> workflow.cancel(new Decision(payoutRequestId, actorId, reason)));
    }

    public Mono<PayoutRequest> hold(String payoutRequestId, String actorId, String reason) {
        return command(payoutRequestId, workflow -> workflow.hold(new Decision(payoutRequestId, actorId, reason)));
    }

    public Mono<PayoutRequest> release(String payoutRequestId, String actorId, String note) {
        return command(payoutRequestId, workflow -> workflow.release(new Decision(payoutRequestId, actorId, note)));
    }

    public Mono<PayoutRequest> retry(String payoutRequestId, String actorId) {
        return command(payoutRequestId, workflow -> workflow.retry(new Decision(payoutRequestId, actorId, null)));
    }

    public Mono<PayoutRequest> confirmTransfer(String payoutRequestId, String actorId, String bankReference) {
        return command(payoutRequestId, workflow -> workflow.confirmTransfer(new Decision(payoutRequestId, actorId, bankReference)));
    }

    public Mono<PayoutRequest> current(String payoutRequestId) {
        return known(payoutRequestId);
    }

    /** A provider callback: the payout is found by the id stored when settlement began, then signalled. */
    public Mono<Void> providerCallback(String providerPayoutId, String status) {
        return payouts.findByPawaPayPayoutId(providerPayoutId)
                .switchIfEmpty(Mono.error(new UnknownPayout(providerPayoutId)))
                .flatMap(request -> temporal.run(() -> temporal.existingWorkflow(PayoutWorkflow.class,
                                WorkflowIds.payout(request.getEscrowAccountId()))
                        .providerCallback(new Evidence(providerPayoutId, status))))
                .onErrorResume(WorkflowNotFoundException.class, settled -> Mono.empty());
    }

    private Mono<PayoutRequest> command(String payoutRequestId, Function<PayoutWorkflow, View> update) {
        return known(payoutRequestId)
                .flatMap(request -> temporal.call(() -> update.apply(temporal.existingWorkflow(PayoutWorkflow.class,
                                WorkflowIds.payout(request.getEscrowAccountId()))))
                        .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.PAYOUT_STATE_INVALID)))
                .flatMap(view -> payoutRequests.findById(view.payoutRequestId()));
    }

    private Mono<PayoutRequest> known(String payoutRequestId) {
        return payoutRequests.findById(payoutRequestId)
                .switchIfEmpty(Mono.defer(() -> payouts.findByRequestId(payoutRequestId)))
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.PAYOUT_STATE_INVALID,
                        "no payout request " + payoutRequestId)));
    }

    /** A callback naming a payout this platform never began. */
    public static final class UnknownPayout extends RuntimeException {
        public UnknownPayout(String providerPayoutId) {
            super("no payout was begun under provider id " + providerPayoutId);
        }
    }
}
