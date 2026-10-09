package com.pml.shared.constants;

/**
 * Lifecycle of a per-event escrow account.
 *
 * <p>Exactly five states, and the collection's MongoDB validator must agree with them: this is
 * the collection that holds organizers' money, and it can have only one answer.
 *
 * <h2>States it deliberately does not have</h2>
 * <ul>
 *   <li>No "created but unfunded" state. The account opens when the event publishes and is
 *       {@link #ACTIVE} from that moment; a balance of zero already says "no funds yet"
 *       without a status for it.</li>
 *   <li>No "processing payout" state. In-flight payout state belongs to the payout request
 *       ({@code booking_payout_requests.status}), not to the account. Holding it in both
 *       places is two sources of truth for one fact, and they drift.</li>
 *   <li>No separate "cancelled" state. {@link #CLOSED} covers both endings and refuses
 *       credits. <b>This gives up a distinction:</b> "the event was cancelled and everyone was
 *       refunded" and "everything was paid out normally" share a status. The journal still
 *       separates them — refunds and payouts are different entry types — so the fact is not
 *       lost, only the shortcut to it.</li>
 * </ul>
 *
 * <p>Lives in shared-library rather than in booking-service so catalog-service's
 * reference-data bootstrapper can reflect over it to derive the administrator-
 * editable status list.
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
