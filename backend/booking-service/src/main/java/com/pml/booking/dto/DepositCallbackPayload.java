package com.pml.booking.dto;

/**
 * pawaPay Deposit Callback Payload
 *
 * Business Intent: Represents the callback data sent by pawaPay when a mobile money
 * deposit (ticket payment) reaches a final status. This is how we learn that a customer
 * has confirmed or rejected the payment on their phone.
 *
 * @param depositId Our unique deposit ID sent in the original request
 * @param status Final status: COMPLETED (payment successful) or FAILED
 * @param requestedAmount The amount we requested
 * @param amount The actual amount transferred (may differ due to fees)
 * @param currency Currency code (ZMW for Zambian Kwacha)
 * @param country Country code (ZMB for Zambia)
 * @param payer Customer's mobile money account details
 * @param customerMessage The message shown to customer during payment
 * @param created Timestamp when the deposit was created
 * @param providerTransactionId The mobile money network's transaction reference
 * @param failureReason Details if the payment failed
 */
public record DepositCallbackPayload(
        String depositId,
        String status,
        String requestedAmount,
        String amount,
        String currency,
        String country,
        PayerDetails payer,
        String customerMessage,
        String created,
        String providerTransactionId,
        FailureReason failureReason
) {}
