package com.pml.booking.workflow.payout;

import com.pml.booking.workflow.payout.PayoutWorkflow.Answer;
import io.temporal.activity.ActivityInterface;

/**
 * The transfer itself, through the mobile-money provider.
 *
 * <p>Runs on {@code booking-provider}, outside any transaction. The provider deduplicates on the
 * payout id stored at {@code beginSettlement}, so a retried initiation cannot send money twice.
 */
@ActivityInterface(namePrefix = "PayoutProvider")
public interface PayoutProviderActivities {

    /** Asks the provider to send the settled amount; bad details fail non-retryably as {@code BAD_ACCOUNT_DETAILS}. */
    void initiate(String payoutRequestId);

    /** What the provider's status API says now. */
    Answer status(String payoutRequestId);
}
