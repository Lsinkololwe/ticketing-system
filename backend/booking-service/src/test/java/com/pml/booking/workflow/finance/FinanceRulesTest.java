package com.pml.booking.workflow.finance;

import com.pml.booking.workflow.refund.RefundRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-001-R4/ET-FIN-004-R5 · the hold is seven days, and a waiting refund is escalated at two and five days")
class FinanceRulesTest {

    @Test
    @DisplayName("holdUntil is the end plus exactly seven days")
    void holdIsSevenDays() {
        Instant end = Instant.parse("2026-12-02T01:00:00Z");

        assertThat(Instant.ofEpochMilli(EventFinanceRules.holdUntil(end.toEpochMilli())))
                .isEqualTo(Instant.parse("2026-12-09T01:00:00Z"));
        assertThat(EventFinanceRules.HOLD_PERIOD).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("R5, D-26 · a refund waiting for a person is escalated after two days and again after five")
    void refundReviewEscalations() {
        assertThat(RefundRules.REVIEW_ESCALATIONS).containsExactly(Duration.ofDays(2), Duration.ofDays(5));
        assertThat(RefundRules.reviewEscalation(2)).isEqualTo(Duration.ofDays(5));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RefundRules.reviewEscalation(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refund status polls double from thirty seconds to ten minutes")
    void refundPollsBackOff() {
        assertThat(RefundRules.pollDelay(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(RefundRules.pollDelay(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(RefundRules.pollDelay(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(RefundRules.pollDelay(40)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("R6 · cancellations refund two hundred tickets per batch")
    void refundBatchSize() {
        assertThat(EventFinanceRules.REFUND_BATCH).isEqualTo(200);
    }
}
