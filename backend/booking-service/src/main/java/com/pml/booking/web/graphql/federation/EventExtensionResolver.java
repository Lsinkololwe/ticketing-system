package com.pml.booking.web.graphql.federation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.TicketService;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * ============================================================================
 * EVENT EXTENSION RESOLVER - Federation Extended Fields
 * ============================================================================
 *
 * This resolver handles fields that the Booking Service ADDS to the Event type.
 * The Event type is owned by the Catalog Service, but we extend it with
 * ticket-related fields.
 *
 * HOW FEDERATION WORKS HERE:
 * --------------------------
 * 1. Client queries: event(id: "123") { title tickets { ticketNumber } }
 * 2. Apollo Router sees that "title" belongs to Catalog, "tickets" to Booking
 * 3. Router calls Catalog Service for Event { id, title }
 * 4. Router calls Booking Service's Event resolver with { id } (the key)
 * 5. This resolver fetches tickets for that event ID
 * 6. Router merges the results
 *
 * THE @DgsData ANNOTATION:
 * -----------------------
 * - parentType = "Event": This resolver handles fields on the Event type
 * - field = "tickets": Specifically the "tickets" field
 *
 * The "Event" object we receive is a Map<String, Object> containing:
 * - The @key fields (id)
 * - Any @requires fields (totalCapacity, if specified)
 * - Any fields the Router has already fetched
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventExtensionResolver {

    private final TicketService ticketService;

    /**
     * Set of ticket statuses that count as "sold" for reporting.
     * Excludes cancelled, refunded, and failed payment tickets.
     */
    private static final Set<TicketStatus> SOLD_STATUSES = Set.of(
            TicketStatus.PURCHASED,
            TicketStatus.CONFIRMED,
            TicketStatus.VALIDATED,
            TicketStatus.USED
    );

    /**
     * ========================================================================
     * TICKETS FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Event.tickets: [Ticket!]!
     *
     * When a client queries for an event's tickets, this method is called.
     * The Event object (as a Map) contains at minimum the "id" field from @key.
     *
     * Example query:
     *   event(id: "abc") {
     *     title           <- From Catalog Service
     *     tickets {       <- From THIS resolver
     *       ticketNumber
     *       status
     *     }
     *   }
     *
     * @param dfe The DataFetchingEnvironment containing the parent Event
     * @return Flux of tickets for this event
     */
    @DgsData(parentType = "Event", field = "tickets")
    public Flux<Ticket> getEventTickets(DgsDataFetchingEnvironment dfe) {
        // The parent Event is provided as a Map (from federation resolution)
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");

        log.debug("Federation: Resolving Event.tickets for eventId={}", eventId);

        return ticketService.findByEventId(eventId);
    }

    /**
     * ========================================================================
     * TICKETS SOLD FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Event.ticketsSold: Int!
     *
     * Returns the count of tickets in "sold" statuses for this event.
     * This is used for analytics and availability calculations.
     *
     * @param dfe The DataFetchingEnvironment containing the parent Event
     * @return Count of sold tickets
     */
    @DgsData(parentType = "Event", field = "ticketsSold")
    public Mono<Integer> getTicketsSold(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");

        log.debug("Federation: Resolving Event.ticketsSold for eventId={}", eventId);

        return ticketService.countByEventIdAndStatusIn(eventId, SOLD_STATUSES)
                .map(Long::intValue);
    }

    /**
     * ========================================================================
     * REVENUE FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: Event.revenue: BigDecimal!
     *
     * Calculates total revenue from ticket sales for this event.
     * Only counts tickets in "sold" statuses (not refunded/cancelled).
     *
     * @param dfe The DataFetchingEnvironment containing the parent Event
     * @return Total revenue as BigDecimal
     */
    @DgsData(parentType = "Event", field = "revenue")
    public Mono<BigDecimal> getEventRevenue(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");

        log.debug("Federation: Resolving Event.revenue for eventId={}", eventId);

        return ticketService.calculateRevenueByEventId(eventId)
                .map(result -> result != null ? result.getValueOrZero() : BigDecimal.ZERO)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * ========================================================================
     * TICKETS AVAILABLE FIELD RESOLVER (Uses @requires)
     * ========================================================================
     *
     * Resolves: Event.ticketsAvailable: Int! @requires(fields: "totalCapacity")
     *
     * IMPORTANT: This field uses @requires in the schema!
     *
     * The @requires directive tells Apollo Router:
     * "Before calling this resolver, fetch the 'totalCapacity' field from
     * the Catalog Service and include it in the Event object."
     *
     * So when this resolver is called, the Event map will contain:
     * - id (from @key)
     * - totalCapacity (from @requires, fetched from Catalog Service)
     *
     * Formula: ticketsAvailable = totalCapacity - ticketsSold
     *
     * @param dfe The DataFetchingEnvironment with Event including totalCapacity
     * @return Available ticket count
     */
    @DgsData(parentType = "Event", field = "ticketsAvailable")
    public Mono<Integer> getTicketsAvailable(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");

        // totalCapacity is provided by @requires - Router fetched it from Catalog
        Integer totalCapacity = (Integer) event.get("totalCapacity");

        log.debug("Federation: Resolving Event.ticketsAvailable for eventId={}, totalCapacity={}",
                eventId, totalCapacity);

        if (totalCapacity == null) {
            log.warn("totalCapacity is null for eventId={}. @requires may not be working correctly.", eventId);
            return Mono.just(0);
        }

        return ticketService.countByEventIdAndStatusIn(eventId, SOLD_STATUSES)
                .map(soldCount -> Math.max(0, totalCapacity - soldCount.intValue()));
    }

    /**
     * ========================================================================
     * SOLD OUT FIELD RESOLVER (Uses @requires)
     * ========================================================================
     *
     * Resolves: Event.soldOut: Boolean! @requires(fields: "totalCapacity")
     *
     * Returns true if all tickets are sold (available <= 0).
     *
     * @param dfe The DataFetchingEnvironment with Event including totalCapacity
     * @return True if sold out
     */
    @DgsData(parentType = "Event", field = "soldOut")
    public Mono<Boolean> isSoldOut(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");
        Integer totalCapacity = (Integer) event.get("totalCapacity");

        log.debug("Federation: Resolving Event.soldOut for eventId={}", eventId);

        if (totalCapacity == null || totalCapacity == 0) {
            return Mono.just(true);
        }

        return ticketService.countByEventIdAndStatusIn(eventId, SOLD_STATUSES)
                .map(soldCount -> soldCount >= totalCapacity);
    }

    /**
     * ========================================================================
     * SALES PERCENTAGE FIELD RESOLVER (Uses @requires)
     * ========================================================================
     *
     * Resolves: Event.salesPercentage: Float! @requires(fields: "totalCapacity")
     *
     * Returns the percentage of tickets sold (0.0 to 100.0).
     *
     * @param dfe The DataFetchingEnvironment with Event including totalCapacity
     * @return Sales percentage as Float
     */
    @DgsData(parentType = "Event", field = "salesPercentage")
    public Mono<Float> getSalesPercentage(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> event = dfe.getSource();
        String eventId = (String) event.get("id");
        Integer totalCapacity = (Integer) event.get("totalCapacity");

        log.debug("Federation: Resolving Event.salesPercentage for eventId={}", eventId);

        if (totalCapacity == null || totalCapacity == 0) {
            return Mono.just(0.0f);
        }

        return ticketService.countByEventIdAndStatusIn(eventId, SOLD_STATUSES)
                .map(soldCount -> (soldCount.floatValue() / totalCapacity) * 100);
    }
}
