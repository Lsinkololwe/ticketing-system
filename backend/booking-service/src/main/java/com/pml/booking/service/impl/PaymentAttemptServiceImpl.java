package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Implementation of {@link PaymentAttemptService}: reads, operator notes and review status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private static final Set<PaymentAttemptStatus> SUCCESSFUL_STATUSES = Set.of(
            PaymentAttemptStatus.CONFIRMED,
            PaymentAttemptStatus.COMPLETED
    );

    private final PaymentAttemptRepository paymentAttemptRepository;

    /** The injected platform clock, so every timestamp below is freezable. */
    private final java.time.Clock clock;

    @Override
    public Mono<PaymentAttempt> findById(String id) {
        return paymentAttemptRepository.findById(id);
    }

    @Override
    public Mono<PaymentAttempt> findByDepositId(String depositId) {
        return paymentAttemptRepository.findByDepositId(depositId);
    }

    @Override
    public Flux<PaymentAttempt> findByPaymentIntentId(String paymentIntentId) {
        return paymentAttemptRepository.findByPaymentIntentIdOrderByCreatedAtDesc(paymentIntentId);
    }

    @Override
    public Mono<PaymentAttempt> findByAttemptNumber(String attemptNumber) {
        return paymentAttemptRepository.findByAttemptNumber(attemptNumber);
    }

    @Override
    public Flux<PaymentAttempt> findByReservationId(String reservationId) {
        return paymentAttemptRepository.findByReservationId(reservationId);
    }

    @Override
    public Mono<PaymentAttempt> findLatestByReservationId(String reservationId) {
        return paymentAttemptRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservationId);
    }

    @Override
    public Mono<PaymentAttempt> findSuccessfulByReservationId(String reservationId) {
        return paymentAttemptRepository.findByReservationIdAndStatusIn(reservationId, SUCCESSFUL_STATUSES);
    }

    @Override
    public Flux<PaymentAttempt> findByBuyerId(String buyerId) {
        return paymentAttemptRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    @Override
    public Flux<PaymentAttempt> findByEventId(String eventId) {
        return paymentAttemptRepository.findByEventId(eventId);
    }

    @Override
    public Flux<PaymentAttempt> findByStatus(PaymentAttemptStatus status) {
        return paymentAttemptRepository.findByStatus(status);
    }

    @Override
    public Flux<PaymentAttempt> findConfirmedUnfulfilled() {
        return paymentAttemptRepository.findByStatusAndFulfilled(PaymentAttemptStatus.CONFIRMED, false);
    }

    @Override
    public Mono<PaymentAttempt> addNote(String depositId, String author, String note) {
        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    attempt.addNote(author, note, clock.instant());
                    return paymentAttemptRepository.save(attempt);
                });
    }

    @Override
    public Mono<PaymentAttempt> setReviewStatus(String depositId, String reviewStatus, String reviewedBy, String notes) {
        return findByDepositId(depositId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment attempt not found: " + depositId)))
                .flatMap(attempt -> {
                    attempt.setReviewStatus(reviewStatus);
                    attempt.setReviewedBy(reviewedBy);
                    attempt.setReviewedAt(clock.instant());
                    attempt.setReviewNotes(notes);
                    attempt.addNote(reviewedBy, "Review status set to: " + reviewStatus + " - " + notes, clock.instant());
                    return paymentAttemptRepository.save(attempt);
                });
    }

    @Override
    public Mono<Long> countByStatus(PaymentAttemptStatus status) {
        return paymentAttemptRepository.countByStatus(status);
    }

    @Override
    public Mono<Long> countByEventIdAndStatus(String eventId, PaymentAttemptStatus status) {
        return paymentAttemptRepository.countByEventIdAndStatus(eventId, status);
    }

    @Override
    public Mono<Boolean> hasSuccessfulPayment(String reservationId) {
        return paymentAttemptRepository.findByReservationIdAndStatusIn(reservationId, SUCCESSFUL_STATUSES)
                .hasElement();
    }
}
