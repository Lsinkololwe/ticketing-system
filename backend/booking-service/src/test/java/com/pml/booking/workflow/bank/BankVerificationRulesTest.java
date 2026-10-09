package com.pml.booking.workflow.bank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R4 · the micro-deposit is K0.50 moved by a few ngwee, and three misses lock")
class BankVerificationRulesTest {

    @Test
    @DisplayName("every draw gives an amount within nine ngwee of K0.50 and never K0.50 itself")
    void depositsStayNearButNotOnTheBase() {
        Set<BigDecimal> seen = new HashSet<>();
        for (int draw = -2_000; draw < 2_000; draw++) {
            BigDecimal amount = BankVerificationRules.depositFor(draw);
            assertThat(amount).isBetween(new BigDecimal("0.41"), new BigDecimal("0.59"));
            assertThat(amount).isNotEqualByComparingTo("0.50");
            assertThat(amount.scale()).isEqualTo(2);
            seen.add(amount);
        }
        assertThat(seen).as("the amount varies, so it has to be read rather than guessed").hasSizeGreaterThan(9);
    }

    @Test
    @DisplayName("a confirmation matches by value, not by how many decimals were typed")
    void matchesByValue() {
        assertThat(BankVerificationRules.matches(new BigDecimal("0.53"), new BigDecimal("0.530"))).isTrue();
        assertThat(BankVerificationRules.matches(new BigDecimal("0.53"), new BigDecimal("0.35"))).isFalse();
        assertThat(BankVerificationRules.matches(null, new BigDecimal("0.53"))).isFalse();
    }

    @Test
    @DisplayName("the third wrong amount locks; the second does not")
    void thirdMissLocks() {
        assertThat(BankVerificationRules.locksAfter(2)).isFalse();
        assertThat(BankVerificationRules.locksAfter(3)).isTrue();
    }

    @Test
    @DisplayName("D-31 · a sent deposit is asked about after thirty seconds, doubling to hourly")
    void depositPollsBackOff() {
        org.assertj.core.api.Assertions.assertThat(BankVerificationRules.depositPollDelay(0)).isEqualTo(java.time.Duration.ofSeconds(30));
        org.assertj.core.api.Assertions.assertThat(BankVerificationRules.depositPollDelay(3)).isEqualTo(java.time.Duration.ofMinutes(4));
        org.assertj.core.api.Assertions.assertThat(BankVerificationRules.depositPollDelay(40)).isEqualTo(java.time.Duration.ofHours(1));
    }
}
