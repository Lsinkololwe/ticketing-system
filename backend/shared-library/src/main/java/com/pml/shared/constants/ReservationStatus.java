package com.pml.shared.constants;

/**
 * Reservation lifecycle, per ET-TKT-001 R6.
 *
 * <p>Exactly the five the spec declares. This enum matters more than a status
 * list usually does, because ET-TKT-001 R8 makes it the purchase saga's ONLY
 * state: <em>"the saga's state is {@code ReservationStatus} plus the payment
 * intent's status — no separate saga collection exists"</em>. A recovery sweep
 * after a crash has these five values and the intent to work from, and nothing
 * else. A missing state is therefore not a naming gap — it is a purchase the
 * sweep cannot classify, and so cannot resolve.
 *
 * <h2>What changed and why</h2>
 * <ul>
 *   <li>{@code ACTIVE → HELD} — a rename, but a clarifying one: what is held is
 *       inventory, and that is the whole point of the state.</li>
 *   <li>{@code CONVERTED → CONFIRMED} — "converted" described the row's own
 *       bookkeeping; "confirmed" describes what happened to the purchase.</li>
 *   <li>{@code CANCELLED → RELEASED} — the seats going back is the fact that
 *       matters downstream. Who or what caused it is a reason, not a state.</li>
 *   <li>{@link #FAILED} is <b>new</b>, and the gap that mattered. Without it a
 *       purchase that took money but could not be completed had nowhere to sit:
 *       it would be marked cancelled, look identical to a buyer changing their
 *       mind, and stop being investigated — while a payment was still out
 *       there.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
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
     * (R5) — a check in code loses that race.
     */
    HELD,

    /**
     * Payment succeeded and the tickets exist.
     *
     * <p>Reaching this is what CREATES the ticket rows: ET-TKT-001 R7 writes
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
     * two hides the case that needs a human — which is why the spec has both.
     */
    FAILED
}
