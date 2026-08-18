package com.pml.shared.constants;

/**
 * Lifecycle of a per-event escrow account, per ET-FIN-001 R4.
 *
 * <p>Exactly the five the spec names. It previously carried seven, none of which
 * matched the six in the collection's own MongoDB validator — three different
 * answers for the collection that holds organizers' money. The spec is the one
 * that wins.
 *
 * <h2>What the four dropped constants became</h2>
 * <ul>
 *   <li>{@code CREATED} → {@link #ACTIVE}. It meant "opened but no funds yet",
 *       a distinction only {@code activate()} cared about. ET-FIN-001 R4 opens
 *       the account when the event publishes and it is live from that moment;
 *       a balance of zero already says "no funds yet" without a status for it.</li>
 *   <li>{@code LOCKED} → {@link #HOLD}. A rename.</li>
 *   <li>{@code PROCESSING_PAYOUT} → dropped. In-flight payout state belongs to
 *       the payout request ({@code booking_payout_requests.status}), not to the
 *       account. Holding it in both places is two sources of truth for one fact,
 *       and they drift.</li>
 *   <li>{@code CANCELLED} → {@link #CLOSED}. Both are terminal and both refuse
 *       credits. <b>This loses a distinction:</b> "the event was cancelled and
 *       everyone was refunded" and "everything was paid out normally" now share
 *       a status. The journal still separates them — refunds and payouts are
 *       different entry types — so the fact is not lost, only the shortcut to it.</li>
 * </ul>
 *
 * <p>Lives in shared-library rather than in booking-service so catalog-service's
 * reference-data bootstrapper can reflect over it to derive the administrator-
 * editable status list.
 *
 * @see <a href="file:../../../../../../../../specs/finance/001-escrow-and-ledger/spec.md">ET-FIN-001</a>
 */
public enum EscrowStatus {

    /** Open and receiving ticket revenue. The state an account opens in. */
    ACTIVE,

    /**
     * The event has ended and the hold period is counting down.
     *
     * <p>Entered by {@code catalog.EventCompleted}, which sets
     * {@code holdUntil = endsAt + finance.escrow.hold-period}. A clock, not a
     * person — nothing is waiting on a decision.
     */
    HOLD,

    /** The hold elapsed with no open disputes; the organizer may request a payout. */
    PAYOUT_ELIGIBLE,

    /**
     * Held by the platform — fraud review, a dispute, a compliance stop.
     *
     * <p>Distinct from {@link #HOLD} on purpose: this one ends when a person
     * decides it does, and it can be entered from any live state.
     */
    SUSPENDED,

    /** Terminal. Fully paid out, or fully refunded after a cancellation. */
    CLOSED
}
