package com.pml.identity.workflow.repair;

import io.temporal.activity.ActivityInterface;

import java.util.Map;

/** The three passes of the repair. Each is idempotent: a retry, or the next run, finds the work done. */
@ActivityInterface(namePrefix = "AccountRepair")
public interface AccountRepairActivities {

    /** D1, D3, D4, D5, D6. */
    Map<String, Long> repairAccounts();

    /** D2, D9. */
    Map<String, Long> repairKeycloakUsers();

    /** D7, D8 and the alerts. */
    Map<String, Long> repairStale();
}
