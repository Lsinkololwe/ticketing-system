package com.pml.booking.workflow.payout;

import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentAttemptRecorder.CallOutcome;
import com.pml.booking.service.PaymentAttemptRecorder.ProviderCall;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.PayoutResult;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.payout.PayoutWorkflow.Answer;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;

/**
 * The transfer, on {@code booking-provider}.
 *
 * <p>The provider payout id was stored by {@code beginSettlement} before this runs, and the
 * provider deduplicates on it, so a retried initiation is the same payout rather than a second one.
 * A status answer is trusted only when it is terminal and carries the provider's own reference —
 * the rule {@code PaymentOutcomeService.verdictOf} applies to deposits.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.PROVIDER)
public class PayoutProviderActivitiesImpl implements PayoutProviderActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final ReactiveMongoTemplate template;
    private final MobileMoneyGatewayFactory gateways;
    private final PaymentAttemptRecorder attempts;

    public PayoutProviderActivitiesImpl(ReactiveMongoTemplate template, MobileMoneyGatewayFactory gateways,
                                        PaymentAttemptRecorder attempts) {
        this.template = template;
        this.gateways = gateways;
        this.attempts = attempts;
    }

    @Override
    public void initiate(String payoutRequestId) {
        PayoutResult result = await(destination(payoutRequestId).flatMap(target -> gatewayFor(target.getT2())
                .flatMap(gateway -> attempts.beforeCall(new ProviderCall(PaymentAttemptType.PAYOUT,
                                target.getT1().getPawaPayPayoutId(), target.getT1().getSettledAmount(),
                                target.getT1().getCurrency(), target.getT1().getEventId(),
                                target.getT1().getOrganizationId(), null, target.getT1().getId(), target.getT2().getId()))
                        .then(gateway.initiatePayout(
                        com.pml.booking.infrastructure.gateway.model.GatewayPayoutRequest.builder()
                                .correlationId(target.getT1().getPawaPayPayoutId())
                                .phoneNumber(target.getT2().getAccountNumber())
                                .amount(target.getT1().getSettledAmount())
                                .currency(target.getT1().getCurrency())
                                .description("Payout " + target.getT1().getRequestId())
                                .recipientName(target.getT2().getAccountHolderName())
                                .payoutRequestId(target.getT1().getId())
                                .eventId(target.getT1().getEventId())
                                .organizerId(target.getT1().getOrganizerId())
                                .build())))
                .flatMap(answer -> attempts.afterCall(target.getT1().getPawaPayPayoutId(), outcomeOf(answer),
                                answer.errorCode(), answer.errorMessage())
                        .thenReturn(answer))));

        if (result.isSuccessOrPending()) {
            return;
        }
        String message = result.errorMessage() != null ? result.errorMessage() : "the provider refused the transfer";
        if (PayoutRules.isBadAccount(result.errorCode())) {
            throw ApplicationFailure.newNonRetryableFailure(message, PayoutRules.BAD_ACCOUNT_DETAILS);
        }
        if (result.isRetryable()) {
            throw ApplicationFailure.newFailure(message, PayoutRules.TRANSFER_UNAVAILABLE);
        }
        throw ApplicationFailure.newNonRetryableFailure(message, PayoutRules.TRANSFER_REFUSED);
    }

    @Override
    public Answer status(String payoutRequestId) {
        return await(destination(payoutRequestId).flatMap(target -> gatewayFor(target.getT2())
                .flatMap(gateway -> gateway.checkPayoutStatus(target.getT1().getPawaPayPayoutId()))
                .map(result -> answer(result, target.getT1().getPawaPayPayoutId()))));
    }

    public static Answer answer(PayoutResult result, String providerPayoutId) {
        if (result.isSuccess()) {
            return Answer.succeeded(result.providerTransactionId() != null ? result.providerTransactionId() : providerPayoutId);
        }
        if (result.isFailed() && result.providerTransactionId() != null && !result.isRetryable()) {
            return Answer.failed(result.errorCode() != null ? result.errorCode() : PayoutRules.TRANSFER_REFUSED,
                    result.errorMessage());
        }
        return Answer.pending();
    }

    private Mono<Tuple2<PayoutRequest, BankAccount>> destination(String payoutRequestId) {
        return template.findById(payoutRequestId, PayoutRequest.class)
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "no payout request " + payoutRequestId)))
                .flatMap(request -> template.findById(request.getBankAccountId(), BankAccount.class)
                        .switchIfEmpty(Mono.error(ApplicationFailure.newNonRetryableFailure(
                                "the payout's bank account no longer exists", PayoutRules.BAD_ACCOUNT_DETAILS)))
                        .map(bank -> Tuples.of(request, bank)));
    }

    private Mono<MobileMoneyGateway> gatewayFor(BankAccount bank) {
        return gateways.getGatewayForPhone(bank.getAccountNumber())
                .switchIfEmpty(Mono.error(ApplicationFailure.newNonRetryableFailure(
                        "no mobile-money provider serves this account", PayoutRules.BAD_ACCOUNT_DETAILS)));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }

    /** A retryable failure is the provider not answering, not a refusal of this payout. */
    public static CallOutcome outcomeOf(PayoutResult result) {
        if (result.isSuccessOrPending()) {
            return CallOutcome.ACCEPTED;
        }
        return result.isRetryable() ? CallOutcome.NO_ANSWER : CallOutcome.REFUSED;
    }
}
