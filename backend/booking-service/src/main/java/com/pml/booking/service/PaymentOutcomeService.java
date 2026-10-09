package com.pml.booking.service;

import com.mongodb.MongoException;
import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.exception.ReservationExpiredException;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.impl.PurchaseServiceImpl;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The only path by which a payment intent reaches a terminal outcome.
 *
 * <h2>Why callbacks, polls and operators converge here</h2>
 * A deposit becomes a ticket only if four hops hold together: the provider receives an id the
 * platform has stored, the callback naming that id finds the intent, a terminal status is written
 * exactly once, and that write is followed by confirmation. All four live in this class or are
 * called from it, so no caller that knows only part of the chain can skip one.
 *
 * <h2>The steps</h2>
 * <ol>
 *   <li>The intent carries a {@code depositId} minted when it is created, and that id is what the
 *       provider receives — so a callback names something the platform can find.</li>
 *   <li>A callback is evidence, not an outcome. {@link #verifyAndApply} asks the provider's status
 *       API and acts on that answer, so a forged or stale callback moves nothing.</li>
 *   <li>{@link #apply} is a compare-and-set from a non-terminal status, and the {@code booking}
 *       outbox envelope is staged in the same transaction — the transition and its announcement
 *       commit together. A losing writer changes nothing.</li>
 *   <li>Only the writer that made the transition drives the purchase forward: confirmation on
 *       success, release on failure. Duplicated callbacks and polls therefore produce one set of
 *       tickets. A confirmation that cannot finish leaves the reservation {@code HELD} with a
 *       {@code SUCCEEDED} intent, which the purchase's workflow resolves through {@link #resume}.</li>
 * </ol>
 *
 * <h2>Silence is never a failure</h2>
 * A timeout, a 5xx or an open circuit breaker is built by the gateway adapter without a provider
 * reference, and {@link #verdictOf} reads that as {@link Verdict#PENDING}. No code
 * path infers {@code FAILED} from an exception, because the money may still move.
 */
@Slf4j
@Service
public class PaymentOutcomeService {

    /** What the provider's own records say about a deposit. */
    public enum Verdict { SUCCEEDED, FAILED, PENDING }

    /** A callback or poll named a deposit no intent carries. Kept as a signal, not swallowed. */
    public static final class UnknownDeposit extends RuntimeException {
        public UnknownDeposit(String depositId) {
            super("No payment intent carries depositId " + depositId);
        }
    }

    private static final List<PaymentStatus> SETTLED = List.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.REFUNDED);

    private final PaymentIntentRepository intents;
    private final ReactiveMongoTemplate mongoTemplate;
    private final TransactionalOperator transactionalOperator;
    private final Outbox outbox;
    private final MobileMoneyGatewayFactory gateways;
    private final PurchaseService purchases;
    private final PurchaseEscalationService escalations;
    private final Clock clock;

    public PaymentOutcomeService(PaymentIntentRepository intents,
                                 ReactiveMongoTemplate mongoTemplate,
                                 TransactionalOperator transactionalOperator,
                                 Outbox outbox,
                                 MobileMoneyGatewayFactory gateways,
                                 PurchaseService purchases,
                                 PurchaseEscalationService escalations,
                                 Clock clock) {
        this.intents = intents;
        this.mongoTemplate = mongoTemplate;
        this.transactionalOperator = transactionalOperator;
        this.outbox = outbox;
        this.gateways = gateways;
        this.purchases = purchases;
        this.escalations = escalations;
        this.clock = clock;
    }

    /**
     * Asks the provider what happened to a deposit, and applies the answer.
     *
     * <p>Called by the deposit webhook, by the payment poll and by an operator's re-query. All
     * three converge here, so they cannot disagree about what a given provider answer means.</p>
     *
     * @return the intent as it stands after this call
     */
    public Mono<PaymentIntent> verifyAndApply(String depositId) {
        return intents.findByDepositId(depositId)
                .switchIfEmpty(Mono.error(() -> new UnknownDeposit(depositId)))
                .flatMap(intent -> isSettled(intent)
                        // Already decided. Not re-driven from here: the writer that made the
                        // transition drove it, and a stuck purchase is its workflow's to escalate.
                        ? Mono.just(intent)
                        : gateways.getGatewayForPhone(intent.getPhoneNumber())
                                .flatMap(gateway -> gateway.checkPaymentStatus(depositId))
                                .flatMap(result -> switch (verdictOf(result)) {
                                    case SUCCEEDED -> apply(intent, Verdict.SUCCEEDED,
                                            result.providerTransactionId(), null, null);
                                    case FAILED -> apply(intent, Verdict.FAILED,
                                            result.providerTransactionId(),
                                            orDefault(result.errorCode(), "PROVIDER_DECLINED"),
                                            orDefault(result.errorMessage(), "Payment declined by the provider"));
                                    case PENDING -> recordPoll(intent);
                                }));
    }

    /**
     * Reads a gateway result as an answer about the deposit, or as no answer at all.
     *
     * <p>The adapter builds a provider's own report — accepted, completed, rejected — with the
     * provider reference set. It builds a transport failure (timeout, 5xx, circuit open) without
     * one. Only the first is evidence about the money.</p>
     */
    public static Verdict verdictOf(PaymentResult result) {
        if (result == null) {
            return Verdict.PENDING;
        }
        if (result.isSuccess()) {
            return Verdict.SUCCEEDED;
        }
        if (result.isFailed() && result.providerTransactionId() != null) {
            return Verdict.FAILED;
        }
        return Verdict.PENDING;
    }

    /**
     * Moves the intent to a terminal status if, and only if, it is still open — and stages the
     * matching {@code booking.Payment*} envelope in the same transaction.
     *
     * <p>A verified success may also move an {@code EXPIRED} intent. {@code EXPIRED} was written by
     * a local timer, not by the provider, and the provider saying "the buyer paid" outranks it:
     * refusing the transition would leave captured money with no record that it arrived.</p>
     */
    public Mono<PaymentIntent> apply(PaymentIntent intent,
                                     Verdict verdict,
                                     String providerReference,
                                     String failureCode,
                                     String failureReason) {
        if (verdict == Verdict.PENDING) {
            return Mono.just(intent);
        }
        Instant now = clock.instant();

        List<PaymentStatus> from = verdict == Verdict.SUCCEEDED
                ? List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.EXPIRED)
                : List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

        Update update = new Update()
                .set("status", verdict == Verdict.SUCCEEDED ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED)
                .set("processedAt", now)
                .set("updatedAt", now)
                .inc("version", 1);
        if (providerReference != null) {
            update.set("providerTransactionId", providerReference);
        }
        if (verdict == Verdict.FAILED) {
            update.set("failureCode", orDefault(failureCode, "UNKNOWN"))
                    .set("failureReason", orDefault(failureReason, "Payment failed"));
        }

        Mono<Boolean> transition = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(intent.getId()).and("status").in(from)),
                        update,
                        PaymentIntent.class)
                .map(result -> result.getModifiedCount() == 1)
                .flatMap(moved -> moved
                        ? outbox.stage(envelopeFor(intent, verdict, failureCode, now)).thenReturn(true)
                        : Mono.just(false))
                .as(transactionalOperator::transactional)
                // Two answers for one deposit can race into this transaction. MongoDB aborts the
                // loser with a transient write conflict; retried, it finds the intent settled and
                // changes nothing.
                .retryWhen(Retry.backoff(5, Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(200))
                        .filter(PaymentOutcomeService::isTransientTransactionError));

        return transition.flatMap(moved -> intents.findById(intent.getId())
                .flatMap(fresh -> {
                    if (!moved) {
                        log.debug("Payment intent {} was already {} — outcome {} not re-applied",
                                fresh.getId(), fresh.getStatus(), verdict);
                        return Mono.just(fresh);
                    }
                    log.info("Payment intent {} → {} (reservation {})",
                            fresh.getId(), fresh.getStatus(), fresh.getReservationId());
                    return drive(fresh).thenReturn(fresh);
                }));
    }

    /**
     * Re-drives the purchase a verified intent implies while its reservation is still
     * HELD — what a transient failure inside {@link #apply} leaves behind. A reservation already moved
     * is left alone.
     */
    public Mono<Void> resume(String reservationId) {
        return intents.findByReservationId(reservationId)
                .filter(intent -> intent.getStatus() == com.pml.booking.domain.model.PaymentIntent.PaymentStatus.SUCCEEDED
                        || intent.getStatus() == com.pml.booking.domain.model.PaymentIntent.PaymentStatus.FAILED)
                .flatMap(intent -> mongoTemplate.exists(
                                Query.query(Criteria.where("_id").is(reservationId)
                                        .and("status").is(com.pml.shared.constants.ReservationStatus.HELD)),
                                com.pml.booking.domain.model.TicketReservation.class)
                        .flatMap(held -> held ? drive(intent) : Mono.<Void>empty()));
    }

    /**
     * Turns the transition into the purchase step it implies. Errors are logged, not raised: the
     * transition is already durable, and the purchase workflow re-verifies and escalates a
     * stranded purchase.
     */
    private Mono<Void> drive(PaymentIntent intent) {
        return switch (intent.getStatus()) {
            case SUCCEEDED -> purchases.confirm(
                            intent.getReservationId(), intent.getId(), intent.getProviderTransactionId())
                    .then()
                    .onErrorResume(PaymentOutcomeService::isLateArrival, late -> escalateLateArrival(intent))
                    .onErrorResume(error -> {
                        log.error("Payment {} succeeded but reservation {} did not confirm — "
                                        + "left HELD for its purchase workflow: {}",
                                intent.getId(), intent.getReservationId(), error.getMessage(), error);
                        return Mono.empty();
                    });
            case FAILED -> purchases.release(
                            intent.getReservationId(),
                            ReservationStateMachine.Action.RELEASE,
                            intent.getFailureReason())
                    .then()
                    .onErrorResume(error -> {
                        log.error("Payment {} failed but reservation {} was not released — "
                                        + "left for its purchase workflow: {}",
                                intent.getId(), intent.getReservationId(), error.getMessage(), error);
                        return Mono.empty();
                    });
            default -> Mono.empty();
        };
    }

    private static boolean isLateArrival(Throwable error) {
        return error instanceof ReservationExpiredException
                || error instanceof PurchaseServiceImpl.ReservationStateInvalid;
    }

    /**
     * The provider took the money after the hold was gone. No tickets exist and the seats may be
     * sold, so the only safe outcome is an operator's queue with the amount attached — then the
     * reservation is failed, in that order, so a crash between the two leaves the escalation.
     */
    private Mono<Void> escalateLateArrival(PaymentIntent intent) {
        log.warn("Payment {} for reservation {} arrived after the hold ended — {} {} needs returning",
                intent.getId(), intent.getReservationId(), intent.getAmount(), intent.getCurrency());
        return escalations.raise(
                        intent.getReservationId(),
                        intent.getEventId(),
                        intent.getUserId(),
                        intent.getId(),
                        PurchaseEscalation.Reason.PAID_AFTER_EXPIRY,
                        "The provider confirmed payment after the hold lapsed or was released. No tickets "
                                + "were issued and the inventory was returned. The capture must be refunded.",
                        intent.getAmount(),
                        intent.getCurrency())
                .then(purchases.release(
                        intent.getReservationId(),
                        ReservationStateMachine.Action.FAIL,
                        "Payment captured after the hold ended; refund owed"))
                .then();
    }

    private Mono<PaymentIntent> recordPoll(PaymentIntent intent) {
        return mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(intent.getId())),
                        new Update().inc("pollAttempts", 1).set("lastPolledAt", clock.instant()),
                        PaymentIntent.class)
                .then(intents.findById(intent.getId()));
    }

    private static EventEnvelope envelopeFor(PaymentIntent intent, Verdict verdict,
                                             String failureCode, Instant now) {
        if (verdict == Verdict.SUCCEEDED) {
            return EventEnvelopes.of(EventType.BOOKING_PAYMENT_COMPLETED, now, intent.getReservationId(),
                    Map.of("paymentIntentId", intent.getId(),
                            "reservationId", intent.getReservationId(),
                            "userId", intent.getUserId(),
                            "amount", intent.getAmount(),
                            "currency", orDefault(intent.getCurrency(), "ZMW")));
        }
        return EventEnvelopes.of(EventType.BOOKING_PAYMENT_FAILED, now, intent.getReservationId(),
                Map.of("paymentIntentId", intent.getId(),
                        "reservationId", intent.getReservationId(),
                        "userId", intent.getUserId(),
                        "failureCode", orDefault(failureCode, "UNKNOWN")));
    }

    static boolean isTransientTransactionError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSettled(PaymentIntent intent) {
        return SETTLED.contains(intent.getStatus());
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
