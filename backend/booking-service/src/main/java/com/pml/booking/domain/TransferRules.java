package com.pml.booking.domain;

import com.pml.booking.domain.model.Ticket;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Who may hand a ticket to whom, and when. Pure, so each refusal is a unit test.
 *
 * <p>A transfer of a ticket the caller does not hold is answered {@code TICKET_UNKNOWN}, not a
 * permission error: telling a stranger the ticket exists is the leak. The remaining refusals are only
 * reachable by the holder, who already knows the ticket, so they say exactly what is wrong.
 */
public final class TransferRules {

    private TransferRules() {
    }

    /** Settings that bound a transfer. */
    public record Limits(Duration ttl, int maxChain, Duration cutoff) {
        public static Limits defaults() {
            return new Limits(Duration.ofHours(48), 5, Duration.ofHours(2));
        }
    }

    /** The reasons a ticket cannot be transferred, carried as the refusal's {@code reason} detail. */
    public enum Reason { CUTOFF_PASSED, CHAIN_LIMIT, EVENT_CANCELLED, EVENT_ENDED }

    /**
     * Null when {@code ticket} may be transferred by {@code holder} to {@code recipient} at {@code now};
     * otherwise the refusal.
     *
     * @param eventStart  when the event starts, null if the catalog could not say
     * @param eventStatus the event's status, null if the catalog could not say
     */
    public static DomainRefusal check(Ticket ticket, String holder, String recipient, Instant eventStart,
                                      EventStatus eventStatus, Instant now, Limits limits) {
        if (holder == null || !holder.equals(ticket.getBuyerId())) {
            return new TranslatedRefusal(ErrorCode.TICKET_UNKNOWN, "no ticket " + ticket.getId());
        }
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            return new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID, "only an issued ticket can be transferred",
                    Map.of("currentStatus", String.valueOf(ticket.getStatus())));
        }
        if (ticket.getActiveTransferId() != null) {
            return new TranslatedRefusal(ErrorCode.TICKET_STATE_INVALID, "this ticket is already in a transfer",
                    Map.of("currentStatus", "TRANSFER_PENDING"));
        }
        if (recipient != null && recipient.equals(holder)) {
            return new TranslatedRefusal(ErrorCode.TRANSFER_TO_SELF, "a ticket cannot be transferred to its holder");
        }
        if (eventStatus == EventStatus.CANCELLED) {
            return notTransferable(Reason.EVENT_CANCELLED);
        }
        if (eventStatus == EventStatus.COMPLETED) {
            return notTransferable(Reason.EVENT_ENDED);
        }
        if (ticket.getTransferCount() >= limits.maxChain()) {
            return notTransferable(Reason.CHAIN_LIMIT);
        }
        // The cutoff is an exclusive boundary: at exactly cutoff-before-start the window is closed.
        if (eventStart != null && !now.isBefore(eventStart.minus(limits.cutoff()))) {
            return notTransferable(Reason.CUTOFF_PASSED);
        }
        return null;
    }

    /**
     * When an offer made at {@code now} lapses: after the ttl, but never later than the event's start,
     * since a ticket must not stay held through the doors opening.
     */
    public static Instant expiryOf(Instant now, Instant eventStart, Limits limits) {
        Instant byTtl = now.plus(limits.ttl());
        return eventStart != null && eventStart.isBefore(byTtl) ? eventStart : byTtl;
    }

    private static DomainRefusal notTransferable(Reason reason) {
        return new TranslatedRefusal(ErrorCode.TICKET_NOT_TRANSFERABLE, "the ticket cannot be transferred",
                Map.of("reason", reason.name()));
    }
}
