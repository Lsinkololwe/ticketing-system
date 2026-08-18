package com.pml.identity.service.referencedata;

import com.pml.identity.domain.model.Organization;
import com.pml.shared.referencedata.StatusSemanticResolver;
import com.pml.shared.referencedata.StatusSemanticStamper;
import org.reactivestreams.Publisher;
import org.springframework.data.mongodb.core.mapping.event.ReactiveBeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps an organization's status meaning on every write.
 *
 * <p>The approval workflow is the place a stale semantic does the most visible
 * damage: the admin review queue is "organizations whose status means PENDING".
 * An organization approved yesterday but still carrying the semantic it had while
 * pending sits in that queue forever, and an administrator who approves it again
 * changes nothing — the queue is built from a field nobody is updating.
 *
 * @see StatusSemanticStamper
 */
@Component
public class OrganizationStatusSemanticStamper extends StatusSemanticStamper<Organization>
        implements ReactiveBeforeConvertCallback<Organization> {

    public OrganizationStatusSemanticStamper(StatusSemanticResolver resolver) {
        super(resolver, "ORGANIZATION_STATUS",
                o -> o.getStatus() == null ? null : o.getStatus().name(),
                Organization::setStatusSemantic,
                Organization::getId);
    }

    @Override
    public Publisher<Organization> onBeforeConvert(Organization entity, String collection) {
        return stamp(entity);
    }
}
