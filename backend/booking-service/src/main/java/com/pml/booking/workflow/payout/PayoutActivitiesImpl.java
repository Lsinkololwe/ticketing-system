package com.pml.booking.workflow.payout;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.service.PayoutSettlementService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The payout activities, adapting {@link PayoutSettlementService} to Temporal.
 *
 * <p>An activity method is synchronous by contract and runs on the worker's activity executor, so
 * the reactive chain is awaited here. The await is shorter than the activity's
 * start-to-close timeout, so a stalled database surfaces as a retried attempt rather than an
 * abandoned one.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.FINANCE)
public class PayoutActivitiesImpl implements PayoutActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final PayoutSettlementService settlement;

    public PayoutActivitiesImpl(PayoutSettlementService settlement) {
        this.settlement = settlement;
    }

    @Override
    public View createRequest(Submit command) {
        return await(settlement.createRequest(command));
    }

    @Override
    public View approve(Decision decision) {
        return await(settlement.approve(decision));
    }

    @Override
    public View reject(Decision decision) {
        return await(settlement.reject(decision));
    }

    @Override
    public View cancel(Decision decision) {
        return await(settlement.cancel(decision));
    }

    @Override
    public View hold(Decision decision) {
        return await(settlement.hold(decision));
    }

    @Override
    public View release(Decision decision) {
        return await(settlement.release(decision));
    }

    @Override
    public View beginSettlement(String payoutRequestId, String providerPayoutId) {
        return await(settlement.beginSettlement(payoutRequestId, providerPayoutId));
    }

    @Override
    public View completeSettlement(String payoutRequestId, String reference) {
        return await(settlement.completeSettlement(payoutRequestId, reference));
    }

    @Override
    public View failSettlement(String payoutRequestId, String failureCode, String reason) {
        return await(settlement.failSettlement(payoutRequestId, failureCode, reason));
    }

    @Override
    public void markUnconfirmed(String payoutRequestId) {
        await(settlement.markUnconfirmed(payoutRequestId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void flagBankAccount(String bankAccountId, String failureCode) {
        await(settlement.flagBankAccount(bankAccountId, failureCode).thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
