package com.pml.shared.constants;

import java.util.EnumSet;
import java.util.Set;

/**
 * The seven ticket states, and nothing else.
 *
 * <h2>No pre-payment states</h2>
 * A ticket is issued <em>inside</em> the confirmation transaction, so no ticket
 * document exists before payment. There is no pending-payment, pending-verification
 * or payment-failed state because nothing could ever rest in one.
 *
 * <h2>One issued state, one admitted state</h2>
 * A sold ticket is {@code ISSUED}; no branch anywhere distinguishes a "purchased"
 * ticket from a "confirmed" one. An admitted ticket is {@code VALIDATED}: the gate
 * is a single step that checks and consumes together, so there is no separate
 * "used" state to reach after validation.
 *
 * <h2>No chargeback state</h2>
 * On a chargeback the ticket moves to {@code REFUNDED}. The money left the
 * platform; whether it left because the buyer asked or because their bank took it
 * is a fact about the chargeback record, not about the ticket.
 * {@code booking_chargebacks} holds that distinction, so a ticket state would store
 * it twice.
 *
 * <h2>TRANSFERRED is declared but is not a resting state</h2>
 * It is a marker on the <em>source</em> ticket's history rather than a resting
 * state — the ticket itself returns to {@code ISSUED} under a new {@code ownerId},
 * which is what keeps it scannable. The transition table in
 * {@code TicketStateMachine} is where its shape is enforced.
 */
public enum TicketStatus {

    /** Issued inside the confirmation transaction. The only state a gate admits from. */
    ISSUED,

    /** Admitted at the gate. Still a good ticket, and still refundable. */
    VALIDATED,

    /** A history marker; see the class note — the ticket rests at {@code ISSUED}. */
    TRANSFERRED,

    /** A refund has been requested and not yet settled. */
    REFUND_PENDING,

    /** Terminal. Reached by refund settlement or by a chargeback. */
    REFUNDED,

    /** Terminal. The event was cancelled, or an operator cancelled the ticket. */
    CANCELLED,

    /** Terminal. The event completed and the ticket was never scanned. */
    EXPIRED;

    private static final Set<TicketStatus> TERMINAL =
            EnumSet.of(REFUNDED, CANCELLED, EXPIRED);

    /**
     * Counts as a completed sale for revenue, inventory and reporting.
     *
     * <p>{@code REFUND_PENDING} is included deliberately: the money has not
     * moved back and the seat is still consumed. Excluding it would make an
     * event's revenue drop the moment a buyer <em>asks</em> for a refund, and
     * rise again if it were declined.
     */
    public boolean isSold() {
        return this == ISSUED || this == VALIDATED || this == REFUND_PENDING;
    }

    /** The only state a gate admits from — the admission's conditional update filters on it. */
    public boolean isAdmissible() {
        return this == ISSUED;
    }

    /** The holder passed the gate. */
    public boolean isAdmitted() {
        return this == VALIDATED;
    }

    /** No transition is legal from here. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * May a refund or a chargeback be raised against this ticket?
     *
     * <p>A validated ticket is still refundable. Coupling the two
     * would mean a scan at the door forfeits an entitlement.
     */
    public boolean isRefundable() {
        return this == ISSUED || this == VALIDATED;
    }
}
