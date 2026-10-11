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
 * WHY Ticket.event PROVIDES NOTHING:
 * ---------------------------------
 *
 * The event's name and date are the event's current ones. The ticket carries no copy, and a value
 * provided from one would be served by the router in place of catalog's, so a renamed or
 * rescheduled event would still show its old name and date. The schema declares no @provides on
 * {@code event}, and the router always asks catalog.
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
     * Resolves: Ticket.event: Event!
     *
     * Returns an Event reference ({@code __typename} and {@code id}) that the router resolves in
     * catalog. Nothing is provided from the ticket: the event's name and date are its current ones,
     * and a value cached here would be served in place of catalog's after a rename or reschedule.
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

        // Nothing is provided: a title or date cached on the ticket would be served by the router in place
        // of catalog's, and show the old name after a rename or the old date after a reschedule.

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
     * Because we use @provides, we include cached buyer data from the Ticket, but only for a
     * caller entitled to it (see TicketHolderContactAccess); otherwise the router asks identity.
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
    public reactor.core.publisher.Mono<Map<String, Object>> getTicketBuyer(DgsDataFetchingEnvironment dfe) {
        Ticket ticket = dfe.getSource();

        log.debug("Federation: Resolving Ticket.buyer for ticketId={}, buyerId={}",
                ticket.getId(), ticket.getBuyerId());

        // The cached contact is offered to the router only to a caller entitled to it. Providing it
        // unconditionally would answer fullName/email/phoneNumber without identity's own gate ever
        // running; withheld, the router asks identity, which decides.
        return com.pml.booking.security.TicketHolderContactAccess.mayRead(ticket).map(permitted -> {
            Map<String, Object> userRepresentation = new HashMap<>();
            userRepresentation.put("__typename", "User");
            userRepresentation.put("id", ticket.getBuyerId());
            if (permitted) {
                if (ticket.getBuyerName() != null) {
                    userRepresentation.put("fullName", ticket.getBuyerName());
                }
                if (ticket.getBuyerEmail() != null) {
                    userRepresentation.put("email", ticket.getBuyerEmail());
                }
                if (ticket.getBuyerPhone() != null) {
                    userRepresentation.put("phoneNumber", ticket.getBuyerPhone());
                }
            }
            return userRepresentation;
        });
    }
}
