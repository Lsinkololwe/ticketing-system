package com.pml.booking.workflow.bank;

import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.PaymentAttemptRecorder.ProviderCall;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.GatewayPayoutRequest;
import com.pml.booking.infrastructure.gateway.model.PayoutResult;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.BankVerificationService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.View;
import com.pml.booking.workflow.payout.PayoutProviderActivitiesImpl;
import com.pml.booking.workflow.payout.PayoutRules;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Verification activities. Registered on the finance queue for the account writes
 * and on the provider queue for the deposit, which shares the provider's rate limit with payouts.
 */
@Component
@ActivityImpl(taskQueues = {TaskQueues.FINANCE, TaskQueues.PROVIDER})
public class BankVerificationActivitiesImpl implements BankVerificationActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final BankVerificationService verification;
    private final MobileMoneyGatewayFactory gateways;
    private final AccountingService accounting;
    private final PaymentAttemptRecorder attempts;

    public BankVerificationActivitiesImpl(BankVerificationService verification, MobileMoneyGatewayFactory gateways,
                                          AccountingService accounting, PaymentAttemptRecorder attempts) {
        this.verification = verification;
        this.gateways = gateways;
        this.accounting = accounting;
        this.attempts = attempts;
    }

    @Override
    public View prepare(String bankAccountId, BigDecimal depositAmount) {
        return await(verification.prepare(bankAccountId, depositAmount));
    }

    @Override
    public void sendDeposit(String bankAccountId, String providerPayoutId) {
        PayoutResult result = await(verification.load(bankAccountId).flatMap(account ->
                gateways.getGatewayForPhone(account.getAccountNumber())
                        .switchIfEmpty(Mono.error(ApplicationFailure.newNonRetryableFailure(
                                "no mobile-money provider serves this account", PayoutRules.BAD_ACCOUNT_DETAILS)))
                        .flatMap(gateway -> attempts.beforeCall(new ProviderCall(PaymentAttemptType.VERIFICATION,
                                        providerPayoutId, account.getMicroDepositAmount(), account.getCurrency(), null,
                                        account.getOrganizationId(), null, null, account.getId()))
                                .then(gateway.initiatePayout(GatewayPayoutRequest.builder()
                                .correlationId(providerPayoutId)
                                .phoneNumber(account.getAccountNumber())
                                .amount(account.getMicroDepositAmount())
                                .currency(account.getCurrency())
                                .description("Account verification")
                                .recipientName(account.getAccountHolderName())
                                .organizerId(account.getOrganizerId())
                                .build()))
                                .flatMap(answer -> attempts.afterCall(providerPayoutId,
                                                PayoutProviderActivitiesImpl.outcomeOf(answer), answer.errorCode(),
                                                answer.errorMessage())
                                        .thenReturn(answer)))));
        if (result.isSuccessOrPending()) {
            return;
        }
        String message = result.errorMessage() != null ? result.errorMessage() : "the verification deposit was refused";
        if (result.isRetryable()) {
            throw ApplicationFailure.newFailure(message, PayoutRules.TRANSFER_UNAVAILABLE);
        }
        throw ApplicationFailure.newNonRetryableFailure(message, PayoutRules.BAD_ACCOUNT_DETAILS);
    }

    @Override
    public void bookDeposit(String bankAccountId, String providerPayoutId) {
        await(verification.load(bankAccountId).flatMap(account -> accounting.recordVerificationDeposit(
                providerPayoutId, bankAccountId, account.getMicroDepositAmount(), account.getCurrency())));
    }

    @Override
    public BankVerificationWorkflow.DepositOutcome depositOutcome(String bankAccountId, String providerPayoutId) {
        PayoutResult result = await(verification.load(bankAccountId).flatMap(account ->
                gateways.getGatewayForPhone(account.getAccountNumber())
                        .switchIfEmpty(Mono.error(ApplicationFailure.newNonRetryableFailure(
                                "no mobile-money provider serves this account", PayoutRules.BAD_ACCOUNT_DETAILS)))
                        .flatMap(gateway -> gateway.checkPayoutStatus(providerPayoutId))));
        return switch (PayoutProviderActivitiesImpl.answer(result, providerPayoutId).outcome()) {
            case SUCCEEDED -> BankVerificationWorkflow.DepositOutcome.DELIVERED;
            case FAILED -> BankVerificationWorkflow.DepositOutcome.FAILED;
            case PENDING -> BankVerificationWorkflow.DepositOutcome.PENDING;
        };
    }

    @Override
    public void reverseDeposit(String bankAccountId, String providerPayoutId) {
        await(accounting.reverseVerificationDeposit(providerPayoutId,
                        "the provider confirmed the verification deposit to " + bankAccountId + " failed")
                .thenReturn(Boolean.TRUE));
    }

    @Override
    public View confirm(String bankAccountId, String actorId, BigDecimal amount) {
        return await(verification.confirm(bankAccountId, actorId, amount));
    }

    @Override
    public View unlock(String bankAccountId) {
        return await(verification.unlock(bankAccountId));
    }

    @Override
    public View expire(String bankAccountId) {
        return await(verification.expire(bankAccountId));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
