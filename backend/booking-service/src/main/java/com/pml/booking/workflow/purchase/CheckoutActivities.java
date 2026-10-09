package com.pml.booking.workflow.purchase;

import com.pml.booking.workflow.purchase.PurchaseWorkflow.PaymentView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Progress;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReservationView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReserveCommand;
import io.temporal.activity.ActivityInterface;

/**
 * Every step a checkout takes outside the workflow. Each is idempotent by reservation.
 */
@ActivityInterface(namePrefix = "Checkout")
public interface CheckoutActivities {

    /** Takes the seats and writes the quoted HELD reservation under this id; a repeat returns it. */
    ReservationView hold(String reservationId, ReserveCommand command);

    /** Compensation for a hold that did not complete: returns any seats taken under this id. */
    void abandonHold(String reservationId, ReserveCommand command);

    /** The reservation as stored, or null when there is none. */
    ReservationView current(String reservationId);

    /** Attaches the intent to the reservation and submits it to the provider, once. */
    PaymentView startPayment(String reservationId, String paymentIntentId);

    /** Asks the provider about the payment, applies a verified answer, and drives the purchase it implies. */
    Progress verify(String reservationId);

    /** Ends a HELD reservation by the named action and returns its seats; a second call changes nothing. */
    ReservationView release(String reservationId, String action);

    /** A payment pending past its budget goes to the transaction-recovery queue; the money may still arrive. */
    void escalatePending(String reservationId);

    /**
     * Late money (ROADMAP D-22): starts the automatic full refund of this reservation's payment; a
     * repeat reaches the refund already started and starts nothing.
     */
    void startLateRefund(String reservationId);
}
