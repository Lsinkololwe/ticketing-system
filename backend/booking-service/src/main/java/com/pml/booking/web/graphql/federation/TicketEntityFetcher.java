package com.pml.booking.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * ============================================================================
 * TICKET ENTITY FETCHER - Federation Entity Resolution
 * ============================================================================
 *
 * This class enables Apollo Router to resolve Ticket entities.
 *
 * WHEN IS THIS CALLED?
 * --------------------
 * This fetcher is called when:
 * 1. A query requests User.purchasedTickets and then fields on those Tickets
 *    that aren't already in the list response
 * 2. Any reference to a Ticket by ID from another service (if any were to exist)
 *
 * TICKET OWNERSHIP:
 * -----------------
 * Ticket is owned by the Booking Service. This means:
 * - Booking Service is the source of truth for all Ticket data
 * - Fields like event, buyer are resolved as stubs pointing to other services
 * - ticketNumber, status, price, purchasedAt are core Ticket fields
 *
 * RELATED FIELD RESOLVERS:
 * ------------------------
 * TicketFieldResolver handles the Ticket's references to other entities:
 * - Ticket.event -> Returns Event stub for Catalog Service
 * - Ticket.buyer -> Returns User stub for Identity Service
 *
 * FEDERATED QUERY EXAMPLE:
 * ------------------------
 *
 *   query {
 *     me {
 *       firstName                <- From Identity Service
 *       purchasedTickets {       <- From UserExtensionResolver
 *         ticketNumber           <- From this service (via entity or direct)
 *         event {                <- Stub from TicketFieldResolver
 *           title                <- From Catalog Service
 *         }
 *       }
 *     }
 *   }
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TicketEntityFetcher {

    private final TicketService ticketService;

    /**
     * Resolve a Ticket entity by its key fields.
     *
     * This method is called by Apollo Router during federation query planning
     * when a Ticket entity needs to be resolved from its key.
     *
     * Note: In most cases, Tickets are fetched directly via queries
     * (myTickets, ticket(id:)) or through User.purchasedTickets which
     * returns full Ticket objects directly, not stubs.
     *
     * This fetcher is mainly for edge cases where a Ticket reference
     * needs to be resolved from just an ID.
     *
     * @param values Map containing the @key fields (id)
     * @return The resolved Ticket entity
     */
    @DgsEntityFetcher(name = "Ticket")
    public Mono<Ticket> fetchTicket(Map<String, Object> values) {
        String id = (String) values.get("id");
        log.debug("Federation: Resolving Ticket entity with id={}", id);
        return ticketService.findById(id);
    }
}
