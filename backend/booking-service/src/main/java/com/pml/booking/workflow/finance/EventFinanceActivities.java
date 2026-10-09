package com.pml.booking.workflow.finance;

import io.temporal.activity.ActivityInterface;

/**
 * The escrow and commission steps of an event's finances. Each is idempotent.
 */
@ActivityInterface(namePrefix = "EventFinance")
public interface EventFinanceActivities {

    /** Opens the event's one escrow account; a second call finds it open. */
    void openEscrow(String eventId, String organizationId, long startsAtMillis);

    /** Marks every live ticket refundable at 100% for the reschedule window. */
    void openRescheduleWindow(String eventId, long previousStartsAtMillis, long newStartsAtMillis);

    /** Moves the escrow to HOLD with {@code holdUntil} seven days after completion; answers {@code holdUntil}. */
    long startHold(String eventId, long completedAtMillis);

    /** Recomputes {@code holdUntil} from the new date; answers it. */
    long rescheduleHold(String eventId, long newStartsAtMillis);

    int openDisputes(String eventId);

    void makePayoutEligible(String eventId);

    /** Pending commission becomes earned, once; answers how many records moved. */
    long recogniseCommission(String eventId);
}
