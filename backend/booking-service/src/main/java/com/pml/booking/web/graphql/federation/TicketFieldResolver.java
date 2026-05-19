package com.pml.booking.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * TICKET FIELD RESOLVER - Federation Entity References
 * ============================================================================
 *
 * This resolver handles fields on the Ticket type that reference OTHER
 * federated entities (Event and User).
 *
 * HOW ENTITY REFERENCES WORK:
 * ---------------------------
 *
 * When a Ticket has an "event" field that returns an Event, we don't actually
 * need to fetch the full Event from Catalog Service ourselves. Instead:
 *
 * 1. We return a "stub" containing just the @key fields (id)
 * 2. Apollo Router sees this stub and recognizes it as an Event reference
 * 3. If the client requested more Event fields (like title), Router calls
 *    Catalog Service's _entities query to fetch them
 * 4. Router merges our Ticket data with Catalog's Event data
 *
 * THE @provides OPTIMIZATION:
 * ---------------------------
 *
 * In the schema, we declared:
 *   event: Event! @provides(fields: "title eventDateTime organizerId")
 *
 * This tells Router: "When I return an Event stub, I ALSO include title,
 * eventDateTime, and organizerId from my cached data."
 *
 * Why? Because Ticket already stores eventTitle, eventDate for display purposes.
 * So if client only needs title, Router can use OUR cached value instead of
 * making an extra call to Catalog Service.
 *
 * We include these fields in the stub map, and Router decides whether to use
 * them or fetch fresh data from Catalog based on the query.
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TicketFieldResolver {

    /**
     * ========================================================================
     * EVENT FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Ticket.event: Event! @provides(fields: "title eventDateTime organizerId")
     *
     * Returns an Event "representation" (stub) that Apollo Router can use
     * to fetch the full Event from Catalog Service if needed.
     *
     * The representation MUST include:
     * - __typename: "Event" (tells Router what type this is)
     * - id: The event's ID (the @key field)
     *
     * Because we use @provides, we ALSO include:
     * - title: From ticket.eventTitle (cached)
     * - eventDateTime: From ticket.eventDate (cached)
     * - organizerId: From ticket (if we have it cached)
     *
     * Router will use these provided fields if:
     * 1. The client only requested those fields
     * 2. Router determines it's safe to use cached data
     *
     * Otherwise, Router calls Catalog Service for fresh data.
     *
     * @param dfe The DataFetchingEnvironment containing the parent Ticket
     * @return Map representing an Event entity reference
     */
    @DgsData(parentType = "Ticket", field = "event")
    public Map<String, Object> getTicketEvent(DgsDataFetchingEnvironment dfe) {
        Ticket ticket = dfe.getSource();

        log.debug("Federation: Resolving Ticket.event for ticketId={}, eventId={}",
                ticket.getId(), ticket.getEventId());

        // Build the Event representation (stub)
        Map<String, Object> eventRepresentation = new HashMap<>();

        // REQUIRED: These are necessary for federation to work
        eventRepresentation.put("__typename", "Event");
        eventRepresentation.put("id", ticket.getEventId());

        // PROVIDED: These are optional but optimize queries via @provides
        // If we have cached data, include it so Router doesn't need to call Catalog
        if (ticket.getEventTitle() != null) {
            eventRepresentation.put("title", ticket.getEventTitle());
        }

        if (ticket.getEventDate() != null) {
            eventRepresentation.put("eventDateTime", ticket.getEventDate());
        }

        // organizerId might be stored on ticket for analytics
        // If we have it, include it
        // Note: We might need to add this field to the Ticket model if not present

        return eventRepresentation;
    }

    /**
     * ========================================================================
     * BUYER FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Ticket.buyer: User! @provides(fields: "fullName email phoneNumber")
     *
     * Returns a User "representation" (stub) that Apollo Router can use
     * to fetch the full User from Identity Service if needed.
     *
     * Because we use @provides, we include cached buyer data from the Ticket.
     * This optimization allows queries that only need basic buyer info
     * (name, email, phone) to be resolved without calling Identity Service.
     *
     * Field Mapping:
     * - Ticket.buyerName -> User.fullName
     * - Ticket.buyerEmail -> User.email
     * - Ticket.buyerPhone -> User.phoneNumber
     *
     * @param dfe The DataFetchingEnvironment containing the parent Ticket
     * @return Map representing a User entity reference with provided fields
     */
    @DgsData(parentType = "Ticket", field = "buyer")
    public Map<String, Object> getTicketBuyer(DgsDataFetchingEnvironment dfe) {
        Ticket ticket = dfe.getSource();

        log.debug("Federation: Resolving Ticket.buyer for ticketId={}, buyerId={}",
                ticket.getId(), ticket.getBuyerId());

        // Build the User representation (stub)
        Map<String, Object> userRepresentation = new HashMap<>();

        // REQUIRED: These are necessary for federation to work
        userRepresentation.put("__typename", "User");
        userRepresentation.put("id", ticket.getBuyerId());

        // PROVIDED: These fields are declared in @provides, so Router can use
        // our cached values instead of calling Identity Service
        if (ticket.getBuyerName() != null) {
            userRepresentation.put("fullName", ticket.getBuyerName());
        }
        if (ticket.getBuyerEmail() != null) {
            userRepresentation.put("email", ticket.getBuyerEmail());
        }
        if (ticket.getBuyerPhone() != null) {
            userRepresentation.put("phoneNumber", ticket.getBuyerPhone());
        }

        return userRepresentation;
    }
}
