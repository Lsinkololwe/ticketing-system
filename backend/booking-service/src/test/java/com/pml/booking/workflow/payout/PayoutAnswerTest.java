package com.pml.booking.workflow.payout;

import com.pml.booking.infrastructure.gateway.model.PaymentResultStatus;
import com.pml.booking.infrastructure.gateway.model.PayoutResult;
import com.pml.booking.workflow.payout.PayoutWorkflow.Answer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only a terminal provider answer carrying the provider's own reference ends a
 * transfer. Transport failures and failures with no reference are no answer at all.
 */
@Tag("L1")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R6 · a provider status is a verdict only when it is terminal and referenced")
class PayoutAnswerTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    @DisplayName("a completed payout is a success carrying the provider's reference")
    void successIsAVerdict() {
        Answer answer = PayoutProviderActivitiesImpl.answer(PayoutResult.success("pp-1", "prov-9", "pawapay", NOW), "pp-1");

        assertThat(answer.outcome()).isEqualTo(Answer.Outcome.SUCCEEDED);
        assertThat(answer.reference()).isEqualTo("prov-9");
    }

    @Test
    @DisplayName("a referenced, non-retryable failure is a failure")
    void referencedFailureIsAVerdict() {
        PayoutResult failed = PayoutResult.builder()
                .correlationId("pp-1").providerTransactionId("prov-9").status(PaymentResultStatus.FAILED)
                .errorCode("INSUFFICIENT_BALANCE").errorMessage("float empty").isRetryable(false)
                .providerId("pawapay").timestamp(NOW).build();

        Answer answer = PayoutProviderActivitiesImpl.answer(failed, "pp-1");

        assertThat(answer.outcome()).isEqualTo(Answer.Outcome.FAILED);
        assertThat(answer.failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("a failure with no provider reference — a timeout, a 5xx — is no answer")
    void unreferencedFailureIsPending() {
        PayoutResult transport = PayoutResult.builder()
                .correlationId("pp-1").status(PaymentResultStatus.FAILED).errorCode("HTTP_503")
                .isRetryable(true).providerId("pawapay").timestamp(NOW).build();

        assertThat(PayoutProviderActivitiesImpl.answer(transport, "pp-1").outcome()).isEqualTo(Answer.Outcome.PENDING);
    }

    @Test
    @DisplayName("a pending payout is no answer")
    void pendingIsPending() {
        assertThat(PayoutProviderActivitiesImpl.answer(PayoutResult.pending("pp-1", "prov-9", "pawapay", NOW), "pp-1").outcome())
                .isEqualTo(Answer.Outcome.PENDING);
    }
}
