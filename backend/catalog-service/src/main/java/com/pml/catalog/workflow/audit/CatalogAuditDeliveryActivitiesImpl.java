package com.pml.catalog.workflow.audit;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.security.tenancy.PlatformWideAuditDeliveryActivities;
import com.pml.shared.security.tenancy.PlatformWideAuditForwarder;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sends a row of this service's outbox to identity. The sending logic is shared; the wait for
 * identity's answer happens here, on the activity's own thread, which is where waiting is allowed.
 * A failure is thrown so that the workflow's retry policy asks again.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.AUDIT)
public class CatalogAuditDeliveryActivitiesImpl implements PlatformWideAuditDeliveryActivities {

    private final PlatformWideAuditForwarder forwarder;

    public CatalogAuditDeliveryActivitiesImpl(PlatformWideAuditForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @Override
    public void forward(String recordId) {
        forwarder.forward(recordId).block(Duration.ofSeconds(25));
    }
}
