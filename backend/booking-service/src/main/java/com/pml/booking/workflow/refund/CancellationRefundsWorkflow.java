package com.pml.booking.workflow.refund;

import io.temporal.activity.ActivityInterface;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * An event cancellation refunds every live ticket, resumably.
 *
 * <p>Addressed as {@code cancellation-refunds/{eventId}}. Tickets are taken two hundred at a time in
 * id order; each gets its own refund execution, and the batch continues as new with the cursor, so a
 * restart resumes after the last completed batch rather than refunding anyone twice.
 */
@WorkflowInterface
public interface CancellationRefundsWorkflow {

    @WorkflowMethod
    Summary run(Start start);

    @QueryMethod
    Summary progress();

    record Start(String eventId, String reason, String afterTicketId, int refundsStarted) {
    }

    record Batch(List<String> ticketIds, String lastTicketId) {
    }

    record Summary(String eventId, int refundsStarted, boolean escrowClosed) {
    }

    @ActivityInterface(namePrefix = "Cancellation")
    interface Activities {

        /** The next live tickets of the event after the cursor, in id order. */
        Batch nextBatch(String eventId, String afterTicketId, int limit);

        /** Closes the escrow when it has reached zero; answers whether it did. */
        boolean closeEscrow(String eventId);
    }
}
