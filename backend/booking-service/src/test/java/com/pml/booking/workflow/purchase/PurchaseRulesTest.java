package com.pml.booking.workflow.purchase;

import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Progress;
import com.pml.shared.constants.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001-R6/R8 · checkout rules: one reservation per key, polls, and when nothing is left to wait for")
class PurchaseRulesTest {

    @Test
    @DisplayName("R6 · a buyer's key always names the same reservation; another buyer's same key does not")
    void reservationIdsFollowTheBuyerAndKey() {
        String first = PurchaseRules.reservationIdFor("buyer-1", "key-1");

        assertThat(PurchaseRules.reservationIdFor("buyer-1", "key-1")).isEqualTo(first);
        assertThat(PurchaseRules.reservationIdFor("buyer-2", "key-1")).isNotEqualTo(first);
        assertThat(PurchaseRules.reservationIdFor("buyer-1", "key-2")).isNotEqualTo(first);
        assertThat(UUID.fromString(first)).isNotNull();
    }

    @Test
    @DisplayName("payment polls double from ten seconds to five minutes, and are hourly once escalated")
    void pollsBackOff() {
        assertThat(PurchaseRules.pollDelay(0, false)).isEqualTo(Duration.ofSeconds(10));
        assertThat(PurchaseRules.pollDelay(4, false)).isEqualTo(Duration.ofSeconds(160));
        assertThat(PurchaseRules.pollDelay(5, false)).isEqualTo(Duration.ofMinutes(5));
        assertThat(PurchaseRules.pollDelay(80, false)).isEqualTo(Duration.ofMinutes(5));
        assertThat(PurchaseRules.pollDelay(0, true)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("R8 · confirmed tickets settle; a live hold or a payment that may still arrive does not")
    void settledOnlyWhenNothingCanStillHappen() {
        assertThat(new Progress(PaymentStatus.SUCCEEDED, ReservationStatus.CONFIRMED).settled()).isTrue();
        assertThat(new Progress(PaymentStatus.FAILED, ReservationStatus.RELEASED).settled()).isTrue();
        assertThat(new Progress(PaymentStatus.SUCCEEDED, ReservationStatus.FAILED).settled()).isTrue();
        assertThat(new Progress(null, ReservationStatus.EXPIRED).settled()).isTrue();

        assertThat(new Progress(null, ReservationStatus.HELD).settled()).isFalse();
        assertThat(new Progress(PaymentStatus.PROCESSING, ReservationStatus.HELD).settled()).isFalse();
        assertThat(new Progress(PaymentStatus.SUCCEEDED, ReservationStatus.HELD).settled())
                .as("paid but not yet confirmed is exactly the state recovery exists for").isFalse();
        assertThat(new Progress(PaymentStatus.PROCESSING, ReservationStatus.EXPIRED).settled())
                .as("the seats are back, but the money may still arrive and must be escalated").isFalse();
    }
}
