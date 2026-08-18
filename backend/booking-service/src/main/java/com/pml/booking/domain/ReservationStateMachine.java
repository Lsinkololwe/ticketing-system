package com.pml.booking.domain;

import com.pml.shared.constants.ReservationStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.pml.shared.constants.ReservationStatus.CONFIRMED;
import static com.pml.shared.constants.ReservationStatus.EXPIRED;
import static com.pml.shared.constants.ReservationStatus.FAILED;
import static com.pml.shared.constants.ReservationStatus.HELD;
import static com.pml.shared.constants.ReservationStatus.RELEASED;

/**
 * The seven legal reservation transitions of ET-TKT-001 R6, and nothing else.
 *
 * <h2>Why a table rather than checks at the call sites</h2>
 * Five states and six actions make thirty pairs, of which seven are legal. Spread
 * across call sites, the twenty-three illegal ones are not written down anywhere
 * — each site guards the cases its author thought of, and the gaps are invisible
 * because nothing enumerates what is missing. Here the whole grid exists, so a
 * test can drive all thirty pairs and the absent ones fail by default.
 *
 * <h2>What the illegal transitions actually protect</h2>
 * The dangerous ones are not exotic. {@code CONFIRMED → RELEASED} would return
 * seats that have already been sold and paid for. {@code EXPIRED → CONFIRMED}
 * would issue tickets against inventory handed back to someone else. Both are
 * one careless retry away without a table that refuses them.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
public final class ReservationStateMachine {

    private ReservationStateMachine() {}

    /** The six actions of R6's table. */
    public enum Action {
        /** Buyer takes the hold. The only action with no origin state. */
        RESERVE,
        /** Payment succeeded — writes the tickets and credits escrow. */
        CONFIRM,
        /** Buyer changed their mind. */
        CANCEL,
        /** The sweep found the hold past its expiry. */
        EXPIRE,
        /** Payment failed; the seats go back. */
        RELEASE,
        /** Payment resolved but the purchase could not complete. */
        FAIL
    }

    /**
     * From-state → action → to-state. A pair absent here is illegal.
     *
     * <p>{@code RESERVE} is deliberately not in this map: it has no origin, and
     * modelling "null" as a key would let a caller apply it to an existing
     * reservation and silently reopen a terminal one.
     */
    private static final Map<ReservationStatus, Map<Action, ReservationStatus>> TRANSITIONS = Map.of(
            HELD, Map.of(
                    Action.CONFIRM, CONFIRMED,
                    Action.CANCEL, RELEASED,
                    Action.EXPIRE, EXPIRED,
                    Action.RELEASE, RELEASED,
                    Action.FAIL, FAILED),
            // Every other state is terminal and maps to nothing at all. Spelled
            // out rather than omitted so the grid reads as complete.
            CONFIRMED, Map.of(),
            EXPIRED, Map.of(),
            RELEASED, Map.of(),
            FAILED, Map.of());

    /** The states from which no action is legal. */
    public static final Set<ReservationStatus> TERMINAL =
            EnumSet.of(CONFIRMED, EXPIRED, RELEASED, FAILED);

    /** Raised when a caller attempts one of the twenty-three illegal pairs. */
    public static class IllegalTransitionException extends RuntimeException {
        private final ReservationStatus currentStatus;

        public IllegalTransitionException(ReservationStatus from, Action action) {
            // Carries currentStatus because the caller usually raced with
            // something: knowing what won is the difference between a retry and
            // an investigation.
            super("PAYOUT_STATE_INVALID: cannot " + action + " a reservation that is " + from);
            this.currentStatus = from;
        }

        public ReservationStatus getCurrentStatus() {
            return currentStatus;
        }
    }

    /** @return the resulting state, or empty when the pair is illegal */
    public static Optional<ReservationStatus> next(ReservationStatus from, Action action) {
        if (from == null || action == null || action == Action.RESERVE) {
            return Optional.empty();
        }
        return Optional.ofNullable(TRANSITIONS.getOrDefault(from, Map.of()).get(action));
    }

    /** @throws IllegalTransitionException when the pair is not one of the seven */
    public static ReservationStatus require(ReservationStatus from, Action action) {
        return next(from, action)
                .orElseThrow(() -> new IllegalTransitionException(from, action));
    }

    public static boolean isTerminal(ReservationStatus status) {
        return TERMINAL.contains(status);
    }
}
