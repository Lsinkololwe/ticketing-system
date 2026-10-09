package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentOutcomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptRecorderImpl implements PaymentAttemptRecorder {

    /** The only mobile-money provider the platform calls. */
    private static final String PROVIDER = "PAWAPAY";

    private final PaymentAttemptRepository attempts;

    /** Every timestamp comes from here, never from the wall clock. */
    private final Clock clock;

    @Override
    public Mono<PaymentAttempt> beforeCollect(PaymentIntent intent) {
        return attempts.findByDepositId(intent.getDepositId())
                .switchIfEmpty(Mono.defer(() -> attempts.save(PaymentAttempt.create(
                                intent.getDepositId(),
                                intent.getId(),
                                intent.getReservationId(),
                                intent.getEventId(),
                                null,
                                null,
                                intent.getUserId(),
                                intent.getAmount(),
                                intent.getCurrency(),
                                intent.getProvider() != null ? intent.getProvider().name() : "UNKNOWN",
                                intent.getPhoneNumber(),
                                clock.instant()))
                        .doOnSuccess(attempt -> log.info("Payment attempt {} staged before calling the provider for deposit {}",
                                attempt.getId(), intent.getDepositId()))
                        // Two concurrent submissions for the same intent can both reach the save;
                        // the unique index on depositId refuses the second, and losing that race
                        // means the row exists already.
                        .onErrorResume(DuplicateKeyException.class,
                                e -> attempts.findByDepositId(intent.getDepositId()))));
    }

    /**
     * Applies the same three-way read {@link PaymentServiceImpl#recordSubmission} uses: accepted is
     * evidence of submission, a verdict of {@code FAILED} (a real answer, not just a failed HTTP
     * call) is evidence of a decline, and anything else — a timeout, a 5xx, an open breaker — is no
     * answer at all and leaves the row exactly as {@link #beforeCollect} staged it (nothing
     * here infers a decline from an exception).
     */
    @Override
    public Mono<PaymentAttempt> afterCollect(String depositId, PaymentResult result) {
        return attempts.findByDepositId(depositId)
                .flatMap(attempt -> {
                    Instant now = clock.instant();
                    attempt.markApiResponded(result.status() != null ? result.status().name() : "UNKNOWN", now);
                    // Only the row's first answer moves it off CREATED — a poll or a retried
                    // submission that lands here after the provider already answered changes
                    // nothing, so this stays idempotent under at-least-once activity execution.
                    if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
                        if (result.isSuccessOrPending()) {
                            attempt.transitionTo(PaymentAttemptStatus.PENDING_APPROVAL, now);
                        } else if (PaymentOutcomeService.verdictOf(result) == PaymentOutcomeService.Verdict.FAILED) {
                            attempt.markRejected(result.errorCode(), result.errorMessage(), now);
                        }
                    }
                    return attempts.save(attempt);
                });
    }

    @Override
    public Mono<PaymentAttempt> beforeCall(ProviderCall call) {
        return attempts.findByProviderReference(call.providerReference())
                .switchIfEmpty(Mono.defer(() -> attempts.save(PaymentAttempt.forProviderCall(
                                call.type(), call.providerReference(), call.amount(), call.currency(), PROVIDER,
                                call.eventId(), call.organizationId(), call.refundRequestId(), call.payoutRequestId(),
                                call.bankAccountId(), clock.instant()))
                        .doOnSuccess(attempt -> log.info("{} attempt {} staged before calling the provider with reference {}",
                                call.type(), attempt.getId(), call.providerReference()))
                        // Concurrent callers for one reference both reach the save; the unique
                        // index refuses the second, which then reads the row the first wrote.
                        .onErrorResume(DuplicateKeyException.class,
                                e -> attempts.findByProviderReference(call.providerReference()))));
    }

    @Override
    public Mono<PaymentAttempt> afterCall(String providerReference, CallOutcome outcome, String failureCode,
                                          String failureMessage) {
        if (outcome == CallOutcome.NO_ANSWER) {
            return attempts.findByProviderReference(providerReference);
        }
        return attempts.findByProviderReference(providerReference)
                .flatMap(attempt -> {
                    if (attempt.getStatus() != PaymentAttemptStatus.CREATED) {
                        return Mono.just(attempt);
                    }
                    Instant now = clock.instant();
                    if (outcome == CallOutcome.ACCEPTED) {
                        attempt.markApiResponded("ACCEPTED", now);
                        attempt.transitionTo(PaymentAttemptStatus.PROCESSING, now);
                    } else {
                        attempt.markApiResponded("REJECTED", now);
                        attempt.markRejected(failureCode, failureMessage, now);
                    }
                    return attempts.save(attempt);
                });
    }
}
