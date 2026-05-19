package com.pml.booking.dto;

/**
 * pawaPay Refund Callback Payload
 *
 * Business Intent: Represents the callback data sent by pawaPay when a mobile money
 * refund reaches a final status. This confirms that refund funds have been returned
 * to the customer's mobile money account.
 *
 * @param refundId Our unique refund ID
 * @param depositId The original deposit ID being refunded
 * @param status Final status: COMPLETED or FAILED
 * @param amount Amount refunded
 * @param currency Currency code
 * @param created Timestamp when refund was initiated
 * @param completedTimestamp Timestamp when refund completed
 * @param providerTransactionId Mobile money network's transaction reference
 * @param failureReason Details if the refund failed
 */
public record RefundCallbackPayload(
        String refundId,
        String depositId,
        String status,
        String amount,
        String currency,
        String created,
        String completedTimestamp,
        String providerTransactionId,
        FailureReason failureReason
) {}
