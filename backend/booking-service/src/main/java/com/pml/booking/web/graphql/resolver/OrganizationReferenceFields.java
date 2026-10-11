package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * The organization a money record belongs to, as a reference identity resolves.
 *
 * <p>Booking keeps the organization's id and nothing else about it: a name copied onto an escrow
 * account or a payout goes stale on the first rename and is a second place to correct. A client asks
 * for {@code organization { name }} and the router resolves it in identity, where access to the
 * organization's private fields is decided.
 */
@DgsComponent
public class OrganizationReferenceFields {

    @DgsData(parentType = "EventEscrowAccount", field = "organization")
    public Map<String, Object> ofEscrowAccount(DgsDataFetchingEnvironment dfe) {
        return reference(((EventEscrowAccount) dfe.getSource()).getOrganizationId());
    }

    @DgsData(parentType = "PayoutRequest", field = "organization")
    public Map<String, Object> ofPayoutRequest(DgsDataFetchingEnvironment dfe) {
        return reference(((PayoutRequest) dfe.getSource()).getOrganizationId());
    }

    /** {@code {__typename, id}} for an organization id, or null when there is none. */
    public static Map<String, Object> reference(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return null;
        }
        Map<String, Object> reference = new HashMap<>();
        reference.put("__typename", "Organization");
        reference.put("id", organizationId);
        return reference;
    }
}
