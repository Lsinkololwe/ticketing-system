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
 * USER EXTENSION RESOLVER - Federation Extended Fields
 * ============================================================================
 *
 * This resolver handles fields that the Booking Service ADDS to the User type.
 * The User type is owned by the Identity Service, but we extend it with
 * purchase-related fields.
 *
 * HOW THIS WORKS:
 * ---------------
 * 1. Client queries: user(id: "123") { firstName purchasedTickets { ticketNumber } }
 * 2. Router sees that "firstName" belongs to Identity, "purchasedTickets" to Booking
 * 3. Router calls Identity Service for User { id, firstName }
 * 4. Router calls Booking Service with the User's id
 * 5. This resolver fetches tickets purchased by that user
 * 6. Router merges the results
 *
 * IMPORTANT: User.purchasedTickets is resolved by THIS service,
 * not the Identity Service, even though User is "owned" by Identity.
 * This is the power of federation - services can add fields to types
 * they don't own.
 *
 * ============================================================================
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class UserExtensionResolver {

    private final TicketService ticketService;

    /**
     * Statuses that represent active tickets (not cancelled/refunded).
     */
    private static final Set<TicketStatus> ACTIVE_STATUSES = Set.of(
            TicketStatus.PURCHASED,
            TicketStatus.CONFIRMED,
            TicketStatus.VALIDATED
    );

    /**
     * ========================================================================
     * PURCHASED TICKETS FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: User.purchasedTickets: [Ticket!]!
     *
     * Returns all tickets purchased by this user, regardless of status.
     * Includes confirmed, refunded, cancelled, etc.
     *
     * Example query:
     *   me {
     *     firstName           <- From Identity Service
     *     purchasedTickets {  <- From THIS resolver
     *       ticketNumber
     *       event {
     *         title           <- From Catalog Service (via federation)
     *       }
     *     }
     *   }
     *
     * @param dfe The DataFetchingEnvironment containing the parent User
     * @return Flux of all tickets purchased by this user
     */
    @DgsData(parentType = "User", field = "purchasedTickets")
    public Flux<Ticket> getUserPurchasedTickets(DgsDataFetchingEnvironment dfe) {
        // The parent User is provided as a Map (from federation resolution)
        Map<String, Object> user = dfe.getSource();
        String userId = (String) user.get("id");

        log.debug("Federation: Resolving User.purchasedTickets for userId={}", userId);

        return ticketService.findByBuyerId(userId);
    }

    /**
     * ========================================================================
     * TOTAL SPENT FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: User.totalSpent: BigDecimal!
     *
     * Calculates the total amount spent by this user on tickets.
     * Only counts tickets that are purchased/confirmed (not refunded).
     *
     * @param dfe The DataFetchingEnvironment containing the parent User
     * @return Total amount spent as BigDecimal
     */
    @DgsData(parentType = "User", field = "totalSpent")
    public Mono<BigDecimal> getUserTotalSpent(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> user = dfe.getSource();
        String userId = (String) user.get("id");

        log.debug("Federation: Resolving User.totalSpent for userId={}", userId);

        return ticketService.calculateTotalSpentByBuyerId(userId)
                .map(result -> result != null ? result.getValueOrZero() : BigDecimal.ZERO)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * ========================================================================
     * ACTIVE TICKET COUNT FIELD RESOLVER
     * ========================================================================
     *
     * Resolves: User.activeTicketCount: Int!
     *
     * Returns the count of active (non-refunded, non-cancelled, non-used) tickets.
     * Useful for showing "You have X upcoming events" on a user dashboard.
     *
     * @param dfe The DataFetchingEnvironment containing the parent User
     * @return Count of active tickets
     */
    @DgsData(parentType = "User", field = "activeTicketCount")
    public Mono<Integer> getActiveTicketCount(DgsDataFetchingEnvironment dfe) {
        Map<String, Object> user = dfe.getSource();
        String userId = (String) user.get("id");

        log.debug("Federation: Resolving User.activeTicketCount for userId={}", userId);

        return ticketService.countByBuyerIdAndStatusIn(userId, ACTIVE_STATUSES)
                .map(Long::intValue);
    }
}
