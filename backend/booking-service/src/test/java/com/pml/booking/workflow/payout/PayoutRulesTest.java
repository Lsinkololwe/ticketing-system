package com.pml.booking.workflow.payout;

import com.pml.booking.domain.PayoutEligibility.Reason;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R1/R7 · payout rules: attempts, backoff, fees, refusal codes")
class PayoutRulesTest {

    @Test
    @DisplayName("R7 · a payout is attempted at most three times")
    void threeAttempts() {
        assertThat(PayoutRules.canRetry(0)).isTrue();
        assertThat(PayoutRules.canRetry(2)).isTrue();
        assertThat(PayoutRules.canRetry(3)).isFalse();
    }

    @Test
    @DisplayName("status polls double from thirty seconds and never wait longer than an hour")
    void pollsBackOffToAnHour() {
        assertThat(PayoutRules.pollDelay(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(PayoutRules.pollDelay(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(PayoutRules.pollDelay(6)).isEqualTo(Duration.ofMinutes(32));
        assertThat(PayoutRules.pollDelay(7)).isEqualTo(Duration.ofHours(1));
        for (int poll = 0; poll < 500; poll++) {
            assertThat(PayoutRules.pollDelay(poll)).isLessThanOrEqualTo(Duration.ofHours(1)).isPositive();
        }
    }

    @Test
    @DisplayName("§4 · a transfer is unconfirmed after exactly three days without an answer")
    void unconfirmedAfterThreeDays() {
        long start = 1_000L;
        assertThat(PayoutRules.unconfirmed(start, start + Duration.ofDays(3).toMillis() - 1)).isFalse();
        assertThat(PayoutRules.unconfirmed(start, start + Duration.ofDays(3).toMillis())).isTrue();
    }

    @Test
    @DisplayName("R7 · bad-destination codes are recognised; a balance problem is not one")
    void badAccountCodes() {
        assertThat(PayoutRules.isBadAccount("INVALID_RECIPIENT")).isTrue();
        assertThat(PayoutRules.isBadAccount(PayoutRules.BAD_ACCOUNT_DETAILS)).isTrue();
        assertThat(PayoutRules.isBadAccount("INSUFFICIENT_BALANCE")).isFalse();
        assertThat(PayoutRules.isBadAccount(null)).isFalse();
    }

    @Test
    @DisplayName("R1 · every eligibility failure maps to a declared refusal code")
    void everyReasonHasACode() {
        assertThat(PayoutRules.codeFor(Reason.HOLD_NOT_ELAPSED)).isEqualTo(ErrorCode.PAYOUT_WINDOW_NOT_OPEN);
        assertThat(PayoutRules.codeFor(Reason.OPEN_DISPUTES)).isEqualTo(ErrorCode.PAYOUT_WINDOW_NOT_OPEN);
        assertThat(PayoutRules.codeFor(Reason.BELOW_MINIMUM)).isEqualTo(ErrorCode.PAYOUT_BELOW_MINIMUM);
        assertThat(PayoutRules.codeFor(Reason.NO_ESCROW_ACCOUNT)).isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN);
        for (Reason reason : Reason.values()) {
            assertThat(PayoutRules.codeFor(reason)).isNotNull();
        }
    }
}
