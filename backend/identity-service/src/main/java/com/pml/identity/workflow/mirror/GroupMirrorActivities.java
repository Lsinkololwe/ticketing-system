package com.pml.identity.workflow.mirror;

import io.temporal.activity.ActivityInterface;

/**
 * One pass of the Keycloak group-mirror repair.
 */
@ActivityInterface(namePrefix = "GroupMirror")
public interface GroupMirrorActivities {

    /** Applies MongoDB's view of up to one batch of {@code mirrorPending} memberships; returns how many were repaired. */
    int repairPending();
}
