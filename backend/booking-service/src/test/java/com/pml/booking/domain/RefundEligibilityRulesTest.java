package com.pml.booking.domain;

import com.pml.booking.domain.model.Ticket;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** What can be refunded on a seat, to whom, and the shape of an amount: every boundary of the partial-refund rule. */
@Tag("L1")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004 · refund eligibility: status, transfer, what remains, who may ask, and the shape of an amount")
class RefundEligibilityRulesTest {

    private static Ticket seat(TicketStatus status, String price, String refunded) {
        return Ticket.builder().id("t-1").buyerId("holder").status(status).price(new BigDecimal(price))
                .refundedAmount(refunded == null ? null : new BigDecimal(refunded)).build();
    }

    @Test
    @DisplayName("only an issued or admitted seat can be refunded; every other state names itself in the refusal")
    void statusMatrix() {
        for (TicketStatus status : TicketStatus.values()) {
            var verdict = RefundEligibility.of(seat(status, "100", "0"));
            if (status == TicketStatus.ISSUED || status == TicketStatus.VALIDATED) {
                assertThat(verdict.eligible()).as(status.name()).isTrue();
            } else {
                assertThat(verdict.refusal().errorCode()).as(status.name()).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
                assertThat(verdict.refusal().details()).containsEntry("currentStatus", status.name());
            }
        }
    }

    @Test
    @DisplayName("what remains is the price less what came back, never negative, and a missing figure counts as nothing refunded")
    void remaining() {
        assertThat(RefundEligibility.remaining(seat(TicketStatus.ISSUED, "100.00", "40.00"))).isEqualByComparingTo("60.00");
        assertThat(RefundEligibility.remaining(seat(TicketStatus.ISSUED, "100.00", null))).isEqualByComparingTo("100.00");
        assertThat(RefundEligibility.remaining(seat(TicketStatus.ISSUED, "100.00", "130.00"))).as("clamped").isEqualByComparingTo("0");
        assertThat(RefundEligibility.of(seat(TicketStatus.ISSUED, "100.00", "100.00")).refusal().errorCode()).isEqualTo(ErrorCode.REFUND_ALREADY_ISSUED);
    }

    @Test
    @DisplayName("a seat in a transfer that has not resolved cannot be refunded")
    void heldByATransfer() {
        Ticket held = seat(TicketStatus.ISSUED, "100", "0");
        held.setActiveTransferId("transfer-1");
        var verdict = RefundEligibility.of(held);
        assertThat(verdict.refusal().errorCode()).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
        assertThat(verdict.refusal().details()).containsEntry("currentStatus", "TRANSFER_PENDING");
    }

    @Test
    @DisplayName("the holder asks for a seat they bought; a seat given to them is refunded by the organizer or the platform, to whoever paid")
    void holderRules() {
        Ticket bought = seat(TicketStatus.ISSUED, "100", "0");
        assertThat(RefundEligibility.isHolder(bought, "holder")).isTrue();
        assertThat(RefundEligibility.isHolder(bought, "someone")).isFalse();
        assertThat(RefundEligibility.isHolder(bought, null)).isFalse();
        assertThat(RefundEligibility.forHolder(bought).eligible()).isTrue();
        bought.setOriginalBuyerId("holder");
        assertThat(RefundEligibility.forHolder(bought).eligible()).as("the payer is still the holder").isTrue();

        Ticket gifted = seat(TicketStatus.ISSUED, "100", "0");
        gifted.setOriginalBuyerId("the-payer");
        var verdict = RefundEligibility.forHolder(gifted);
        assertThat(verdict.refusal().errorCode()).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(verdict.refusal().details()).containsEntry("reason", "TRANSFERRED_TICKET");
        assertThat(RefundEligibility.of(gifted).eligible()).as("an operator is not bound by that rule").isTrue();
    }

    @Test
    @DisplayName("an amount is positive, to the ngwee, and not above what remains; one ngwee over is over")
    void amountShape() {
        BigDecimal remaining = new BigDecimal("60.00");
        assertThat(RefundEligibility.checkAmount(new BigDecimal("60.00"), remaining)).isNull();
        assertThat(RefundEligibility.checkAmount(new BigDecimal("0.01"), remaining)).isNull();
        assertThat(RefundEligibility.checkAmount(new BigDecimal("12.5"), remaining)).isNull();
        assertThat(RefundEligibility.checkAmount(new BigDecimal("12.500"), remaining)).as("trailing zeros are not a third decimal").isNull();
        assertThat(RefundEligibility.checkAmount(new BigDecimal("60.01"), remaining).errorCode()).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(RefundEligibility.checkAmount(new BigDecimal("60.01"), remaining).details()).containsEntry("reason", "AMOUNT_EXCEEDS_REMAINING")
                .containsEntry("remaining", "60.00");
        assertThat(RefundEligibility.checkAmount(BigDecimal.ZERO, remaining).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(RefundEligibility.checkAmount(new BigDecimal("-1"), remaining).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(RefundEligibility.checkAmount(null, remaining).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(RefundEligibility.checkAmount(new BigDecimal("10.001"), remaining).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }
}
