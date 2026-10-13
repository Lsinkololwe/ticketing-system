package com.pml.shared.security.tenancy;

import io.temporal.activity.ActivityInterface;

/** The one step of a delivery: send the row, and record what identity answered. */
@ActivityInterface(namePrefix = "PlatformWideAuditDelivery")
public interface PlatformWideAuditDeliveryActivities {

    /**
     * Sends the row to identity. Returns once identity confirmed it or refused it for good, or when
     * there is nothing left to send; fails (and is retried) while identity cannot be reached.
     */
    void forward(String recordId);
}
