package com.pml.booking.service;

import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * The inventory hold: creating it, and giving it back.
 *
 * <h2>What is deliberately absent</h2>
 * <ul>
 *   <li><b>{@code completeReservation}</b> — ET-TKT-001 §4 puts
 *       {@code confirmPurchase} outside the client surface entirely:
 *       confirmation follows the payment outcome, "never by a client asserting
 *       that it paid". The old mutation let a caller mint tickets by asking
 *       nicely. Confirmation now lives in {@link PurchaseService}, reachable
 *       only from the payment listener and the recovery sweep.</li>
 *   <li><b>{@code extendReservation}</b> — listed under the spec's "deliberately
 *       never in scope": every buyer asks, and a tier that can be extended never
 *       recovers. Ten minutes is the trade, and it is not negotiable per
 *       buyer.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
public interface ReservationService {

    /**
     * Takes the inventory and writes the hold.
     *
     * <p>Idempotent on {@code idempotencyKey} (R6) and capped at one live hold
     * per buyer per tier (R5): a second call for a tier the buyer already holds
     * returns the existing reservation rather than stacking a second, because
     * one flaky connection would otherwise lock out four other buyers.
     */
    Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input);

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

    Flux<TicketReservation> findExpiredByEventId(String eventId, LocalDateTime since);

    Flux<TicketReservation> findExpiredSince(LocalDateTime since);

    /**
     * Releases every hold past its expiry (R4).
     *
     * <p>Runs on a schedule and is safe to run concurrently with itself: the
     * per-reservation compare-and-set means two sweeps meeting the same row
     * return the inventory once between them.
     *
     * @return how many reservations this run actually expired
     */
    Mono<Long> expireReservations();
}
