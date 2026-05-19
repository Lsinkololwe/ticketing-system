package com.pml.booking.service;

import com.pml.booking.domain.model.PaymentIntent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Payment Service Interface
 *
 * Manages payment intents for ticket purchases via pawaPay mobile money.
 * Handles the complete payment lifecycle from initiation to completion.
 */
public interface PaymentService {

    /**
     * Create a new payment intent for a ticket purchase.
     *
     * @param ticketId     The ticket being purchased
     * @param eventId      The event ID
     * @param userId       The user making the purchase
     * @param amount       The payment amount
     * @param currency     The currency (default: ZMW)
     * @param phoneNumber  User's mobile money phone number
     * @return Created payment intent
     */
    Mono<PaymentIntent> createPaymentIntent(
            String ticketId,
            String eventId,
            String userId,
            BigDecimal amount,
            String currency,
            String phoneNumber
    );

    /**
     * Initiate payment processing with pawaPay.
     * Sends deposit request to mobile money provider.
     *
     * @param paymentIntentId The payment intent to process
     * @return Updated payment intent with processing status
     */
    Mono<PaymentIntent> initiatePayment(String paymentIntentId);

    /**
     * Handle webhook callback from pawaPay.
     * Updates payment status based on provider response.
     *
     * @param depositId The pawaPay deposit ID
     * @param status    The final status (COMPLETED, FAILED)
     * @param providerTransactionId Provider's transaction reference
     * @param failureCode Optional failure code
     * @param failureMessage Optional failure message
     * @return Updated payment intent
     */
    Mono<PaymentIntent> handlePaymentCallback(
            String depositId,
            String status,
            String providerTransactionId,
            String failureCode,
            String failureMessage
    );

    /**
     * Check payment status by polling pawaPay API.
     * Used as fallback when webhook is not received.
     *
     * @param paymentIntentId The payment intent to check
     * @return Updated payment intent
     */
    Mono<PaymentIntent> checkPaymentStatus(String paymentIntentId);

    /**
     * Cancel a pending payment intent.
     *
     * @param paymentIntentId The payment intent to cancel
     * @return Cancelled payment intent
     */
    Mono<PaymentIntent> cancelPayment(String paymentIntentId);

    /**
     * Find payment intent by ID.
     */
    Mono<PaymentIntent> findById(String id);

    /**
     * Find payment intent by ticket ID.
     */
    Mono<PaymentIntent> findByTicketId(String ticketId);

    /**
     * Find payment intent by idempotency key.
     */
    Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find all payment intents for a user.
     */
    Flux<PaymentIntent> findByUserId(String userId);

    /**
     * Find all payment intents for an event.
     */
    Flux<PaymentIntent> findByEventId(String eventId);

    /**
     * Find expired payment intents for cleanup.
     */
    Flux<PaymentIntent> findExpiredPayments();

    /**
     * Process expired payments (mark as expired).
     */
    Mono<Long> processExpiredPayments();
}
