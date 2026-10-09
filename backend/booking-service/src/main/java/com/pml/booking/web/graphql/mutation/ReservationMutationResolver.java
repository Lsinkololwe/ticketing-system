package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.workflow.purchase.PurchaseProcess;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * The buyer-facing half of the reservation: take a hold, or give it up.
 *
 * <p>Both reach the purchase's workflow through {@link PurchaseProcess}, which owns the hold's timer
 * and its release. There is no {@code confirmPurchase}: confirmation follows a verified payment
 * outcome, never a client asserting that it paid. There is no {@code extendReservation}: a tier whose
 * holds can be extended never recovers its seats.
 */
@Slf4j
@DgsComponent
@Validated
@RequiredArgsConstructor
public class ReservationMutationResolver {

    private final PurchaseProcess purchaseProcess;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> reserveTickets(@Valid @InputArgument ReserveTicketsInput input) {
        // userId from the JWT, never from input: a buyer-supplied id would let one account place
        // holds against another's purchase limits.
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("reserveTickets: buyer {} on event {}", userId, input.eventId()))
                .flatMap(userId -> purchaseProcess.reserve(userId, input));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> cancelReservation(@InputArgument String reservationId) {
        // Scoped to the caller's own reservation: without it a buyer could release somebody else's
        // hold seconds before an on-sale and take the seats themselves.
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> purchaseProcess.cancel(userId, reservationId, false));
    }

    /**
     * Releases a stuck reservation (admin). It goes through the same workflow update a buyer's
     * cancellation does, without the ownership and payment-in-flight checks.
     */
    @FailClosedOnRevocation
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Boolean> forceExpireReservation(@InputArgument String reservationId) {
        log.warn("forceExpireReservation({}) — operator release", reservationId);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(adminId -> purchaseProcess.cancel(adminId, reservationId, true));
    }
}
