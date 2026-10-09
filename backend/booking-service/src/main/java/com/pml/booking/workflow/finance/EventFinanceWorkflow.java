package com.pml.booking.workflow.finance;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One event's money, from on-sale to payout eligibility.
 *
 * <p>Addressed as {@code event-finance/{eventId}}. Catalog's facts arrive through Service Bus and
 * become signals, each carrying its envelope id so a redelivered fact changes nothing. The workflow
 * owns the hold: seven days from completion, recomputed on reschedule, extended while disputes are
 * open, and followed by payout eligibility and commission recognition exactly once.
 */
@WorkflowInterface
public interface EventFinanceWorkflow {

    @WorkflowMethod
    void run(Start start);

    @SignalMethod
    void published(Published fact);

    @SignalMethod
    void completed(Completed fact);

    @SignalMethod
    void cancelled(Cancelled fact);

    @SignalMethod
    void rescheduled(Rescheduled fact);

    /** A chargeback against this event resolved; eligibility is re-checked now. */
    @SignalMethod
    void disputeClosed(String chargebackId);

    @QueryMethod
    Stage stage();

    record Start(String eventId) {
    }

    record Published(String envelopeId, String organizationId, long startsAtMillis) {
    }

    record Completed(String envelopeId, long completedAtMillis) {
    }

    record Cancelled(String envelopeId, String reason) {
    }

    record Rescheduled(String envelopeId, long previousStartsAtMillis, long newStartsAtMillis) {
    }

    enum Stage { AWAITING_FACTS, SELLING, HOLDING, AWAITING_DISPUTES, ELIGIBLE, REFUNDING, CLOSED }
}
