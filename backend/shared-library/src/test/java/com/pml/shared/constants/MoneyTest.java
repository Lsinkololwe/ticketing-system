package com.pml.shared.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rounding contract, with the arithmetic written out.
 *
 * <h2>L1: no Spring, no database</h2>
 * These are pure functions over {@link BigDecimal}, and the interesting cases are all arithmetic
 * — half-way values, a percentage that does not divide, and the difference between rounding once
 * and rounding twice. Nothing here needs a context, and a test that needed one would be
 * measuring something other than the arithmetic.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R4 · HALF_UP at scale 2, applied once")
class MoneyTest {

    @Test
    @DisplayName("half-way values round up, not to even")
    void halfWayRoundsUp() {
        // The distinction that makes HALF_UP the right choice here: HALF_EVEN sends K0.125 to
        // K0.12 because 2 is even, and K0.135 to K0.14 because 4 is. A customer shown K0.13
        // and charged K0.12 has found a discrepancy nobody can explain from the figures.
        assertThat(Money.round(new BigDecimal("0.125"))).isEqualByComparingTo("0.13");
        assertThat(Money.round(new BigDecimal("0.135"))).isEqualByComparingTo("0.14");
        assertThat(Money.round(new BigDecimal("2.345"))).isEqualByComparingTo("2.35");
    }

    @Test
    @DisplayName("a rounded amount always carries scale 2, even when it did not need to")
    void scaleIsAlwaysTwo() {
        // Scale is not cosmetic once the value reaches Decimal128: K5 stored at scale 0 and K5
        // stored at scale 2 are the same amount and different documents, and BigDecimal.equals
        // says they differ.
        assertThat(Money.round(new BigDecimal("5")).scale()).isEqualTo(2);
        assertThat(Money.round(new BigDecimal("5.1")).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("an absent amount stays absent — it is not zero")
    void nullIsNotZero() {
        // A null fee means "no fee was calculated"; zero means "the fee is nothing". Coercing
        // the first into the second makes an unanswered question look like a settled one.
        assertThat(Money.round(null)).isNull();
        assertThat(Money.percentageOf(null, BigDecimal.TEN)).isNull();
        assertThat(Money.percentageOf(BigDecimal.TEN, null)).isNull();
    }

    @Test
    @DisplayName("a percentage that does not divide evenly is rounded once, at the end")
    void percentageRoundsOnceAtTheEnd() {
        // 15% of K33.33 is K4.99950 exactly. Rounding once gives K5.00.
        assertThat(Money.percentageOf(new BigDecimal("33.33"), new BigDecimal("15")))
                .isEqualByComparingTo("5.00");

        // A third of a kwacha does not terminate. Plain BigDecimal.divide throws
        // ArithmeticException here, which at checkout is a 500 rather than a price.
        assertThat(Money.percentageOf(new BigDecimal("1.00"), new BigDecimal("33.333333")))
                .isEqualByComparingTo("0.33");
    }

    @Test
    @DisplayName("rounding twice gives a different answer than rounding once")
    void doubleRoundingDrifts() {
        // Why rounding is applied once rather than at each step. Rounding an intermediate value to
        // the platform scale and then computing from it moves the result: the fee below lands a
        // full ngwee away from the same fee computed in one pass.
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("2.5");

        BigDecimal once = Money.percentageOf(amount, rate);

        BigDecimal roundedRateFirst = Money.round(rate.divide(new BigDecimal("100"), 2, Money.ROUNDING));
        BigDecimal twice = Money.round(amount.multiply(roundedRateFirst));

        assertThat(once).isEqualByComparingTo("2.50");
        assertThat(twice).isEqualByComparingTo("3.00");
        assertThat(once)
                .as("rounding an intermediate is not a smaller version of rounding the result")
                .isNotEqualByComparingTo(twice);
    }
}
