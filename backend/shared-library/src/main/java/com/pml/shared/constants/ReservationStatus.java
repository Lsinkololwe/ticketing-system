package com.pml.shared.constants;

/**
 * Reservation lifecycle.
 *
 * <p>Exactly five states. This enum matters more than a status list usually
 * does, because it is the purchase's state: the reservation's status plus the
 * payment intent's status, with no separate saga collection. Recovery after a
 * crash has these five values and the intent to work from, and nothing else. A
 * missing state is therefore not a naming gap — it is a purchase recovery cannot
 * classify, and so cannot resolve.
 *
 * <h2>Why these names</h2>
 * <ul>
 *   <li>{@link #HELD} — what is held is inventory, and that is the whole point
 *       of the state.</li>
 *   <li>{@link #CONFIRMED} describes what happened to the purchase, not the
 *       row's own bookkeeping.</li>
 *   <li>{@link #RELEASED} — the seats going back is the fact that matters
 *       downstream. Who or what caused it is a reason, not a state.</li>
 *   <li>{@link #FAILED} is the state a purchase that took money but could not be
 *       completed sits in. Marked released instead, it would look identical to a
 *       buyer changing their mind and stop being investigated — while a payment
 *       was still out there.</li>
 * </ul>
 */
/*
 * Lives in shared-library so catalog-service's reference-data bootstrapper can
 * reflect over it. Catalog owns the administrator-editable status list and
 * cannot depend on the service that uses the enum.
 */
public enum ReservationStatus {

    /**
     * Inventory is held and the buyer is paying. The only non-terminal state.
     *
     * <p>A partial unique index on {@code (userId, tierId)} where status is
     * {@code HELD} enforces one live hold per buyer per tier at the database
     * — a check in code loses that race.
     */
    HELD,

    /**
     * Payment succeeded and the tickets exist.
     *
     * <p>Reaching this is what CREATES the ticket rows: confirmation writes
     * one per seat inside the same transaction that credits escrow and records
     * commission. Before this state, no ticket document exists at all.
     */
    CONFIRMED,

    /** The hold ran out before payment completed. Seats returned. */
    EXPIRED,

    /** Deliberately given up — buyer cancelled, or the system let it go. Seats returned. */
    RELEASED,

    /**
     * Payment resolved but the purchase could not be completed.
     *
     * <p>Distinct from {@link #RELEASED} on purpose: a released hold is
     * uneventful, while this one may have taken a buyer's money. Collapsing the
     * two hides the case that needs a human — which is why both exist.
     */
    FAILED
}
