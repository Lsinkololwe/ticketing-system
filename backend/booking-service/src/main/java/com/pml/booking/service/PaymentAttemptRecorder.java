package com.pml.booking.service;

import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.domain.enums.PaymentAttemptType;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Records the collect provider call as a {@code booking_payment_attempts} row before
 * it is made, and applies the provider's immediate answer to that row after.
 *
 * <p>The row is evidence for a crash mid-call; it never decides a payment intent's own status —
 * {@link PaymentOutcomeService} still owns that, verified against the provider's status API.
 */
public interface PaymentAttemptRecorder {

    /**
     * Idempotent on {@code intent.getDepositId()} — a retried submission for the same intent finds
     * the row already staged rather than writing a second one.
     */
    Mono<PaymentAttempt> beforeCollect(PaymentIntent intent);

    /** Applies the provider's immediate response to the row {@link #beforeCollect} staged. */
    Mono<PaymentAttempt> afterCollect(String depositId, PaymentResult result);

    /**
     * A refund, payout or verification call about to be made. {@code providerReference} is the id sent
     * to the provider; the row is found by it on a retry.
     */
    record ProviderCall(PaymentAttemptType type,
                        String providerReference,
                        BigDecimal amount,
                        String currency,
                        String eventId,
                        String organizationId,
                        String refundRequestId,
                        String payoutRequestId,
                        String bankAccountId) {
    }

    /** How the provider answered a call. {@code NO_ANSWER} covers timeouts, 5xx and an open circuit breaker. */
    enum CallOutcome { ACCEPTED, REFUSED, NO_ANSWER }

    /** Writes the row for a call before it is made; a second call with the same reference returns the first row. */
    Mono<PaymentAttempt> beforeCall(ProviderCall call);

    /**
     * Applies the provider's answer to the row {@link #beforeCall} wrote. Only the row's first answer
     * moves it off {@code CREATED}; {@code NO_ANSWER} leaves it there, because no answer is not a refusal.
     */
    Mono<PaymentAttempt> afterCall(String providerReference, CallOutcome outcome, String failureCode, String failureMessage);
}
