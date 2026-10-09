package com.pml.booking.service.impl;

import com.pml.shared.constants.PlatformTime;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentProvider;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.MobileMoneyRequest;
import com.pml.booking.infrastructure.gateway.model.MobileNetwork;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PaymentOutcomeService.Verdict;
import com.pml.booking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Payment intents and their submission to a mobile-money provider.
 *
 * <h2>Where outcomes are decided</h2>
 * This service creates intents and submits them. It does not decide what happened to the money:
 * every terminal transition goes through {@link PaymentOutcomeService}, which verifies against the
 * provider's status API and drives the purchase. Keeping that in one class is what makes a callback,
 * a poll and an activity retry unable to disagree about the same deposit.
 *
 * <h2>The deposit id is committed before the provider is called</h2>
 * An intent carries its {@code depositId} from creation, and the provider receives exactly that id.
 * The provider call never runs inside a transaction: a transaction waiting on a
 * network round trip holds MongoDB locks for its duration, and a rollback cannot recall a charge.
 *
 * @see PaymentOutcomeService
 * @see MobileMoneyGateway
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final MobileMoneyGatewayFactory gatewayFactory;
    private final PaymentOutcomeService outcomes;
    private final PaymentAttemptRecorder attempts;

    /** The injected platform clock, so every timestamp below is freezable. */
    private final java.time.Clock clock;

    @Value("${booking.payment.timeout:PT15M}")
    private Duration paymentTimeout;

    @Override
    public Mono<PaymentIntent> createPaymentIntent(
            String reservationId,
            String eventId,
            String userId,
            BigDecimal amount,
            String currency,
            String phoneNumber
    ) {
        log.info("Creating payment intent for reservation: {}, amount: {} {}", reservationId, amount, currency);

        // Keyed on the reservation alone, with no timestamp, so a retry reaches the same intent:
        // one reservation may be charged once.
        String idempotencyKey = String.format("%s_%s", userId, reservationId);
        String transactionRef = PaymentIntent.generateTransactionRef(PlatformTime.dateAt(clock.instant()));

        MobileNetwork network = MobileNetwork.fromPhoneNumber(phoneNumber);
        PaymentProvider provider = mapNetworkToProvider(network);
        String correspondent = mapNetworkToCorrespondent(network);

        PaymentIntent paymentIntent = PaymentIntent.builder()
                .idempotencyKey(idempotencyKey)
                .transactionRef(transactionRef)
                .depositId(UUID.randomUUID().toString())
                .reservationId(reservationId)
                .eventId(eventId)
                .userId(userId)
                .amount(amount)
                .currency(currency != null ? currency : "ZMW")
                .provider(provider)
                .correspondent(correspondent)
                .phoneNumber(phoneNumber)
                .status(PaymentStatus.PENDING)
                .expiresAt(clock.instant().plus(paymentTimeout))
                .build();

        // The existing intent is returned rather than a second one written. A buyer tapping "pay"
        // twice must see the payment in flight, not a unique-index failure.
        return paymentIntentRepository.findByReservationId(reservationId)
                .doOnNext(existing -> log.info(
                        "Reservation {} already has payment intent {} ({}) — reusing it",
                        reservationId, existing.getTransactionRef(), existing.getStatus()))
                .switchIfEmpty(Mono.defer(() -> paymentIntentRepository.save(paymentIntent)
                        .doOnSuccess(pi -> log.info("Payment intent created: {}", pi.getTransactionRef()))
                        // Two concurrent taps can both reach the save. The index refuses the
                        // second, and losing that race means the intent exists.
                        .onErrorResume(DuplicateKeyException.class,
                                e -> paymentIntentRepository.findByReservationId(reservationId))));
    }

    @Override
    public Mono<PaymentIntent> initiatePayment(String paymentIntentId) {
        log.info("Initiating payment for intent: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(this::withDepositId)
                .flatMap(intent -> {
                    if (intent.getStatus() != PaymentStatus.PENDING) {
                        return Mono.error(new IllegalStateException(
                                "Cannot initiate payment. Current status: " + intent.getStatus()));
                    }
                    if (intent.isExpired(clock.instant())) {
                        // No state change here: an expired intent is settled by the expiry pass,
                        // which asks the provider first in case an earlier submission landed.
                        return Mono.error(new IllegalStateException("Payment intent expired"));
                    }

                    MobileMoneyRequest request = MobileMoneyRequest.builder()
                            .correlationId(intent.getDepositId())
                            .phoneNumber(intent.getPhoneNumber())
                            .amount(intent.getAmount())
                            .currency(intent.getCurrency())
                            .description("Ticket Purchase: " + intent.getReservationId())
                            .build();

                    // A booking_payment_attempts row is staged before the provider is
                    // ever called, so a crash mid-call leaves evidence; the outcome is applied to
                    // that same row once the provider answers.
                    return attempts.beforeCollect(intent)
                            .then(gatewayFactory.getGatewayForPhone(intent.getPhoneNumber())
                                    .flatMap(gateway -> gateway.initiatePayment(request)))
                            .flatMap(result -> attempts.afterCollect(intent.getDepositId(), result)
                                    .then(recordSubmission(intent, result)));
                });
    }

    /**
     * Gives an intent its deposit id if it has none, before anything is sent. Conditional on the
     * field still being null, so two concurrent submissions settle on one id.
     */
    private Mono<PaymentIntent> withDepositId(PaymentIntent intent) {
        if (intent.getDepositId() != null) {
            return Mono.just(intent);
        }
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(intent.getId()).and("depositId").is(null)),
                        new Update().set("depositId", UUID.randomUUID().toString()).inc("version", 1),
                        PaymentIntent.class)
                .then(paymentIntentRepository.findById(intent.getId()));
    }

    /**
     * Records what the provider said when the deposit was submitted.
     *
     * <ul>
     *   <li>Accepted: {@code PENDING → PROCESSING} by compare-and-set, so a callback that arrived
     *       first and already settled the intent is never overwritten.</li>
     *   <li>Declined by the provider: a real answer, applied through the outcome service, which
     *       releases the reservation.</li>
     *   <li>Anything else — a timeout, a 5xx, an open breaker — is no answer. The intent stays
     *       {@code PENDING} with its deposit id, and resubmitting reuses that id, which the
     *       provider deduplicates.</li>
     * </ul>
     */
    private Mono<PaymentIntent> recordSubmission(PaymentIntent intent, PaymentResult result) {
        if (result.isSuccessOrPending()) {
            return mongoTemplate.updateFirst(
                            Query.query(Criteria.where("_id").is(intent.getId())
                                    .and("status").is(PaymentStatus.PENDING)),
                            new Update()
                                    .set("status", PaymentStatus.PROCESSING)
                                    .set("providerTransactionId", intent.getDepositId())
                                    .set("updatedAt", clock.instant())
                                    .inc("version", 1),
                            PaymentIntent.class)
                    .then(paymentIntentRepository.findById(intent.getId()))
                    .doOnNext(fresh -> log.info("Payment {} submitted as deposit {} via {} — now {}",
                            fresh.getId(), fresh.getDepositId(), result.providerId(), fresh.getStatus()));
        }

        if (PaymentOutcomeService.verdictOf(result) == Verdict.FAILED) {
            return outcomes.apply(intent, Verdict.FAILED, result.providerTransactionId(),
                    result.errorCode(), result.errorMessage());
        }

        log.warn("Payment {} submission outcome unknown ({}: {}) — left PENDING for the callback or poll",
                intent.getId(), result.errorCode(), result.errorMessage());
        return Mono.just(intent);
    }

    @Override
    public Mono<PaymentIntent> checkPaymentStatus(String paymentIntentId) {
        log.debug("Checking payment status for: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(intent -> intent.getDepositId() == null
                        ? Mono.just(intent)
                        : outcomes.verifyAndApply(intent.getDepositId()));
    }

    @Override
    public Mono<PaymentIntent> cancelPayment(String paymentIntentId) {
        log.info("Cancelling payment: {}", paymentIntentId);

        return paymentIntentRepository.findById(paymentIntentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + paymentIntentId)))
                .flatMap(paymentIntent -> {
                    if (paymentIntent.isTerminal()) {
                        return Mono.error(new IllegalStateException(
                                "Cannot cancel payment in terminal state: " + paymentIntent.getStatus()));
                    }
                    if (paymentIntent.getStatus() == PaymentStatus.PROCESSING) {
                        // Submitted to the provider: the buyer may approve the prompt after this
                        // call returns. Only the provider's answer can close a submitted payment.
                        return Mono.error(new IllegalStateException(
                                "PAYMENT_IN_FLIGHT: payment " + paymentIntentId + " has been submitted to the provider"));
                    }
                    return mongoTemplate.updateFirst(
                                    Query.query(Criteria.where("_id").is(paymentIntentId)
                                            .and("status").is(PaymentStatus.PENDING)),
                                    new Update()
                                            .set("status", PaymentStatus.CANCELLED)
                                            .set("processedAt", clock.instant())
                                            .set("failureReason", "Cancelled by user")
                                            .inc("version", 1),
                                    PaymentIntent.class)
                            .then(paymentIntentRepository.findById(paymentIntentId))
                            .doOnNext(pi -> log.info("Payment {} is {}", pi.getTransactionRef(), pi.getStatus()));
                });
    }

    @Override
    public Mono<PaymentIntent> findById(String id) {
        return paymentIntentRepository.findById(id);
    }

    @Override
    public Mono<PaymentIntent> findByReservationId(String reservationId) {
        return paymentIntentRepository.findByReservationId(reservationId);
    }

    @Override
    public Mono<PaymentIntent> findByIdempotencyKey(String idempotencyKey) {
        return paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Flux<PaymentIntent> findByUserId(String userId) {
        return paymentIntentRepository.findByUserId(userId);
    }

    @Override
    public Flux<PaymentIntent> findByEventId(String eventId) {
        return paymentIntentRepository.findByEventId(eventId);
    }

    private PaymentProvider mapNetworkToProvider(MobileNetwork network) {
        if (network == null) return PaymentProvider.PAWAPAY;
        return switch (network) {
            case MTN -> PaymentProvider.MTN_MOMO_ZMB;
            case AIRTEL -> PaymentProvider.AIRTEL_ZMB;
            case ZAMTEL -> PaymentProvider.ZAMTEL_ZMB;
        };
    }

    /** The provider-specific correspondent code a payment gateway expects. */
    private String mapNetworkToCorrespondent(MobileNetwork network) {
        if (network == null) return "UNKNOWN";
        return switch (network) {
            case MTN -> "MTN_MOMO_ZMB";
            case AIRTEL -> "AIRTEL_ZMB";
            case ZAMTEL -> "ZAMTEL_ZMB";
        };
    }
}
