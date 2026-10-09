package com.pml.booking.service;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads over payment attempts, and the notes and review status operators attach to them.
 *
 * <p>The lifecycle of a payment — the provider call, the verified answer, expiry and retry — is the
 * reservation's {@code PurchaseWorkflow}; this service never moves a payment's
 * status.
 *
 * @see PaymentAttempt
 * @see PaymentAttemptStatus
 */
public interface PaymentAttemptService {

    Mono<PaymentAttempt> findById(String id);

    /** @param depositId the id sent to PawaPay */
    Mono<PaymentAttempt> findByDepositId(String depositId);

    /** Every attempt made against one payment intent, newest first. */
    Flux<PaymentAttempt> findByPaymentIntentId(String paymentIntentId);

    /** @param attemptNumber format {@code PAY-{YYYYMMDD}-{XXXXX}} */
    Mono<PaymentAttempt> findByAttemptNumber(String attemptNumber);

    /** All attempts for a reservation. */
    Flux<PaymentAttempt> findByReservationId(String reservationId);

    Mono<PaymentAttempt> findLatestByReservationId(String reservationId);

    /** The successful attempt for a reservation, CONFIRMED or COMPLETED. */
    Mono<PaymentAttempt> findSuccessfulByReservationId(String reservationId);

    /** A buyer's attempts, newest first. */
    Flux<PaymentAttempt> findByBuyerId(String buyerId);

    Flux<PaymentAttempt> findByEventId(String eventId);

    Flux<PaymentAttempt> findByStatus(PaymentAttemptStatus status);

    /** Confirmed attempts with no fulfilment recorded, for the recovery queue. */
    Flux<PaymentAttempt> findConfirmedUnfulfilled();

    /**
     * @param author the authenticated operator
     */
    Mono<PaymentAttempt> addNote(String depositId, String author, String note);

    /**
     * @param reviewStatus PENDING_REVIEW, UNDER_INVESTIGATION or RESOLVED
     * @param reviewedBy   the authenticated operator
     */
    Mono<PaymentAttempt> setReviewStatus(String depositId, String reviewStatus, String reviewedBy, String notes);

    Mono<Long> countByStatus(PaymentAttemptStatus status);

    Mono<Long> countByEventIdAndStatus(String eventId, PaymentAttemptStatus status);

    Mono<Boolean> hasSuccessfulPayment(String reservationId);
}
