package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * The outcome of asking for a payment prompt — never the outcome of the payment.
 *
 * <p>{@code success} here means the provider accepted the request and the
 * buyer's handset should be ringing. It does not mean money moved. The
 * distinction is the reason this type exists instead of returning tickets: a
 * response that carried tickets would be claiming an outcome that is still
 * minutes away and fails roughly one time in six.
 */
public record PaymentInitiationResponse(
        String paymentIntentId,
        String transactionRef,
        String paymentStatus,
        String reservationId
) {

    /** The prompt is out. Watch the reservation for the actual outcome. */
    public static PaymentInitiationResponse pending(String paymentIntentId,
                                                    String transactionRef,
                                                    String paymentStatus,
                                                    String reservationId) {
        return new PaymentInitiationResponse(
                paymentIntentId, transactionRef, paymentStatus, reservationId);
    }

    public static PaymentInitiationResponse error(String message) {
        return new PaymentInitiationResponse(null, null, null, null);
    }
}
