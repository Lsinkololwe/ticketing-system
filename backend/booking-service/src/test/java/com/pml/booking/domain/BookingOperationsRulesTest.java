package com.pml.booking.domain;

import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.booking.domain.model.Ticket;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure rules behind the booking, transfer, refund, messaging, sales-series, risk and dual-control operations. */
@Tag("L1")
@Tag("ET-TKT-002")
@Tag("ET-TKT-004")
@Tag("ET-FIN-004")
@DisplayName("Booking operations · pure rules")
class BookingOperationsRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    // ---- booking numbers and status ----

    @Test
    void bookingNumberIsYearAndPaddedSequence() {
        assertThat(BookingRules.format(NOW, 1234)).isEqualTo("BK-2026-00001234");
        assertThat(BookingRules.sequenceFor(NOW)).isEqualTo("booking-2026");
        // UTC year boundary
        assertThat(BookingRules.format(Instant.parse("2025-12-31T23:59:59Z"), 7)).isEqualTo("BK-2025-00000007");
    }

    private static Booking booking(BookingStatus status, String refunded, String total, int tickets, int cancelled, String late) {
        return Booking.builder().status(status).refundedAmount(new BigDecimal(refunded)).totalAmount(new BigDecimal(total))
                .ticketCount(tickets).cancelledTicketCount(cancelled).lateRefundStatus(late).build();
    }

    @Test
    void effectiveStatusDerivesFromFacts() {
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.CONFIRMED, "0", "100", 2, 0, null))).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.CONFIRMED, "40", "100", 2, 0, null))).isEqualTo(BookingStatus.PARTIALLY_REFUNDED);
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.CONFIRMED, "100", "100", 2, 0, null))).isEqualTo(BookingStatus.REFUNDED);
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.CONFIRMED, "0", "100", 2, 2, null))).isEqualTo(BookingStatus.CANCELLED);
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.EXPIRED, "0", "100", 2, 0, null))).isEqualTo(BookingStatus.EXPIRED);
        assertThat(BookingRules.effectiveStatus(booking(BookingStatus.EXPIRED, "0", "100", 2, 0, "PROCESSING")))
                .isEqualTo(BookingStatus.PAID_AFTER_EXPIRY_AUTO_REFUNDED);
        assertThat(BookingRules.effectiveStatus(Booking.builder().build())).isEqualTo(BookingStatus.PENDING);
    }

    // ---- transfer ----

    private static Ticket ticket() {
        return Ticket.builder().id("t1").buyerId("u1").status(TicketStatus.ISSUED).transferCount(0)
                .price(new BigDecimal("100.00")).refundedAmount(BigDecimal.ZERO).build();
    }

    private static ErrorCode code(DomainRefusal r) {
        return r == null ? null : r.errorCode();
    }

    @Test
    void transferRefusals() {
        var limits = TransferRules.Limits.defaults();
        Instant start = NOW.plus(Duration.ofDays(3));
        assertThat(TransferRules.check(ticket(), "u1", "u2", start, EventStatus.PUBLISHED, NOW, limits)).isNull();
        assertThat(code(TransferRules.check(ticket(), "stranger", "u2", start, null, NOW, limits))).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(code(TransferRules.check(ticket(), "u1", "u1", start, null, NOW, limits))).isEqualTo(ErrorCode.TRANSFER_TO_SELF);
        assertThat(code(TransferRules.check(ticket().toBuilder().status(TicketStatus.VALIDATED).build(), "u1", "u2", start, null, NOW, limits)))
                .isEqualTo(ErrorCode.TICKET_STATE_INVALID);
        assertThat(code(TransferRules.check(ticket().toBuilder().activeTransferId("x").build(), "u1", "u2", start, null, NOW, limits)))
                .isEqualTo(ErrorCode.TICKET_STATE_INVALID);
        assertThat(code(TransferRules.check(ticket().toBuilder().transferCount(5).build(), "u1", "u2", start, null, NOW, limits)))
                .isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);
        assertThat(code(TransferRules.check(ticket(), "u1", "u2", start, EventStatus.CANCELLED, NOW, limits)))
                .isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);
    }

    @Test
    void transferCutoffIsExclusiveAndExpiryNeverPassesStart() {
        var limits = TransferRules.Limits.defaults();
        Instant start = NOW.plus(limits.cutoff());
        assertThat(code(TransferRules.check(ticket(), "u1", "u2", start, null, NOW, limits))).isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);
        assertThat(TransferRules.check(ticket(), "u1", "u2", start.plusSeconds(1), null, NOW, limits)).isNull();
        assertThat(TransferRules.expiryOf(NOW, NOW.plus(Duration.ofHours(5)), limits)).isEqualTo(NOW.plus(Duration.ofHours(5)));
        assertThat(TransferRules.expiryOf(NOW, NOW.plus(Duration.ofDays(9)), limits)).isEqualTo(NOW.plus(limits.ttl()));
        assertThat(TransferRules.expiryOf(NOW, null, limits)).isEqualTo(NOW.plus(limits.ttl()));
    }

    // ---- refunds ----

    @Test
    void refundAmountRules() {
        BigDecimal remaining = new BigDecimal("60.00");
        assertThat(RefundEligibility.checkAmount(new BigDecimal("60.00"), remaining)).isNull();
        assertThat(code(RefundEligibility.checkAmount(new BigDecimal("60.01"), remaining))).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(code(RefundEligibility.checkAmount(BigDecimal.ZERO, remaining))).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(code(RefundEligibility.checkAmount(new BigDecimal("1.234"), remaining))).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(code(RefundEligibility.checkAmount(null, remaining))).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    void remainingAndHolderEligibility() {
        Ticket partly = ticket().toBuilder().refundedAmount(new BigDecimal("40.00")).build();
        assertThat(RefundEligibility.remaining(partly)).isEqualByComparingTo("60.00");
        assertThat(RefundEligibility.of(partly).eligible()).isTrue();
        Ticket full = ticket().toBuilder().refundedAmount(new BigDecimal("100.00")).build();
        assertThat(code(RefundEligibility.of(full).refusal())).isEqualTo(ErrorCode.REFUND_ALREADY_ISSUED);
        Ticket given = ticket().toBuilder().originalBuyerId("someone-else").build();
        assertThat(code(RefundEligibility.forHolder(given).refusal())).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(RefundEligibility.of(given).eligible()).isTrue();
        assertThat(code(RefundEligibility.of(ticket().toBuilder().activeTransferId("x").build()).refusal())).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
    }

    @Test
    void refundSplitAddsUpExactly() {
        var whole = RefundSplit.of(new BigDecimal("100.00"), new BigDecimal("5.00"), new BigDecimal("100.00"));
        assertThat(whole.whole()).isTrue();
        assertThat(whole.commissionShare().add(whole.escrowDebit())).isEqualByComparingTo("100.00");
        var part = RefundSplit.of(new BigDecimal("30.00"), new BigDecimal("1.00"), new BigDecimal("10.00"));
        assertThat(part.whole()).isFalse();
        assertThat(part.commissionShare()).isEqualByComparingTo("0.33");
        assertThat(part.commissionShare().add(part.escrowDebit())).isEqualByComparingTo("10.00");
    }

    // ---- messaging, names ----

    @Test
    void holderMessageLimits() {
        assertThat(HolderMessageRules.check("Doors moved", "Doors now open at 18:00 sharp.")).isEmpty();
        assertThat(HolderMessageRules.check("ab", "short")).hasSize(2);
        assertThat(HolderMessageRules.check("Valid subject", "x".repeat(501))).hasSize(1);
        assertThat(HolderMessageRules.clean("  hi\u0007there \n")).isEqualTo("hithere");
    }

    @Test
    void displayNamesHideSurnames() {
        assertThat(DisplayNames.firstAndInitial("Mary Kasonde Phiri")).isEqualTo("Mary P.");
        assertThat(DisplayNames.firstAndInitial("Mary")).isEqualTo("Mary");
        assertThat(DisplayNames.firstAndInitial("  ")).isNull();
    }

    // ---- sales buckets ----

    @Test
    void dayBucketsAreLusakaDaysAndQuietDaysArePresent() {
        // 22:30 UTC is 00:30 next day in Lusaka (UTC+2)
        var floor = SalesBuckets.floor(Instant.parse("2026-10-05T22:30:00Z"), SalesBuckets.Bucket.DAY);
        assertThat(floor.toLocalDate().toString()).isEqualTo("2026-10-06");
        var days = SalesBuckets.between(Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-04T08:00:00Z"), SalesBuckets.Bucket.DAY);
        assertThat(days).hasSize(4);
        var week = SalesBuckets.floor(Instant.parse("2026-10-07T08:00:00Z"), SalesBuckets.Bucket.WEEK);
        assertThat(week.getDayOfWeek().toString()).isEqualTo("MONDAY");
    }

    // ---- risk ----

    @Test
    void riskScoresFlagsAndCaps() {
        var calm = PaymentRiskRules.assess(new PaymentRiskRules.Facts(new BigDecimal("50"), true, true, true, 0, 0, 0, 0));
        assertThat(calm.flags()).isEmpty();
        assertThat(calm.level()).isEqualTo(PaymentRiskRules.Level.LOW);
        var bad = PaymentRiskRules.assess(new PaymentRiskRules.Facts(new BigDecimal("5000"), true, false, false, 7, 4, 5, 4));
        assertThat(bad.score()).isEqualTo(100);
        assertThat(bad.level()).isEqualTo(PaymentRiskRules.Level.HIGH);
        assertThat(bad.flags()).contains(PaymentRiskRules.HIGH_VALUE, PaymentRiskRules.BURST_ATTEMPTS, PaymentRiskRules.BAD_WEBHOOK_SIGNATURE);
        var rapid = PaymentRiskRules.assess(new PaymentRiskRules.Facts(new BigDecimal("50"), false, false, null, 3, 0, 0, 0));
        assertThat(rapid.flags()).containsExactly(PaymentRiskRules.RAPID_ATTEMPTS);
        assertThat(PaymentRiskRules.levelOf(30)).isEqualTo(PaymentRiskRules.Level.MEDIUM);
    }

    // ---- dual control ----

    private static RecoveryProposal proposal(RecoveryAction action, Instant expires) {
        return RecoveryProposal.builder().action(action).status(RecoveryProposalStatus.PENDING)
                .proposedById("maker").expiresAt(expires).build();
    }

    @Test
    void makerCannotCheckAndRolesAndExpiryAreEnforced() {
        var p = proposal(RecoveryAction.TRANSFER_PLATFORM_FUNDS, NOW.plusSeconds(60));
        assertThat(DualControlRules.checkConfirmation(p, "checker", List.of("ROLE_FINANCE"), NOW)).isNull();
        assertThat(code(DualControlRules.checkConfirmation(p, "maker", List.of("ROLE_FINANCE"), NOW))).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(code(DualControlRules.checkConfirmation(p, "checker", List.of("ROLE_ORGANIZER"), NOW))).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(code(DualControlRules.checkConfirmation(p, "checker", List.of("ROLE_FINANCE"), NOW.plusSeconds(60))))
                .isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        var force = proposal(RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS, NOW.plusSeconds(60));
        assertThat(DualControlRules.holdsRole(List.of("ROLE_ADMIN"), RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS)).isFalse();
        assertThat(DualControlRules.holdsRole(List.of("ROLE_SUPER_ADMIN"), RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS)).isTrue();
        assertThat(DualControlRules.holdsRole(List.of("ROLE_ADMIN"), RecoveryAction.WRITE_OFF_CHARGEBACK)).isTrue();
        assertThat(DualControlRules.checkConfirmation(force, "checker", List.of("ROLE_SUPER_ADMIN"), NOW)).isNull();
        assertThat(DualControlRules.checkReason("too short", "x")).isNotNull();
        assertThat(DualControlRules.checkReason("a sufficiently long reason text", "x")).isNull();
    }
}
