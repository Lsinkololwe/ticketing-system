package com.pml.booking.domain;

import com.pml.booking.domain.model.Ticket;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Whether a seat can be refunded now, and for how much.
 *
 * <p>Pure, so every refusal is a unit test: the status the seat is in, a transfer still pending, what
 * has already come back, a refund the holder is not entitled to ask for because somebody else paid,
 * and an amount that is not a positive sum of ngwee within what remains.
 */
public final class RefundEligibility {

    private RefundEligibility() {
    }

    /** The answer for one seat: the sum still refundable, or the refusal that explains why not. */
    public record Verdict(BigDecimal remaining, DomainRefusal refusal) {
        public boolean eligible() {
            return refusal == null;
        }
    }

    /** What remains refundable on a seat: its price less the refunds that have completed. */
    public static BigDecimal remaining(Ticket ticket) {
        BigDecimal refunded = ticket.getRefundedAmount() == null ? BigDecimal.ZERO : ticket.getRefundedAmount();
        BigDecimal left = (ticket.getPrice() == null ? BigDecimal.ZERO : ticket.getPrice()).subtract(refunded);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }

    /** The conditions that hold for any initiator: status, transfer, and something left to refund. */
    public static Verdict of(Ticket ticket) {
        BigDecimal remaining = remaining(ticket);
        if (ticket.getStatus() == null || !ticket.getStatus().isRefundable()) {
            return refused(remaining, new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID,
                    "the ticket is " + ticket.getStatus(),
                    Map.of("currentStatus", String.valueOf(ticket.getStatus()))));
        }
        if (ticket.getActiveTransferId() != null) {
            return refused(remaining, new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID,
                    "the ticket is in a transfer that has not resolved",
                    Map.of("currentStatus", "TRANSFER_PENDING")));
        }
        if (remaining.signum() <= 0) {
            return refused(remaining, new TranslatedRefusal(ErrorCode.REFUND_ALREADY_ISSUED,
                    "the whole price of this ticket has already been refunded"));
        }
        return new Verdict(remaining, null);
    }

    /**
     * The extra rule for the person holding the ticket: a ticket they were given, rather than bought,
     * is not theirs to refund — the money goes back to whoever paid, who has since passed it on.
     */
    public static Verdict forHolder(Ticket ticket) {
        Verdict general = of(ticket);
        if (!general.eligible()) {
            return general;
        }
        if (ticket.getOriginalBuyerId() != null && !ticket.getOriginalBuyerId().equals(ticket.getBuyerId())) {
            return refused(general.remaining(), new TranslatedRefusal(ErrorCode.REFUND_NOT_PERMITTED,
                    "a ticket that was transferred to you can be refunded by the organizer or the platform, not by you",
                    Map.of("reason", "TRANSFERRED_TICKET")));
        }
        return general;
    }

    /** Whether {@code requester} holds the ticket. */
    public static boolean isHolder(Ticket ticket, String requester) {
        return requester != null && requester.equals(ticket.getBuyerId());
    }

    /** Null when {@code amount} is a positive sum of ngwee within {@code remaining}; otherwise the refusal. */
    public static DomainRefusal checkAmount(BigDecimal amount, BigDecimal remaining) {
        if (amount == null || amount.signum() <= 0) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a refund amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a refund amount has at most two decimal places");
        }
        if (amount.compareTo(remaining) > 0) {
            return new TranslatedRefusal(ErrorCode.REFUND_NOT_PERMITTED,
                    "the amount exceeds what remains refundable on this ticket",
                    Map.of("reason", "AMOUNT_EXCEEDS_REMAINING", "remaining", remaining.toPlainString()));
        }
        return null;
    }

    private static Verdict refused(BigDecimal remaining, DomainRefusal refusal) {
        return new Verdict(remaining, refusal);
    }
}
