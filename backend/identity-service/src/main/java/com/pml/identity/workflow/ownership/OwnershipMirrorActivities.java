package com.pml.identity.workflow.ownership;

import io.temporal.activity.ActivityInterface;

/**
 * The Keycloak group mirror of a completed transfer.
 *
 * <p>Leaving and joining a group are both idempotent at Keycloak, so a retried attempt converges.
 */
@ActivityInterface(namePrefix = "OwnershipMirror")
public interface OwnershipMirrorActivities {

    /** The previous owner moves from {@code owners} to {@code admins}; the nominee from {@code admins} to {@code owners}. */
    void mirror(String transferId);
}
