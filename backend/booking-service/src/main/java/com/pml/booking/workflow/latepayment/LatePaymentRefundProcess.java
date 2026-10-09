package com.pml.booking.workflow.latepayment;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Start;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * How late money reaches its refund workflow.
 *
 * <p>The start is idempotent: a second start for the same reservation reaches the open execution, and
 * a finished one refuses a restart ({@code REJECT_DUPLICATE}) instead of running the refund again.
 */
@Service
public class LatePaymentRefundProcess {

    private final TemporalGateway temporal;

    public LatePaymentRefundProcess(TemporalGateway temporal) {
        this.temporal = temporal;
    }

    /** Starts the refund of this reservation's late payment, or finds it already started. */
    public Mono<Void> start(String reservationId) {
        return temporal.run(() -> {
            LatePaymentRefundWorkflow workflow = temporal.newWorkflow(LatePaymentRefundWorkflow.class,
                    WorkflowIds.lateRefund(reservationId), TaskQueues.CHECKOUT,
                    WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE,
                ProcessSearchAttributes.of("LatePaymentRefund", reservationId).build());
            try {
                WorkflowClient.start(workflow::run, new Start(reservationId));
            } catch (io.temporal.client.WorkflowExecutionAlreadyStarted alreadyRefunded) {
                // A closed execution holds this id: the refund ran, and running it again is the one thing to avoid.
            }
        });
    }

    /** The provider called back about the refund; the workflow checks now instead of at its next poll. */
    public Mono<Void> providerCallback(String reservationId) {
        return temporal.run(() -> temporal.existingWorkflow(LatePaymentRefundWorkflow.class,
                        WorkflowIds.lateRefund(reservationId)).providerCallback())
                .onErrorResume(WorkflowNotFoundException.class, settled -> Mono.empty());
    }
}
