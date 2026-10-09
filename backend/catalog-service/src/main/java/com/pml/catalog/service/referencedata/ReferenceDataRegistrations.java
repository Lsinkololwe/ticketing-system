package com.pml.catalog.service.referencedata;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.constants.WorkflowSemantic;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.DocumentStatus;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.InvitationStatus;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.constants.TransactionStatus;

import java.util.Map;

import static com.pml.shared.constants.WorkflowSemantic.CANCELLED;
import static com.pml.shared.constants.WorkflowSemantic.FAILED;
import static com.pml.shared.constants.WorkflowSemantic.INITIAL;
import static com.pml.shared.constants.WorkflowSemantic.IN_PROGRESS;
import static com.pml.shared.constants.WorkflowSemantic.PENDING;
import static com.pml.shared.constants.WorkflowSemantic.SUCCEEDED;

/**
 * The one thing written by hand: what each existing status MEANS.
 *
 * <h2>Why this is manual when the value list is not</h2>
 * The bootstrapper reflects over the enum classes, so the value LIST is never
 * hand-maintained — add a constant and it appears. Meaning cannot be reflected
 * over. A constant's name is a hint, not a fact:
 * {@code PENDING_REVIEW} genuinely is {@link WorkflowSemantic#PENDING},
 * while {@code PENDING_VERIFICATION} on a ticket is a payment still in flight
 * and {@code PENDING_DELETION} on an organization is a live account with a
 * countdown on it. A pattern match on "PENDING" gets one of those three right.
 *
 * <p>So each constant is classified once, here, and the bootstrapper refuses to
 * start if a workflow constant is missing rather than defaulting it. A status
 * filed under the wrong meaning is worse than one filed under none: the admin
 * screen looks correct and the behaviour is silently wrong.
 *
 * <h2>Adding a status later</h2>
 * An administrator adds one through the admin app and picks its semantic there
 * — no code change, and nothing to add to this file. It covers only the
 * constants that already exist, so today's data keeps its meaning.
 */
public final class ReferenceDataRegistrations {

    private ReferenceDataRegistrations() {}

    static {
        registerAll();
    }

    /** Loading this class runs the static initialiser. */
    public static void ensureRegistered() {
        // Intentionally empty.
    }

