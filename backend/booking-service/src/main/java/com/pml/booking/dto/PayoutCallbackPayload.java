package com.pml.booking.dto;

/**
 * pawaPay Payout Callback Payload
 *
 * Business Intent: Represents the callback data sent by pawaPay when a mobile money
 * payout (organizer disbursement) reaches a final status. This confirms that event
 * proceeds have been transferred to the organizer's mobile money account.
 *
 * @param payoutId Our unique payout ID
 * @param status Final status: COMPLETED or FAILED
 * @param amount Amount paid out
 * @param currency Currency code
 * @param recipient Organizer's mobile money account details
 * @param created Timestamp when payout was initiated
 * @param completedTimestamp Timestamp when payout completed
 * @param providerTransactionId Mobile money network's transaction reference
 * @param failureReason Details if the payout failed
 */
public record PayoutCallbackPayload(
        String payoutId,
        String status,
        String amount,
        String currency,
        RecipientDetails recipient,
        String created,
        String completedTimestamp,
        String providerTransactionId,
        FailureReason failureReason
) {}
