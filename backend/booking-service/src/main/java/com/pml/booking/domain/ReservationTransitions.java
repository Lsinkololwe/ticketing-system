package com.pml.booking.domain;

import com.pml.booking.domain.model.TicketReservation;
import com.pml.shared.constants.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * The only writer permitted to move a reservation's status.
 *
 * <h2>Why a compare-and-set rather than read-check-save</h2>
 * Two writers race for the same reservation <em>by design</em>. ET-TKT-001 §2 says
 * so outright: the expiry sweep and an arriving payment callback will both reach a
 * hold that lapsed while the buyer was entering their PIN, and the TTL index and
 * the sweep will both fire for the same expired row. A read, then a legality
 * check, then a save leaves a window between the read and the save in which the
 * other writer commits — and both writers then believe they moved it, so the
 * inventory is returned twice.
 *
 * <p>Here the legality check and the write are one conditional update. The status
 * the state machine was consulted about is in the query, so a row that moved in
 * between simply does not match. Exactly one writer gets {@code true}, and only
 * that writer touches the counters.
 *
 * <h2>Why false is not an error</h2>
 * Losing the race is the expected outcome for at least one of the two writers
 * every time an expired hold is paid for. {@link #compareAndSet} reports it as
 * {@code false} rather than raising, because the caller's correct response is to
 * do nothing — the work it was about to do has already been done by whoever won.
 *
 * <p>Attempting a transition the state machine forbids is different, and does
 * raise: that is a caller bug, not a race.
 *
 * @see ReservationStateMachine for which pairs are legal
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTransitions {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Applies {@code action} to the reservation, if and only if it is still in
     * {@code from} at the moment of the write.
     *
     * @param reason recorded when the action is {@link ReservationStateMachine.Action#FAIL};
     *               ignored otherwise
     * @return {@code true} when this caller made the move and therefore owns the
     *         follow-up work; {@code false} when another writer got there first
     * @throws ReservationStateMachine.IllegalTransitionException when the pair is
     *         not one of ET-TKT-001 R6's seven
     */
    public Mono<Boolean> compareAndSet(String reservationId,
                                       ReservationStatus from,
                                       ReservationStateMachine.Action action,
                                       String reason) {
        ReservationStatus to = ReservationStateMachine.require(from, action);
        LocalDateTime now = LocalDateTime.now();

        Update update = new Update()
                .set("status", to)
                .set("updatedAt", now);
        stampTerminal(update, to, now, reason);

        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(reservationId).and("status").is(from)),
                        update,
                        TicketReservation.class)
                .map(result -> result.getModifiedCount() == 1)
                .doOnNext(moved -> {
                    if (moved) {
                        log.info("Reservation {}: {} -> {} ({})", reservationId, from, to, action);
                    } else {
                        // Not a warning. This is the designed outcome of a race
                        // the spec expects to happen, and logging it as a problem
                        // trains people to ignore the log.
                        log.debug("Reservation {} was no longer {} — another writer resolved it first",
                                reservationId, from);
                    }
                });
    }

    /** Convenience for the overwhelmingly common case of a reservation leaving {@code HELD}. */
    public Mono<Boolean> compareAndSetFromHeld(String reservationId,
                                               ReservationStateMachine.Action action,
                                               String reason) {
        return compareAndSet(reservationId, ReservationStatus.HELD, action, reason);
    }

    /**
     * Records when and why the reservation reached its terminal state.
     *
     * <p>Each terminal state gets its own timestamp field rather than sharing a
     * {@code resolvedAt}, so that "expired unpaid" and "payment failed" stay
     * separable in the reports — which is the whole reason ET-TKT-001 §4 keeps
     * {@code EXPIRED} and {@code RELEASED} as distinct states.
     */
    private void stampTerminal(Update update, ReservationStatus to, LocalDateTime now, String reason) {
        switch (to) {
            case CONFIRMED -> update.set("confirmedAt", now);
            case RELEASED, EXPIRED -> update.set("releasedAt", now);
            case FAILED -> {
                update.set("failedAt", now);
                update.set("failureReason", reason == null ? "Unspecified" : reason);
            }
            case HELD -> { /* not reachable: nothing transitions into HELD */ }
        }
    }
}
