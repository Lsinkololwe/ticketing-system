package com.pml.booking.workflow.refund;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.service.RefundService;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.refund.RefundWorkflow.Decision;
import com.pml.booking.workflow.refund.RefundWorkflow.Evidence;
import com.pml.booking.workflow.refund.RefundWorkflow.Start;
import com.pml.booking.workflow.refund.RefundWorkflow.Submit;
import com.pml.booking.workflow.refund.RefundWorkflow.View;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * How GraphQL and the refund webhook reach a ticket's refund workflow.
 */
@Service
public class RefundProcess {

    private final TemporalGateway temporal;
    private final RefundService refunds;
    private final RefundRequestRepository requests;

    public RefundProcess(TemporalGateway temporal, RefundService refunds, RefundRequestRepository requests) {
        this.temporal = temporal;
        this.refunds = refunds;
        this.requests = requests;
    }

    public Mono<RefundRequest> request(String ticketId, String reason, String requesterId) {
        return submit(new Submit(Submit.Kind.BUYER, ticketId, reason, requesterId, null, false));
    }

    public Mono<RefundRequest> requestAsAdmin(String ticketId, String reason, String adminId, boolean bypassApproval) {
        return requestAsAdmin(ticketId, reason, adminId, bypassApproval, null);
    }

    /** As above for a stated amount (at most what remains refundable); null refunds what remains. */
    public Mono<RefundRequest> requestAsAdmin(String ticketId, String reason, String adminId, boolean bypassApproval,
                                              java.math.BigDecimal amount) {
        return submit(new Submit(Submit.Kind.ADMIN, ticketId, reason, adminId, amount, bypassApproval));
    }

    /**
     * A refund raised by somebody entitled to decide it — an organizer who may refund for the event, or
     * platform staff — for the whole of what remains or a stated part. Approved by the person raising
     * it, so it goes straight to the provider; the amount is checked against what remains refundable
     * before anything is written.
     */
    public Mono<RefundRequest> requestAsOperator(String ticketId, String reason, String actorId, java.math.BigDecimal amount) {
        return submit(new Submit(Submit.Kind.ADMIN, ticketId, reason, actorId, amount, true));
    }

    /**
     * The requester withdrawing their own request. Only while it still waits for a decision: once it is
     * approved the refund is on its way to the provider and cannot be called back.
     */
    public Mono<RefundRequest> cancelAsRequester(String refundRequestId, String requesterId, String reason) {
        return stored(refundRequestId)
                .filter(request -> requesterId != null && requesterId.equals(request.getRequestedBy())
                        && request.getStatus() == RefundRequestStatus.PENDING)
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.REFUND_NOT_PERMITTED,
                        "only the person who asked can withdraw a refund request, and only before it is decided")))
                .flatMap(request -> cancel(refundRequestId, requesterId, reason));
    }

    public Mono<RefundRequest> approve(String refundRequestId, String actorId, String note) {
        return decide(refundRequestId, workflow -> workflow.approve(new Decision(refundRequestId, actorId, note)));
    }

    public Mono<RefundRequest> reject(String refundRequestId, String actorId, String reason) {
        return decide(refundRequestId, workflow -> workflow.reject(new Decision(refundRequestId, actorId, reason)));
    }

    public Mono<RefundRequest> cancel(String refundRequestId, String actorId, String reason) {
        return decide(refundRequestId, workflow -> workflow.cancel(new Decision(refundRequestId, actorId, reason)));
    }

    /** Processing follows approval inside the workflow; a request still PENDING is approved, anything later is answered as it stands. */
    public Mono<RefundRequest> process(String refundRequestId, String actorId) {
        return stored(refundRequestId).flatMap(request -> request.getStatus() == RefundRequestStatus.PENDING
                ? approve(refundRequestId, actorId, "approved for processing")
                : Mono.just(request));
    }

    public Mono<BulkOperationResponse> bulkApprove(List<String> refundRequestIds, String reviewerId) {
        List<String> errors = new CopyOnWriteArrayList<>();
        return Flux.fromIterable(refundRequestIds)
                .concatMap(id -> approve(id, reviewerId, "Bulk approved")
                        .map(approved -> true)
                        .onErrorResume(refused -> {
                            errors.add("Refund request " + id + " was not approved: " + refused.getMessage());
                            return Mono.just(false);
                        }))
                .collectList()
                .map(results -> {
                    int approved = (int) results.stream().filter(Boolean::booleanValue).count();
                    int failed = results.size() - approved;
                    return BulkOperationResponse.partial(
                            String.format("Bulk approve completed: %d approved, %d failed", approved, failed),
                            approved, failed, errors);
                });
    }

    /** A provider callback wakes the refund's workflow, which asks the status API what happened. */
    public Mono<Void> providerCallback(String providerRefundId, String status) {
        return requests.findByPawaPayRefundId(providerRefundId)
                .switchIfEmpty(Mono.error(new UnknownRefund(providerRefundId)))
                .flatMap(request -> temporal.run(() -> temporal.existingWorkflow(RefundWorkflow.class,
                                WorkflowIds.refund(request.getTicketId()))
                        .providerCallback(new Evidence(providerRefundId, status))))
                .onErrorResume(WorkflowNotFoundException.class, settled -> Mono.empty());
    }

    private Mono<RefundRequest> submit(Submit command) {
        return temporal.call(() -> {
                    RefundWorkflow workflow = temporal.newWorkflow(RefundWorkflow.class, WorkflowIds.refund(command.ticketId()),
                            TaskQueues.FINANCE, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("Refund", command.ticketId()).build());
                    return WorkflowClient.startUpdateWithStart(workflow::submit, command,
                                    UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                    new WithStartWorkflowOperation<>(workflow::run, new Start(command.ticketId(), null)))
                            .getResult();
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.REFUND_ALREADY_ISSUED))
                .flatMap(view -> refunds.findById(view.refundRequestId()));
    }

    private Mono<RefundRequest> decide(String refundRequestId, Function<RefundWorkflow, View> update) {
        return stored(refundRequestId)
                .flatMap(request -> temporal.call(() -> update.apply(temporal.existingWorkflow(RefundWorkflow.class,
                                WorkflowIds.refund(request.getTicketId()))))
                        .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.REFUND_NOT_PERMITTED)))
                .flatMap(view -> refunds.findById(view.refundRequestId()));
    }

    private Mono<RefundRequest> stored(String refundRequestId) {
        return refunds.findById(refundRequestId)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.REFUND_NOT_PERMITTED, "no refund request " + refundRequestId)));
    }

    /** A callback naming a refund this platform never sent. */
    public static final class UnknownRefund extends RuntimeException {
        public UnknownRefund(String providerRefundId) {
            super("no refund was sent under provider id " + providerRefundId);
        }
    }
}
