package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.CompleteReservationInput;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.service.ReservationService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL Mutation Resolver for Ticket Reservations
 *
 * Business Intent: Handles reservation creation, completion, and cancellation.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReservationMutationResolver {

    private final ReservationService reservationService;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> reserveTickets(@InputArgument ReserveTicketsInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("GraphQL mutation: reserveTickets for user: {}", userId))
                .flatMap(userId -> reservationService.createReservation(userId, input));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<List<Ticket>> completeReservation(@InputArgument CompleteReservationInput input) {
        log.info("GraphQL mutation: completeReservation({})", input.reservationId());
        return reservationService.completeReservation(input.reservationId(), input);
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> cancelReservation(@InputArgument String reservationId) {
        log.info("GraphQL mutation: cancelReservation({})", reservationId);
        return reservationService.cancelReservation(reservationId);
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<TicketReservation> extendReservation(
        @InputArgument String reservationId,
        @InputArgument int minutes
    ) {
        log.info("GraphQL mutation: extendReservation({}, {} minutes)", reservationId, minutes);
        return reservationService.extendReservation(reservationId, minutes);
    }

    /**
     * Admin operation to force-expire a stuck reservation.
     * Schema: forceExpireReservation(reservationId: ID!): Boolean!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Boolean> forceExpireReservation(@InputArgument String reservationId) {
        log.info("GraphQL mutation: forceExpireReservation({}) - admin forced expiration", reservationId);
        return reservationService.cancelReservation(reservationId)
                .doOnSuccess(result -> log.info("Force expired reservation {}: {}", reservationId, result))
                .onErrorResume(e -> {
                    log.error("Failed to force expire reservation {}: {}", reservationId, e.getMessage());
                    return Mono.just(false);
                });
    }
}
