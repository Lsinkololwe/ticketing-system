package com.pml.booking.service;

import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * The inventory hold: creating it, and giving it back.
 *
 * <h2>What is deliberately absent</h2>
 * <ul>
 *   <li><b>{@code completeReservation}</b> — confirmation is outside the client
 *       surface entirely: it follows the payment outcome, never a client
 *       asserting that it paid. A client-facing mutation would let a caller mint
 *       tickets by asking nicely. Confirmation lives in {@link PurchaseService},
 *       reachable only from the purchase workflow's activities.</li>
 *   <li><b>{@code extendReservation}</b> — deliberately never offered: every buyer asks, and a tier that can be extended never
 *       recovers. Ten minutes is the trade, and it is not negotiable per
 *       buyer.</li>
 * </ul>
 */
public interface ReservationService {

    /**
     * Takes the inventory and writes the hold.
     *
     * <p>Idempotent on {@code idempotencyKey} and capped at one live hold
     * per buyer per tier: a second call for a tier the buyer already holds
     * returns the existing reservation rather than stacking a second, because
     * one flaky connection would otherwise lock out four other buyers.
     */
    Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input);

    /**
     * Takes the hold under a reservation id the caller chose — the checkout workflow's, derived from
     * the buyer and the idempotency key — so every retry of one purchase names the same hold.
     */
    Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input, String reservationId);

    /**
     * The buyer changed their mind. Releases the inventory and records
     * {@code RELEASED}.
     *
     * @return true when this call did the releasing; false when the reservation
     *         was already resolved or does not exist
     */
    Mono<Boolean> cancelReservation(String reservationId);

    Mono<TicketReservation> findById(String id);

    Flux<TicketReservation> findActiveByUserId(String userId);

    Flux<TicketReservation> findByEventId(String eventId);

    Flux<TicketReservation> findExpiredByEventId(String eventId, Instant since);

    Flux<TicketReservation> findExpiredSince(Instant since);

}