    private static void registerAll() {

        // The ticket states. There is no INITIAL row: a ticket cannot exist
        // before payment, because issuance happens inside the confirmation
        // transaction, so ISSUED is the first state there is.
        ReferenceDataSource.register(ReferenceType.TICKET_STATUS, TicketStatus.class, Map.of(
                "ISSUED", SUCCEEDED,
                // Admitted at the gate. Still a good ticket: refunds and
                // reporting treat it as a completed sale.
                "VALIDATED", SUCCEEDED,
                // Never rested in — a transfer returns the ticket to ISSUED
                // under its new owner — but declared, so the picker offers it.
                "TRANSFERRED", SUCCEEDED,
                // A person is waiting on this one.
                "REFUND_PENDING", PENDING,
                "REFUNDED", CANCELLED,
                "CANCELLED", CANCELLED,
                "EXPIRED", FAILED
        ));

        ReferenceDataSource.register(ReferenceType.PAYOUT_STATUS, PayoutRequestStatus.class, Map.of(
                "PENDING", INITIAL,
                // The escrow is already debited here, so it is in flight rather
                // than merely blessed.
                "APPROVED", IN_PROGRESS,
                "PROCESSING", IN_PROGRESS,
                "COMPLETED", SUCCEEDED,
                "REJECTED", CANCELLED,
                "FAILED", FAILED,
                "CANCELLED", CANCELLED
        ));

        ReferenceDataSource.register(ReferenceType.PAYMENT_STATUS, TransactionStatus.class, Map.of(
                "PENDING", INITIAL,
                "PROCESSING", IN_PROGRESS,
                "RETRYING", IN_PROGRESS,
                "PENDING_VERIFICATION", IN_PROGRESS,
                "COMPLETED", SUCCEEDED,
                "FULFILLED", SUCCEEDED,
                "FAILED", FAILED,
                // A timeout is a failure with an unknown outcome, not a
                // cancellation — the money may still land.
                "TIMED_OUT", FAILED,
                "CANCELLED", CANCELLED,
                "REVERSED", CANCELLED
        ));
        ReferenceDataSource.registerExtra(ReferenceType.PAYMENT_STATUS,
                Map.of("ROLLED_BACK", CANCELLED));

        ReferenceDataSource.register(ReferenceType.REFUND_STATUS, RefundRequestStatus.class, Map.of(
                "PENDING", INITIAL,
                "APPROVED", IN_PROGRESS,
                "PROCESSING", IN_PROGRESS,
                "COMPLETED", SUCCEEDED,
                "REJECTED", CANCELLED,
                "FAILED", FAILED,
                "CANCELLED", CANCELLED
        ));

        ReferenceDataSource.register(ReferenceType.EVENT_STATUS, EventStatus.class, Map.of(
                "DRAFT", INITIAL,
                "PENDING_APPROVAL", PENDING,
                // Waiting on the ORGANIZER rather than the platform, but still
                // waiting on a person, which is what PENDING means.
                "CHANGES_REQUESTED", PENDING,
                "APPROVED", IN_PROGRESS,
                "PUBLISHED", SUCCEEDED,
                "COMPLETED", SUCCEEDED,
                "REJECTED", CANCELLED,
                "CANCELLED", CANCELLED
        ));

        // The five escrow states, and only those.
        ReferenceDataSource.register(ReferenceType.ESCROW_STATUS, EscrowStatus.class, Map.of(
                // Open and taking money. Nobody is being waited on.
                "ACTIVE", IN_PROGRESS,
                // A hold period counting down on its own, not a person deciding.
                "HOLD", IN_PROGRESS,
                // The hold elapsed and the platform is now waiting on the
                // organizer to ask for the money. That wait is on a human.
                "PAYOUT_ELIGIBLE", PENDING,
                // Also waiting on a human — but a platform reviewer, not the
                // organizer. Same semantic, and the status code is what tells
                // the two apart on screen.
                "SUSPENDED", PENDING,
                "CLOSED", SUCCEEDED
        ));

        ReferenceDataSource.register(ReferenceType.ORGANIZATION_STATUS, OrganizationStatus.class, Map.of(
                "DRAFT", INITIAL,
                "PENDING_REVIEW", PENDING,
                // Waiting on the applicant rather than the reviewer, but still a
                // person, which is what PENDING means.
                "CHANGES_REQUESTED", PENDING,
                "APPROVED", SUCCEEDED,
                "ACTIVE", SUCCEEDED,
                "REJECTED", CANCELLED,
                // Nothing failed — the platform stopped it by decision, and it
                // is reversible. Same bucket as any other deliberate stop.
                "SUSPENDED", CANCELLED,
                "INACTIVE", CANCELLED,
                // A live account with a countdown on it, NOT a queue waiting on
                // a person. The name is the exact trap this file exists for.
                "PENDING_DELETION", CANCELLED
        ));

        // The five reservation states. With the payment intent's, this is a purchase's only
        // durable state, so its semantics are what a resumed purchase workflow reads.
        ReferenceDataSource.register(ReferenceType.RESERVATION_STATUS, ReservationStatus.class, Map.of(
                // Where every reservation starts, and the only non-terminal one.
                "HELD", INITIAL,
                "CONFIRMED", SUCCEEDED,
                // The hold ran out. Nobody decided this; a clock did.
                "EXPIRED", FAILED,
                // Deliberately given up — buyer cancelled, or the system let go.
                "RELEASED", CANCELLED,
                // Payment resolved but the purchase could not complete. Distinct
                // from RELEASED because this one may hold someone's money.
                "FAILED", FAILED
        ));

        ReferenceDataSource.register(ReferenceType.CHARGEBACK_STATUS, ChargebackStatus.class, Map.of(
                "RECEIVED", INITIAL,
                "UNDER_REVIEW", IN_PROGRESS,
                // The platform conceded rather than contesting. Deliberate, but
                // the outcome is money gone — FAILED, not CANCELLED, because a
                // "lost money" report that omits concessions understates it.
                "ACCEPTED", FAILED,
                "DISPUTED", IN_PROGRESS,
                "WON", SUCCEEDED,
                "LOST", FAILED
        ));

        ReferenceDataSource.register(ReferenceType.VERIFICATION_DOCUMENT_STATUS, DocumentStatus.class, Map.of(
                // Waiting on a reviewer — a person, which is what PENDING means.
                "PENDING", PENDING,
                "APPROVED", SUCCEEDED,
                "REJECTED", CANCELLED,
                "EXPIRED", FAILED
        ));

        ReferenceDataSource.register(ReferenceType.TEAM_INVITATION_STATUS, InvitationStatus.class, Map.of(
                // Waiting on the invitee to act.
                "PENDING", PENDING,
                "ACCEPTED", SUCCEEDED,
                "DECLINED", CANCELLED,
                "EXPIRED", FAILED,
                // Withdrawn by the inviter. A decision, like DECLINED, just from
                // the other side.
                "REVOKED", CANCELLED
        ));
    }
}
