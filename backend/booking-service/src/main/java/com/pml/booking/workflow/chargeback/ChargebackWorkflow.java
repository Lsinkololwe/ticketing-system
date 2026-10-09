package com.pml.booking.workflow.chargeback;

import com.pml.shared.constants.ChargebackStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;

/**
 * One chargeback, from notification to recovery or reversal.
 *
 * <p>Addressed as {@code chargeback/{providerChargebackId}}: a provider that notifies twice reaches
 * the same execution. Receipt opens a dispute on the event's escrow, which is what blocks its payout
 *; resolution closes it and tells the event's finance workflow to re-check eligibility.
 */
@WorkflowInterface
public interface ChargebackWorkflow {

    @WorkflowMethod
    void run(Start start);

    @UpdateMethod
    View receive(Receive command);

    @UpdateMethod
    View startReview(Decision decision);

    @UpdateValidatorMethod(updateName = "startReview")
    void validateStartReview(Decision decision);

    @UpdateMethod
    View accept(Decision decision);

    @UpdateValidatorMethod(updateName = "accept")
    void validateAccept(Decision decision);

    @UpdateMethod
    View dispute(Dispute command);

    @UpdateValidatorMethod(updateName = "dispute")
    void validateDispute(Dispute command);

    @UpdateMethod
    View recordOutcome(Outcome outcome);

    @UpdateValidatorMethod(updateName = "recordOutcome")
    void validateRecordOutcome(Outcome outcome);

    @QueryMethod
    View current();

    record Start(String chargebackId) {
    }

    record Receive(String chargebackId, String originalTransactionId, String ticketId, String eventId,
                   String organizerId, String organizationId, String customerId, BigDecimal originalAmount,
                   BigDecimal chargebackAmount, BigDecimal chargebackFee, String currency, String reason,
                   long responseDeadlineMillis) {
    }

    record Decision(String actorId, String note) {
    }

    record Dispute(String actorId, String notes, String ticketValidationProof, String customerCommunicationLog,
                   String deliveryConfirmation, String termsAcceptanceProof, String additionalDocuments) {
    }

    record Outcome(String actorId, boolean won, String notes) {
    }

    record View(String recordId, String chargebackId, String eventId, ChargebackStatus status, long responseDeadlineMillis) {
    }
}
