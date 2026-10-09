package com.pml.booking.domain;

import com.pml.shared.constants.PlatformTime;

import com.pml.shared.constants.EscrowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether an organizer may draw a payout from one event's escrow, and if not, why.
 *
 * <h2>Why this is a value object with a static method</h2>
 * All conditions are evaluated by one method over plain values, testable at
 * layer 1, and the same method runs at request AND again at approval. Days pass between those two moments and a chargeback can
 * arrive in the window — a single check at request approves a payout against
 * money that has since been claimed back.
 *
 * <p>Two call sites evaluating the same rules in their own code is how those
 * rules drift apart. Everything here is a plain value, so this is one method
 * that both call and that a test can exercise without a database.
 *
 * <h2>Why the failures are a list and not a boolean</h2>
 * An organizer looking at a disabled "Request payout" button needs to know
 * whether to wait four days or resolve a dispute. A boolean tells them neither,
 * and a single "first failure" hides the second reason they will hit the moment
 * they fix the first.
 */
public record PayoutEligibility(
        boolean eligible,
        List<Reason> reasons,
        /** The withdrawable balance. A payout is all of it or none — no partials. */
        BigDecimal availableAmount,
        String currency,
        /** When the hold elapses. Null once it has, or when there is no escrow. */
        Instant opensAt,
        /** The floor a balance must clear, so the client can say how far short it is. */
        BigDecimal minimumAmount
) {

    /** Why a payout cannot be requested. */
    public enum Reason {
        /** No escrow account exists for this event under this organizer. */
        NO_ESCROW_ACCOUNT,
        /** The event has not finished, so its money is still being taken. */
        EVENT_NOT_COMPLETED,
        /** The event finished but the hold period has not elapsed. */
        HOLD_NOT_ELAPSED,
        /** A chargeback is open against this event and may claim the money back. */
        OPEN_DISPUTES,
        /**
         * The platform has suspended this escrow — fraud review, a dispute, a
         * compliance stop.
         *
         * <p>Separate from {@link #HOLD_NOT_ELAPSED} because the two need
         * different things from the organizer. A hold ends on a date and the
         * honest answer is "wait"; a suspension ends when a person lifts it, and
         * telling someone to wait for a date that will never arrive is worse
         * than telling them nothing.
         */
        ESCROW_SUSPENDED,
        /** The balance is below the platform minimum. */
        BELOW_MINIMUM,
        /**
         * A payout request for this escrow is already open.
         *
         * <p>Not one of the four money conditions.
         * It is included because it is a real reason the button cannot be
         * pressed, and the alternative — an enabled button whose request the
         * server then rejects — is worse than a disabled one that says why.
         */
        PAYOUT_ALREADY_REQUESTED
    }

    /**
     * Evaluate eligibility over values.
     *
     * @param escrowStatus     the escrow's lifecycle state; null when no escrow exists
     * @param lockUntil        when the hold elapses; null means no hold recorded
     * @param balance          the escrow's current balance
     * @param openDisputeCount chargebacks against this event not yet resolved
     * @param hasOpenRequest   whether a payout request for this escrow is already open
     * @param minimum          the platform payout floor
     * @param now              evaluated against, injected so a test need not sleep
     */
    public static PayoutEligibility evaluate(
            EscrowStatus escrowStatus,
            Instant lockUntil,
            BigDecimal balance,
            long openDisputeCount,
            boolean hasOpenRequest,
            BigDecimal minimum,
            Instant now
    ) {
        List<Reason> failures = new ArrayList<>();
        BigDecimal amount = balance == null ? BigDecimal.ZERO : balance;
        BigDecimal floor = minimum == null ? BigDecimal.ZERO : minimum;

        if (escrowStatus == null) {
            // Nothing else can be evaluated: every remaining condition is a
            // property of an account that does not exist. Returning a single
            // honest reason beats four derived from nulls.
            return new PayoutEligibility(
                    false, List.of(Reason.NO_ESCROW_ACCOUNT),
                    BigDecimal.ZERO, "ZMW", null, floor);
        }

        // "The event has completed" as booking-service can actually see it.
        //
        // booking-service does not own Event and cannot read its status without
        // a cross-service call on the request path. The escrow's own transition
        // out of ACTIVE is the local signal for the same fact: it moves to HOLD
        // when the event has happened, so ACTIVE means the event is still
        // selling.
        if (escrowStatus == EscrowStatus.ACTIVE) {
            failures.add(Reason.EVENT_NOT_COMPLETED);
        }

        // A closed account is not "not yet completed" — it is done with, and
        // offering a hold date for it would be misleading.
        if (escrowStatus == EscrowStatus.CLOSED) {
            failures.add(Reason.NO_ESCROW_ACCOUNT);
        }

        // Held by a person rather than by a clock. Reporting HOLD_NOT_ELAPSED
        // would tell the organizer to wait for a date that will never release
        // it; this needs someone to lift the suspension.
        if (escrowStatus == EscrowStatus.SUSPENDED) {
            failures.add(Reason.ESCROW_SUSPENDED);
        }

        boolean holdPending = lockUntil != null && lockUntil.isAfter(now);
        if (holdPending) {
            failures.add(Reason.HOLD_NOT_ELAPSED);
        }

        if (openDisputeCount > 0) {
            failures.add(Reason.OPEN_DISPUTES);
        }

        if (amount.compareTo(floor) < 0) {
            failures.add(Reason.BELOW_MINIMUM);
        }

        if (hasOpenRequest) {
            failures.add(Reason.PAYOUT_ALREADY_REQUESTED);
        }

        return new PayoutEligibility(
                failures.isEmpty(),
                List.copyOf(failures),
                amount,
                "ZMW",
                holdPending ? lockUntil : null,
                floor);
    }

    /** Convenience for the request path, which needs a sentence to refuse with. */
    public String describeFirstFailure() {
        if (reasons.isEmpty()) {
            return "Eligible";
        }
        return switch (reasons.get(0)) {
            case NO_ESCROW_ACCOUNT -> "No escrow account is available for this event.";
            case EVENT_NOT_COMPLETED -> "The event has not finished yet.";
            case HOLD_NOT_ELAPSED -> "The hold period has not elapsed"
                    + (opensAt == null ? "." : ", it opens on " + PlatformTime.dateAt(opensAt) + ".");
            case OPEN_DISPUTES -> "There is an open dispute against this event.";
            case ESCROW_SUSPENDED -> "This event's funds are on hold pending review by the platform.";
            case BELOW_MINIMUM -> "The balance is below the " + minimumAmount + " " + currency + " minimum.";
            case PAYOUT_ALREADY_REQUESTED -> "A payout request for this event is already open.";
        };
    }
}
