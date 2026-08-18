package com.pml.shared.constants;

import java.util.EnumSet;
import java.util.Set;

/**
 * The seven ticket states of ET-TKT-002 R7, and nothing else.
 *
 * <h2>What the previous eleven were</h2>
 * {@code PENDING_PAYMENT}, {@code PENDING_VERIFICATION}, {@code PURCHASED},
 * {@code CONFIRMED}, {@code VALIDATED}, {@code USED}, {@code EXPIRED},
 * {@code CANCELLED}, {@code REFUNDED}, {@code CHARGEDBACK},
 * {@code PAYMENT_FAILED}. Four of them described a ticket that had not been paid
 * for, which ET-TKT-001 R7 made unreachable — a ticket is now issued <em>inside</em>
 * the confirmation transaction, so no ticket document has ever existed in a
 * pre-payment state since that change shipped. They stayed in the enum
 * describing a lifecycle the code no longer had.
 *
 * <h2>The two merges, and why they are safe</h2>
 * {@code PURCHASED} and {@code CONFIRMED} were never distinguished by any
 * branch: every call site that admitted one admitted the other, and the pair
 * appeared together in eleven separate status lists. Both become {@code ISSUED}.
 *
 * <p>{@code VALIDATED} and {@code USED} looked like the risky merge — a
 * two-phase gate where a ticket is checked and then consumed. It was not one.
 * {@code CheckInServiceImpl} admitted from {@code PURCHASED} and
 * {@code CONFIRMED} only, and treated {@code VALIDATED} and {@code USED}
 * identically as "already admitted"; the only writer of {@code USED} was
 * {@code TicketService.useTicket}, a superseded path with no caller in the
 * gate. Merging them changes no admission decision. Both become
 * {@code VALIDATED}.
 *
 * <h2>Where CHARGEDBACK went</h2>
 * ET-FIN-004 R8 states it outright: on a chargeback "the ticket moves to
 * {@code REFUNDED}". The money left the platform; whether it left because the
 * buyer asked or because their bank took it is a fact about the chargeback
 * record, not about the ticket. {@code booking_chargebacks} already holds that
 * distinction, so a separate ticket state was storing it twice.
 *
 * <h2>TRANSFERRED is declared but is not a resting state</h2>
 * ET-TKT-002 §4 is explicit: it "is a marker on the <em>source</em> ticket's
 * history rather than a resting state — the ticket itself returns to
 * {@code ISSUED} under a new {@code ownerId}, which is what keeps it
 * scannable." R7 requires the constant to exist; the transition table in
 * {@code TicketStateMachine} is where its shape is enforced.
 *
 * @see <a href="file:../../../../../../../../specs/ticketing/002-ticket-issuance-and-qr/spec.md">ET-TKT-002 R7</a>
 */
public enum TicketStatus {

    /** Issued inside the confirmation transaction. The only state a gate admits from. */
    ISSUED,

    /** Admitted at the gate. Still a good ticket — ET-TKT-003 R8 keeps it refundable. */
    VALIDATED,

    /** Declared by R7; see the class note — the ticket rests at {@code ISSUED}. */
    TRANSFERRED,

    /** A refund has been requested and not yet settled ([ET-FIN-004] R3). */
    REFUND_PENDING,

    /** Terminal. Reached by refund settlement or by a chargeback (ET-FIN-004 R8). */
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

    /** The only state a gate admits from — ET-TKT-003 R1's conditional update filters on it. */
    public boolean isAdmissible() {
        return this == ISSUED;
    }

    /** The holder passed the gate. */
    public boolean isAdmitted() {
        return this == VALIDATED;
    }

    /** No transition is legal from here (ET-TKT-002 R7). */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * May a refund or a chargeback be raised against this ticket?
     *
     * <p>ET-TKT-003 R8: a validated ticket is still refundable. Coupling the two
     * would mean a scan at the door forfeits an entitlement.
     */
    public boolean isRefundable() {
        return this == ISSUED || this == VALIDATED;
    }
}
