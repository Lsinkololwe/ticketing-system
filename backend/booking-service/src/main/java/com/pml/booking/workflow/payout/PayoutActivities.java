package com.pml.booking.workflow.payout;

import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * Every write a payout makes to MongoDB.
 *
 * <p>Each method is one transaction and is safe to run twice: a retried activity finds the request
 * already in the state it was moving to and returns it unchanged. Refusals are non-retryable
 * failures whose type is the {@code ErrorCode} name.
 */
@ActivityInterface(namePrefix = "Payout")
public interface PayoutActivities {

    View createRequest(Submit command);

    View approve(Decision decision);

    View reject(Decision decision);

    View cancel(Decision decision);

    /** Places an operator hold on the request. */
    View hold(Decision decision);

    /** Lifts the hold. */
    View release(Decision decision);

    /** Debits the escrow and moves the request to PROCESSING, together, before any transfer. */
    View beginSettlement(String payoutRequestId, String providerPayoutId);

    /** Records the disbursement, moves to COMPLETED and stages {@code booking.PayoutCompleted}, together. */
    View completeSettlement(String payoutRequestId, String reference);

    /** Restores the escrow exactly with a reversing entry and moves to FAILED, together. */
    View failSettlement(String payoutRequestId, String failureCode, String reason);

    /** Surfaces a transfer with no verified answer to the recovery queue; changes no money. */
    void markUnconfirmed(String payoutRequestId);

    /** Bad account details: the account must be re-verified before it receives money again. */
    void flagBankAccount(String bankAccountId, String failureCode);
}
