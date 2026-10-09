package com.pml.identity.workflow.ownership;

import com.pml.identity.domain.enums.TransferStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One ownership transfer, from nomination to confirmation, refusal or expiry.
 *
 * <p>Addressed as {@code ownership/{transferId}} with conflict policy {@code USE_EXISTING}. The
 * expiry is a durable timer from the stored {@code expiresAt}, so a transfer nobody answers
 * expires at three days whether or not any pod is running then.
 */
@WorkflowInterface
public interface OwnershipTransferWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** The owner nominates; the first command of every execution. */
    @UpdateMethod
    View open(Nomination nomination);

    @UpdateValidatorMethod(updateName = "open")
    void validateOpen(Nomination nomination);

    /** The nominee confirms: both roles and the organization's owner move in one transaction. */
    @UpdateMethod
    View accept(Response response);

    @UpdateValidatorMethod(updateName = "accept")
    void validateAccept(Response response);

    @UpdateMethod
    View decline(Response response);

    @UpdateValidatorMethod(updateName = "decline")
    void validateDecline(Response response);

    @UpdateMethod
    View cancel(Response response);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(Response response);

    @QueryMethod
    View current();

    record Start(String transferId) {
    }

    record Nomination(String transferId,
                      String organizationId,
                      String currentOwnerId,
                      String newOwnerId,
                      String reason) {
    }

    record Response(String transferId, String actorId) {
    }

    /** {@code status} is null when no such transfer exists. */
    record View(String transferId,
                String organizationId,
                String currentOwnerId,
                String newOwnerId,
                TransferStatus status,
                long expiresAtMillis) {
    }
}
