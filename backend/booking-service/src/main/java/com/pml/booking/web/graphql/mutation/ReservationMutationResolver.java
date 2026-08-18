package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * The buyer-facing half of the reservation: take a hold, or give it up.
 *
 * <h2>What a client cannot do here</h2>
 * There is no {@code completeReservation} and no {@code confirmPurchase}.
 * ET-TKT-001 §4 rules it out by name — confirmation follows the payment
 * outcome, "never by a client asserting that it paid". The mutation that used to
 * sit here took a reservation id and a phone number and minted tickets, which
 * meant anyone who could call it could have tickets for free.
 *
 * <p>There is also no {@code extendReservation}. The spec lists hold extension
 * under what is deliberately never in scope: every buyer asks, and a tier whose
 * holds can be extended never recovers.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReservationMutationResolver {

    private final ReservationService reservationService;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> reserveTickets(@InputArgument ReserveTicketsInput input) {
        // userId from the JWT, never from input: a buyer-supplied id would let
        // one account place holds against another's purchase limits.
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("reserveTickets: buyer {} on event {}", userId, input.eventId()))
                .flatMap(userId -> reservationService.createReservation(userId, input));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> cancelReservation(@InputArgument String reservationId) {
        // Scoped to the caller's own reservation. Without the ownership check a
        // buyer could release somebody else's hold seconds before an on-sale,
        // and the seats would land back in the pool for them to take.
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> reservationService.findById(reservationId)
                        .filter(reservation -> userId.equals(reservation.getUserId()))
                        .flatMap(reservation -> reservationService.cancelReservation(reservationId))
                        .defaultIfEmpty(false));
    }

    /**
     * Force-expires a stuck reservation.
     *
     * <p>Admin-only, and it does the same thing the sweep does rather than
     * anything special: it releases the inventory and records the reservation
     * as resolved. An operator reaching for this is working around a sweep that
     * did not fire, so giving them a different code path would hide that.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Boolean> forceExpireReservation(@InputArgument String reservationId) {
        log.warn("forceExpireReservation({}) — admin override of the expiry sweep", reservationId);
        return reservationService.cancelReservation(reservationId);
    }
}
