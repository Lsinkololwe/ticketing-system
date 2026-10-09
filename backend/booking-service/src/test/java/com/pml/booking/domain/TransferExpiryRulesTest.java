package com.pml.booking.domain;

import com.pml.booking.domain.model.Ticket;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The boundaries of a transfer: chain, cutoff, expiry, and which refusal comes first. */
@Tag("L1")
@Tag("ET-TKT-004")
@DisplayName("ET-TKT-004 · transfer boundaries: chain limit, cutoff, offer expiry, and the order of refusals")
class TransferExpiryRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofDays(3));
    private static final TransferRules.Limits LIMITS = TransferRules.Limits.defaults();

    private static Ticket ticket() {
        return Ticket.builder().id("t").buyerId("holder").status(TicketStatus.ISSUED).transferCount(0).build();
    }

    @Test
    @DisplayName("the defaults are 48 hours, five hand-overs and a two hour cutoff")
    void defaults() {
        assertThat(LIMITS.ttl()).isEqualTo(Duration.ofHours(48));
        assertThat(LIMITS.maxChain()).isEqualTo(5);
        assertThat(LIMITS.cutoff()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("the fifth hand-over is allowed and the sixth is not")
    void chain() {
        Ticket fourth = ticket();
        fourth.setTransferCount(4);
        assertThat(TransferRules.check(fourth, "holder", "r", START, EventStatus.PUBLISHED, NOW, LIMITS)).isNull();
        fourth.setTransferCount(5);
        var refused = TransferRules.check(fourth, "holder", "r", START, EventStatus.PUBLISHED, NOW, LIMITS);
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);
        assertThat(refused.details()).containsEntry("reason", "CHAIN_LIMIT");
    }

    @Test
    @DisplayName("an offer is open until two hours before the start, and closed at that instant")
    void cutoff() {
        Instant closes = START.minus(Duration.ofHours(2));
        assertThat(TransferRules.check(ticket(), "holder", "r", START, EventStatus.PUBLISHED, closes.minusMillis(1), LIMITS)).isNull();
        assertThat(TransferRules.check(ticket(), "holder", "r", START, EventStatus.PUBLISHED, closes, LIMITS).details()).containsEntry("reason", "CUTOFF_PASSED");
        assertThat(TransferRules.check(ticket(), "holder", "r", null, null, NOW, LIMITS)).as("a catalog that cannot say does not block the holder").isNull();
    }

    @Test
    @DisplayName("an offer lapses after the ttl, or at the start of the event if that comes first")
    void expiry() {
        assertThat(TransferRules.expiryOf(NOW, START, LIMITS)).isEqualTo(NOW.plus(Duration.ofHours(48)));
        assertThat(TransferRules.expiryOf(NOW, NOW.plus(Duration.ofHours(10)), LIMITS)).isEqualTo(NOW.plus(Duration.ofHours(10)));
        assertThat(TransferRules.expiryOf(NOW, null, LIMITS)).isEqualTo(NOW.plus(Duration.ofHours(48)));
    }

    @Test
    @DisplayName("refusals come in a fixed order, and a stranger only ever hears 'unknown ticket'")
    void order() {
        Ticket other = ticket();
        other.setStatus(TicketStatus.REFUNDED);
        other.setTransferCount(9);
        assertThat(TransferRules.check(other, "stranger", "r", START, EventStatus.CANCELLED, NOW, LIMITS).errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(TransferRules.check(other, "holder", "r", START, EventStatus.CANCELLED, NOW, LIMITS).errorCode()).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
        Ticket issued = ticket();
        issued.setTransferCount(9);
        assertThat(TransferRules.check(issued, "holder", "holder", START, EventStatus.CANCELLED, NOW, LIMITS).errorCode()).isEqualTo(ErrorCode.TRANSFER_TO_SELF);
        assertThat(TransferRules.check(issued, "holder", "r", START, EventStatus.CANCELLED, NOW, LIMITS).details()).containsEntry("reason", "EVENT_CANCELLED");
        assertThat(TransferRules.check(issued, "holder", "r", START, EventStatus.COMPLETED, NOW, LIMITS).details()).containsEntry("reason", "EVENT_ENDED");
        assertThat(TransferRules.check(issued, "holder", "r", START, EventStatus.PUBLISHED, NOW, LIMITS).details()).containsEntry("reason", "CHAIN_LIMIT");
        issued.setActiveTransferId("open");
        assertThat(TransferRules.check(issued, "holder", "r", START, EventStatus.PUBLISHED, NOW, LIMITS).errorCode()).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
    }
}
