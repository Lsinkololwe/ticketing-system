package com.pml.booking.workflow.latepayment;

import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.exception.ProviderUnavailableException;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.repository.PurchaseEscalationRepository;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentAttemptRecorder.CallOutcome;
import com.pml.booking.service.PaymentAttemptRecorder.ProviderCall;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PurchaseEscalationService;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Answer;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.State;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.View;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * The late-money refund over the services and the gateway the ticket refunds already use.
 *
 * <p>The state lives on the payment intent, one document, so each transition is a single atomic
 * compare-and-set. The refund id is minted once and sent on every retry, so the provider treats a
 * repeat as the refund it already holds. The provider call is never made inside a transaction.
 */
@Component
@ActivityImpl(taskQueues = {TaskQueues.CHECKOUT, TaskQueues.PROVIDER})
public class LatePaymentRefundActivitiesImpl implements LatePaymentRefundActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);
    private static final String PROVIDER = "pawapay";
    private static final String REASON = "Payment received after the reservation lapsed";

    private final PaymentIntentRepository intents;
    private final ReactiveMongoTemplate template;
    private final MobileMoneyGatewayFactory gateways;
    private final PaymentAttemptRecorder attempts;
    private final PurchaseEscalationService escalations;
    private final PurchaseEscalationRepository escalationRows;

    public LatePaymentRefundActivitiesImpl(PaymentIntentRepository intents, ReactiveMongoTemplate template,
                                           MobileMoneyGatewayFactory gateways, PaymentAttemptRecorder attempts,
                                           PurchaseEscalationService escalations,
                                           PurchaseEscalationRepository escalationRows) {
        this.intents = intents;
        this.template = template;
        this.gateways = gateways;
        this.attempts = attempts;
        this.escalations = escalations;
        this.escalationRows = escalationRows;
    }

    @Override
    public View open(String reservationId) {
        return await(late(reservationId)
                .flatMap(intent -> {
                    AggregationUpdate mint = AggregationUpdate.update()
                            .set("lateRefundId").toValue(ConditionalOperators.ifNull("lateRefundId")
                                    .then(PawaPayClient.generateTransactionId()))
                            .set("lateRefundStatus").toValue(ConditionalOperators.ifNull("lateRefundStatus")
                                    .then(State.REQUESTED.name()));
                    return template.findAndModify(Query.query(Criteria.where("_id").is(intent.getId())), mint,
                                    FindAndModifyOptions.options().returnNew(true), PaymentIntent.class)
                            .flatMap(minted -> com.pml.booking.service.BookingStore.lateRefundMoved(template, reservationId,
                                    minted.getLateRefundStatus()).thenReturn(minted));
                })
                .map(LatePaymentRefundActivitiesImpl::view));
    }

    /**
     * Late money is a verified success whose reservation did not become CONFIRMED. A payment that
     * confirmed its tickets, or that the provider never verified, is not owed back and is refused.
     */
    private Mono<PaymentIntent> late(String reservationId) {
        return intents.findByReservationId(reservationId)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.PAYMENT_INTENT_UNKNOWN,
                        "no payment for reservation " + reservationId)))
                .flatMap(intent -> {
                    boolean paid = intent.getStatus() == PaymentStatus.SUCCEEDED || intent.getStatus() == PaymentStatus.REFUNDED;
                    if (!paid || intent.getDepositId() == null) {
                        return Mono.error(Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID,
                                "the payment for reservation " + reservationId + " has no verified success to refund"));
                    }
                    return template.findById(reservationId, TicketReservation.class)
                            .map(TicketReservation::getStatus)
                            .filter(status -> status != ReservationStatus.CONFIRMED && status != ReservationStatus.HELD)
                            .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID,
                                    "reservation " + reservationId + " holds or issued its tickets; its payment is not late")))
                            .thenReturn(intent);
                });
    }

    @Override
    public View submit(String reservationId) {
        return await(stored(reservationId).flatMap(intent -> {
            if (!State.REQUESTED.name().equals(intent.getLateRefundStatus())) {
                return Mono.just(intent);
            }
            ProviderCall call = new ProviderCall(PaymentAttemptType.REFUND, intent.getLateRefundId(), intent.getAmount(),
                    intent.getCurrency(), intent.getEventId(), null, null, null, null);
            return attempts.beforeCall(call)
                    .then(gateway())
                    .flatMap(gateway -> gateway.initiateRefund(intent.getDepositId(), intent.getLateRefundId(), REASON))
                    .flatMap(result -> applySubmission(intent, result));
        }).map(LatePaymentRefundActivitiesImpl::view));
    }

    /**
     * An accepted refund is with the provider; a refusal that carries the provider's reference is
     * final; anything else is silence, which proves nothing and is retried under the same id.
     */
    private Mono<PaymentIntent> applySubmission(PaymentIntent intent, PaymentResult result) {
        String id = intent.getLateRefundId();
        if (result.isSuccessOrPending()) {
            return attempts.afterCall(id, CallOutcome.ACCEPTED, null, null)
                    .then(move(intent, State.REQUESTED, State.PROCESSING, null));
        }
        if (result.isFailed() && result.providerTransactionId() != null) {
            String code = result.errorCode() != null ? result.errorCode() : "REFUND_NOT_ACCEPTED";
            return attempts.afterCall(id, CallOutcome.REFUSED, code, result.errorMessage())
                    .then(move(intent, State.REQUESTED, State.FAILED, code));
        }
        return attempts.afterCall(id, CallOutcome.NO_ANSWER, result.errorCode(), result.errorMessage())
                .then(Mono.error(new ProviderUnavailableException(
                        "the provider gave no answer for late refund " + id + "; it is sent again with the same id")));
    }

    @Override
    public Answer providerStatus(String reservationId) {
        return await(stored(reservationId).flatMap(intent -> gateway()
                .flatMap(gateway -> gateway.checkRefundStatus(intent.getLateRefundId()))
                .map(result -> switch (PaymentOutcomeService.verdictOf(result)) {
                    case SUCCEEDED -> new Answer(Answer.Outcome.COMPLETED, result.providerTransactionId() != null
                            ? result.providerTransactionId() : intent.getLateRefundId(), null);
                    case FAILED -> new Answer(Answer.Outcome.FAILED, null,
                            result.errorCode() != null ? result.errorCode() : "REFUND_FAILED");
                    case PENDING -> new Answer(Answer.Outcome.PENDING, null, null);
                })));
    }

    @Override
    public View complete(String reservationId, String reference) {
        return await(stored(reservationId).flatMap(intent -> template.updateFirst(
                        Query.query(Criteria.where("_id").is(intent.getId())
                                .and("lateRefundStatus").in(State.REQUESTED.name(), State.PROCESSING.name())),
                        new Update().set("lateRefundStatus", State.COMPLETED.name())
                                .set("status", PaymentStatus.REFUNDED).inc("version", 1),
                        PaymentIntent.class)
                .then(com.pml.booking.service.BookingStore.lateRefundMoved(template, reservationId, State.COMPLETED.name()))
                .then(escalationRows.findByReservationId(reservationId)
                        .filter(row -> !row.isResolved())
                        .flatMap(row -> escalations.resolve(reservationId, "SYSTEM",
                                "refunded in full automatically under " + intent.getLateRefundId()))
                        .then(stored(reservationId))))
                .map(LatePaymentRefundActivitiesImpl::view));
    }

    @Override
    public View fail(String reservationId, String failureCode) {
        return await(stored(reservationId).flatMap(intent -> move(intent, null, State.FAILED, failureCode))
                .map(LatePaymentRefundActivitiesImpl::view));
    }

    @Override
    public void escalate(String reservationId, String reason) {
        await(intents.findByReservationId(reservationId)
                .flatMap(intent -> escalations.raise(reservationId, intent.getEventId(), intent.getUserId(), intent.getId(),
                        PurchaseEscalation.Reason.PAID_AFTER_EXPIRY,
                        "The payment arrived after the hold lapsed and could not be refunded automatically: " + reason
                                + ". No tickets were issued; the capture must be refunded in full.",
                        intent.getAmount(), intent.getCurrency()))
                .thenReturn(Boolean.TRUE));
    }

    /** Moves the refund from {@code from} (or any open state when null) to {@code to}, once; returns the stored intent. */
    private Mono<PaymentIntent> move(PaymentIntent intent, State from, State to, String failure) {
        Criteria where = Criteria.where("_id").is(intent.getId());
        where = from != null
                ? where.and("lateRefundStatus").is(from.name())
                : where.and("lateRefundStatus").in(List.of(State.REQUESTED.name(), State.PROCESSING.name()));
        Update update = new Update().set("lateRefundStatus", to.name()).inc("version", 1);
        if (failure != null) {
            update.set("lateRefundFailure", failure);
        }
        return template.updateFirst(Query.query(where), update, PaymentIntent.class)
                .then(com.pml.booking.service.BookingStore.lateRefundMoved(template, intent.getReservationId(), to.name()))
                .then(stored(intent.getReservationId()));
    }

    private Mono<PaymentIntent> stored(String reservationId) {
        return intents.findByReservationId(reservationId)
                .filter(intent -> intent.getLateRefundId() != null)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED,
                        "no late refund was opened for reservation " + reservationId)));
    }

    private Mono<MobileMoneyGateway> gateway() {
        return gateways.getGatewayByProvider(PROVIDER)
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(gateways.getAllGateways().stream().findFirst())))
                .switchIfEmpty(Mono.error(new IllegalStateException("Timeout on blocking read: no refund provider is configured")));
    }

    static View view(PaymentIntent intent) {
        return new View(intent.getReservationId(), intent.getLateRefundId(), State.valueOf(intent.getLateRefundStatus()));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
