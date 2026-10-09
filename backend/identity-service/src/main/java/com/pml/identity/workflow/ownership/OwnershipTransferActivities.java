package com.pml.identity.workflow.ownership;

import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.Nomination;
import com.pml.identity.workflow.ownership.OwnershipTransferWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * Every MongoDB write a transfer makes; each safe to run twice.
 */
@ActivityInterface(namePrefix = "OwnershipTransfer")
public interface OwnershipTransferActivities {

    View load(String transferId);

    /** Validates the nominee and writes the {@code PENDING} transfer under the given id. */
    View open(Nomination nomination);

    /** Claims the transfer and moves both roles and the organization's owner, in one transaction. */
    View complete(String transferId);

    View decline(String transferId, String actorId);

    View cancel(String transferId, String actorId);

    /** {@code PENDING} to {@code EXPIRED}; a transfer already resolved is returned unchanged. */
    View expire(String transferId);

    /** Both memberships are marked for the group-mirror repair. */
    void markMirrorPending(String transferId);
}
