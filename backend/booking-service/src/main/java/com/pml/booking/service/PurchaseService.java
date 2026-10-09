package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Carries a reservation to a ticket, or back to nothing.
 *
 * <h2>Why this is a service the workflow calls</h2>
 * A purchase interrupted by a crash has to be resolvable afterwards, so confirmation is an
 * idempotent step the purchase workflow's activities run from durable state — the same code
 * whether the callback arrived normally or the workflow resumed after a restart. One
 * implementation — otherwise the recovery path is a second, less-tested copy of the
 * money-moving code.
 *
 * <h2>What "no client mutation" means here</h2>
 * There is deliberately no way to reach {@link #confirm} from GraphQL.
 * Confirmation is driven by the payment outcome, never by a client asserting
 * that it paid. The only caller is the purchase workflow's activities.
 */
public interface PurchaseService {

    /**
     * Converts a held reservation into tickets, escrow credit and commission —
     * all of it, or none of it.
     *
     * <p>Idempotent: a reservation already {@code CONFIRMED} returns the tickets
     * the first confirmation wrote, without issuing more. Duplicate provider
     * callbacks are routine, so this is the common case, not the edge case.
     *
     * @param reservationId   the reservation to confirm
     * @param paymentIntentId the intent that paid for it, recorded on the tickets
     * @param providerTxnId   the provider's reference, for reconciliation
     * @return the issued tickets
     */
    Mono<List<Ticket>> confirm(String reservationId, String paymentIntentId, String providerTxnId);

    /**
     * Gives the inventory back and moves the reservation to a terminal state.
     *
     * <p>Safe to apply twice — the TTL index, the expiry timer and a failed
     * payment callback will all reach the same reservation, and at least two of
     * them will fire for the same row. Only the writer that wins the
     * compare-and-set touches the counters.
     *
     * @param terminalAction {@code CANCEL}, {@code EXPIRE}, {@code RELEASE} or
     *                       {@code FAIL} — which one is recorded matters, because
     *                       "nobody paid in time" and "the payment failed" are
     *                       different problems
     * @param reason         recorded when the reservation is being failed
     * @return the reservation as it now stands, whoever moved it
     */
    Mono<TicketReservation> release(String reservationId,
                                    com.pml.booking.domain.ReservationStateMachine.Action terminalAction,
                                    String reason);
}
