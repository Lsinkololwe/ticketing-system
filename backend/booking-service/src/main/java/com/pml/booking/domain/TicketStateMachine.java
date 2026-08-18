package com.pml.booking.domain;

import com.pml.shared.constants.TicketStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.pml.shared.constants.TicketStatus.CANCELLED;
import static com.pml.shared.constants.TicketStatus.EXPIRED;
import static com.pml.shared.constants.TicketStatus.ISSUED;
import static com.pml.shared.constants.TicketStatus.REFUNDED;
import static com.pml.shared.constants.TicketStatus.REFUND_PENDING;
import static com.pml.shared.constants.TicketStatus.TRANSFERRED;
import static com.pml.shared.constants.TicketStatus.VALIDATED;

/**
 * ET-TKT-002 §4's ticket transition table, and nothing else.
 *
 * <h2>Why the whole grid is written down</h2>
 * Seven states and seven actions make forty-nine pairs, of which nine are legal.
 * Before this class the nine lived as {@code if} chains at eleven call sites,
 * each guarding the cases its author had in mind — and the forty absent pairs
 * were absent from everywhere, so nothing could enumerate what was missing.
 * R7 requires "a test drives all {@code (status, transition)} pairs", which is
 * only possible against a table that claims to be complete.
 *
 * <h2>The transitions that matter</h2>
 * {@code REFUNDED → VALIDATED} would readmit a ticket whose money has gone back.
 * {@code EXPIRED → ISSUED} would resurrect a ticket after the event it was for.
 * {@code VALIDATED → VALIDATED} is the double-admission ET-TKT-003 exists to
 * prevent, and is refused here as well as by the unique index on
 * {@code booking_checkins.ticketId} — the index is the guarantee, this is the
 * error message.
 *
 * <h2>Two shapes the table cannot express</h2>
 * {@code ISSUE} has no origin state and so is not in the map; modelling it with
 * a null key would let a caller apply it to an existing ticket and reopen a
 * terminal one.
 *
 * <p>{@code RESTORE} — ET-FIN-004's "refund refused → back to previous" — is the
 * one transition whose target is not a function of {@code (from, action)}. It
 * takes the previous state explicitly through {@link #restore}, which admits
 * only the two states a refund can be requested from.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/002-ticket-issuance-and-qr/spec.md">ET-TKT-002 R7</a>
 */
public final class TicketStateMachine {

    private TicketStateMachine() {}

    /** The actions of §4's table, each owned by exactly one spec. */
    public enum Action {
        /** Confirmation issues the ticket. The only action with no origin state. ET-TKT-002. */
        ISSUE,
        /** Admitted at the gate. ET-TKT-003. */
        VALIDATE,
        /** Handed to a new owner; the ticket rests at {@code ISSUED}. ET-TKT-004. */
        TRANSFER,
        /** A refund was requested. ET-FIN-004. */
        REQUEST_REFUND,
        /** The provider confirmed the refund, or a chargeback landed. ET-FIN-004. */
        SETTLE_REFUND,
        /** The refund was declined; see {@link #restore}. ET-FIN-004. */
        RESTORE,
        /** The event was cancelled. ET-CAT-001. */
        CANCEL,
        /** The event completed and nobody scanned it. ET-TKT-002's sweep. */
        EXPIRE
    }

    /**
     * From-state → action → to-state. A pair absent here is illegal.
     *
     * <p>{@code TRANSFER} maps {@code ISSUED → ISSUED} rather than to
     * {@code TRANSFERRED}, which reads wrong until you read §4: "TRANSFERRED is
     * a marker on the source ticket's history rather than a resting state — the
     * ticket itself returns to ISSUED under a new ownerId, which is what keeps
     * it scannable." A ticket parked in {@code TRANSFERRED} would refuse at the
     * gate, which is the opposite of what a transfer is for.
     */
    private static final Map<TicketStatus, Map<Action, TicketStatus>> TRANSITIONS = Map.of(
            ISSUED, Map.of(
                    Action.VALIDATE, VALIDATED,
                    Action.TRANSFER, ISSUED,
                    Action.REQUEST_REFUND, REFUND_PENDING,
                    Action.CANCEL, CANCELLED,
                    Action.EXPIRE, EXPIRED),
            VALIDATED, Map.of(
                    // ET-TKT-003 R8: a scan at the door forfeits nothing.
                    Action.REQUEST_REFUND, REFUND_PENDING),
            REFUND_PENDING, Map.of(
                    Action.SETTLE_REFUND, REFUNDED),
            // Declared by R7 but never rested in; see the TRANSITIONS note.
            TRANSFERRED, Map.of(),
            // The three terminal states, spelled out rather than omitted so the
            // grid reads as complete rather than as an oversight.
            REFUNDED, Map.of(),
            CANCELLED, Map.of(),
            EXPIRED, Map.of());

    /** The states a refund may be requested from, and so the only legal {@link #restore} targets. */
    private static final Set<TicketStatus> REFUNDABLE_ORIGINS = EnumSet.of(ISSUED, VALIDATED);

    /** ET-TKT-002 R7's terminal three. */
    public static final Set<TicketStatus> TERMINAL = EnumSet.of(REFUNDED, CANCELLED, EXPIRED);

    /** Raised on any of the forty illegal pairs. Carries {@code currentStatus} as R7 requires. */
    public static class IllegalTransitionException extends RuntimeException {

        /** ET-PLT-005's row for this refusal. */
        public static final String CODE = "TICKET_STATE_INVALID";

        private final TicketStatus currentStatus;

        public IllegalTransitionException(TicketStatus from, Action action) {
            super(CODE + ": cannot " + action + " a ticket that is " + from);
            this.currentStatus = from;
        }

        public TicketStatus getCurrentStatus() {
            return currentStatus;
        }
    }

    /** @return the resulting state, or empty when the pair is illegal */
    public static Optional<TicketStatus> next(TicketStatus from, Action action) {
        if (from == null || action == null || action == Action.ISSUE || action == Action.RESTORE) {
            // ISSUE has no origin; RESTORE's target is not a function of the
            // pair. Both go through their own entry points.
            return Optional.empty();
        }
        return Optional.ofNullable(TRANSITIONS.getOrDefault(from, Map.of()).get(action));
    }

    /** @throws IllegalTransitionException when the pair is not one of the nine */
    public static TicketStatus require(TicketStatus from, Action action) {
        return next(from, action)
                .orElseThrow(() -> new IllegalTransitionException(from, action));
    }

    /**
     * ET-FIN-004: a declined refund returns the ticket to where it came from.
     *
     * @param previous the state the refund was requested from
     * @throws IllegalTransitionException if the ticket is not {@code REFUND_PENDING},
     *         or if {@code previous} is not a state a refund could have been requested from
     */
    public static TicketStatus restore(TicketStatus from, TicketStatus previous) {
        if (from != REFUND_PENDING || previous == null || !REFUNDABLE_ORIGINS.contains(previous)) {
            throw new IllegalTransitionException(from, Action.RESTORE);
        }
        return previous;
    }

    public static boolean isTerminal(TicketStatus status) {
        return TERMINAL.contains(status);
    }
}
