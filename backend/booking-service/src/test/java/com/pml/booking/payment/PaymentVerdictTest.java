package com.pml.booking.payment;

import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.infrastructure.gateway.model.PaymentResultStatus;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PaymentOutcomeService.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A gateway result is an answer about the money only when the provider gave it.
 *
 * <p>A declined deposit and a timed-out status call both arrive as a {@code FAILED} result. Only the
 * first carries the provider's reference, and only the first may fail a payment: reading the second as
 * a decline would release seats a buyer is paying for.</p>
 */
@Tag("L1")
@Tag("ET-PAY-001")
@DisplayName("ET-PAY-001-R2/R3 · only a provider's own report settles a payment")
class PaymentVerdictTest {

    private static final Instant NOW = Instant.parse("2026-09-01T09:00:00Z");

    private static PaymentResult reported(PaymentResultStatus status) {
        return PaymentResult.builder()
                .correlationId("deposit-1")
                .providerTransactionId("deposit-1")
                .status(status)
                .providerId("pawapay")
                .timestamp(NOW)
                .build();
    }

    @Test
    @DisplayName("a completed deposit is SUCCEEDED")
    void completedIsSucceeded() {
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.SUCCESS))).isEqualTo(Verdict.SUCCEEDED);
    }

    @Test
    @DisplayName("failed, rejected and expired reports from the provider are FAILED")
    void providerDeclinesAreFailed() {
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.FAILED))).isEqualTo(Verdict.FAILED);
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.REJECTED))).isEqualTo(Verdict.FAILED);
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.EXPIRED))).isEqualTo(Verdict.FAILED);
    }

    @Test
    @DisplayName("pending and processing reports are PENDING")
    void inFlightReportsArePending() {
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.PENDING))).isEqualTo(Verdict.PENDING);
        assertThat(PaymentOutcomeService.verdictOf(reported(PaymentResultStatus.PROCESSING))).isEqualTo(Verdict.PENDING);
    }

    @Test
    @DisplayName("a transport failure carries no provider reference and is PENDING, never FAILED")
    void aTransportFailureIsNoAnswer() {
        PaymentResult timedOut = PaymentResult.failed("deposit-1", "HTTP_503", "unavailable", true, "pawapay", NOW);

        assertThat(PaymentOutcomeService.verdictOf(timedOut)).isEqualTo(Verdict.PENDING);
        assertThat(PaymentOutcomeService.verdictOf(null)).isEqualTo(Verdict.PENDING);
    }
}
