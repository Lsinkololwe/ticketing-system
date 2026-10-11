package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PayoutRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * The event a money record belongs to, as a reference catalog resolves.
 *
 * <p>An escrow account and a payout record keep the event's name as it was when the money moved: they
 * are documents of what was true then. The {@code event} reference beside it is the event as it is now,
 * so a screen can show both and staff can still find a record by the event's new name.
 */
@DgsComponent
public class EventReferenceFields {

    @DgsData(parentType = "EventEscrowAccount", field = "event")
    public Map<String, Object> ofEscrowAccount(DgsDataFetchingEnvironment dfe) {
        return reference(((EventEscrowAccount) dfe.getSource()).getEventId());
    }

    @DgsData(parentType = "PayoutRequest", field = "event")
    public Map<String, Object> ofPayoutRequest(DgsDataFetchingEnvironment dfe) {
        return reference(((PayoutRequest) dfe.getSource()).getEventId());
    }

    /** {@code {__typename, id}} for an event id, or null when there is none. */
    public static Map<String, Object> reference(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        Map<String, Object> reference = new HashMap<>();
        reference.put("__typename", "Event");
        reference.put("id", eventId);
        return reference;
    }
}
