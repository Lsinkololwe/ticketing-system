package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Carries a reservation to a ticket, or back to nothing.
 *
 * <h2>Why this is a service and not a listener</h2>
 * ET-TKT-001 R8 requires that a purchase interrupted by a crash be resolvable
 * afterwards. That means confirmation cannot live only in the payment listener:
 * the recovery sweep has to be able to run exactly the same confirmation, from
 * the same state, for a reservation whose callback arrived while the process was
 * dying. One implementation, two callers — otherwise the recovery path is a
 * second, less-tested copy of the money-moving code.
 *
 * <h2>What "no client mutation" means here</h2>
 * There is deliberately no way to reach {@link #confirm} from GraphQL.
 * ET-TKT-001 §4 puts it plainly: confirmation is driven by the payment outcome,
 * "never by a client asserting that it paid". The only callers are the payment
 * event listener and the recovery sweep.
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
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
     * <p>Safe to apply twice — the TTL index, the expiry sweep and a failed
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
