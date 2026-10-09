package com.pml.booking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** However a refund is cut, the commission's share and the escrow's share add up to exactly the refund. */
@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004 · a refund's commission and escrow shares always add up to the refund, to the ngwee")
class RefundSplitRulesTest {

    @Test
    @DisplayName("every refund from one ngwee to the whole price splits into two shares that sum to it, with the commission never above its own")
    void sumsExactly() {
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal commission = new BigDecimal("5.00");
        for (int ngwee = 1; ngwee <= 10_000; ngwee++) {
            BigDecimal refund = BigDecimal.valueOf(ngwee, 2);
            RefundSplit split = RefundSplit.of(price, commission, refund);
            assertThat(split.commissionShare().add(split.escrowDebit())).as("refund %s", refund).isEqualByComparingTo(refund);
            assertThat(split.commissionShare().signum()).isGreaterThanOrEqualTo(0);
            assertThat(split.escrowDebit().signum()).isGreaterThanOrEqualTo(0);
            assertThat(split.commissionShare()).isLessThanOrEqualTo(commission);
            assertThat(split.whole()).isEqualTo(ngwee == 10_000);
        }
    }

    @Test
    @DisplayName("an awkward price and rate still add up, and a refund of more than stands is the whole")
    void awkwardAmounts() {
        BigDecimal price = new BigDecimal("33.33");
        BigDecimal commission = new BigDecimal("1.67");
        for (String part : new String[]{"0.01", "11.11", "22.22", "33.32", "33.33"}) {
            RefundSplit split = RefundSplit.of(price, commission, new BigDecimal(part));
            assertThat(split.commissionShare().add(split.escrowDebit())).isEqualByComparingTo(part);
        }
        RefundSplit over = RefundSplit.of(price, commission, new BigDecimal("50.00"));
        assertThat(over.whole()).isTrue();
        assertThat(over.commissionShare()).isEqualByComparingTo("1.67");
        assertThat(over.escrowDebit()).isEqualByComparingTo("31.66");
    }

    @Test
    @DisplayName("successive part refunds against what stands take the whole commission between them")
    void successiveRefundsTakeTheWholeCommission() {
        BigDecimal standingPrice = new BigDecimal("100.00");
        BigDecimal standingCommission = new BigDecimal("5.00");
        BigDecimal commissionTaken = BigDecimal.ZERO;
        BigDecimal escrowTaken = BigDecimal.ZERO;
        for (String part : new String[]{"40.00", "35.50", "24.50"}) {
            BigDecimal refund = new BigDecimal(part);
            RefundSplit split = RefundSplit.of(standingPrice, standingCommission, refund);
            commissionTaken = commissionTaken.add(split.commissionShare());
            escrowTaken = escrowTaken.add(split.escrowDebit());
            standingPrice = standingPrice.subtract(refund);
            standingCommission = standingCommission.subtract(split.commissionShare());
        }
        assertThat(commissionTaken).isEqualByComparingTo("5.00");
        assertThat(escrowTaken).isEqualByComparingTo("95.00");
        assertThat(standingCommission).isEqualByComparingTo("0");
    }
}
