package com.pml.booking.refund;

import com.pml.booking.domain.model.CommissionRecord.CommissionStatus;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.it.RefundHarness;
import com.pml.booking.it.RefundHarness.Sale;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Refunds, whole and partial, move exactly the money they should: amounts are bounded by what remains,
 * a refund is applied once however often it is told to complete, the ledger entries balance, and
 * simultaneous requests for one ticket collapse into one.
 */
@Tag("L2")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004 · refunds: bounded amounts, applied once, balanced ledger, one open refund per ticket")
class RefundMoneyTest {

    private static RefundHarness h;

    @BeforeAll
    static void start() {
        h = new RefundHarness(MongoReplicaSet.connectionString());
    }

    @AfterAll
    static void stop() {
        h.close();
    }

    private RefundRequest refund(Sale sale, String amount) {
        return asAdmin("admin-1", h.process.requestAsAdmin(sale.ticket().getId(), "customer asked", "admin-1", true,
                amount == null ? null : new BigDecimal(amount)), "ROLE_ADMIN").block();
    }

    /** Tells the workflow the provider has answered until it has completed the refund. */
    private RefundRequest complete(RefundRequest request) {
        await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(400)).until(() -> {
            RefundRequest current = h.refundRepo.findById(request.getId()).block();
            if (current.getStatus() == RefundRequestStatus.COMPLETED) {
                return true;
            }
            if (current.getPawaPayRefundId() != null) {
                h.process.providerCallback(current.getPawaPayRefundId(), "COMPLETED").block();
            }
            return false;
        });
        return h.refundRepo.findById(request.getId()).block();
    }

    private Ticket ticket(Sale sale) {
        return h.ticketRepo.findById(sale.ticket().getId()).block();
    }

    private EventEscrowAccount escrow(Sale sale) {
        return h.escrowRepo.findByEventId(sale.eventId()).block();
    }

    private static void assertBalanced(List<JournalEntry> entries) {
        assertThat(entries).isNotEmpty();
        for (JournalEntry entry : entries) {
            BigDecimal debits = entry.getLines().stream().map(l -> l.getDebit() == null ? BigDecimal.ZERO : l.getDebit())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entry.getLines().stream().map(l -> l.getCredit() == null ? BigDecimal.ZERO : l.getCredit())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(debits).as("entry %s debits equal credits", entry.getId()).isEqualByComparingTo(credits);
        }
    }

    private List<JournalEntry> entriesFor(RefundRequest... requests) {
        return Flux.fromArray(requests).concatMap(r -> h.journalRepo.findAll().filter(e -> r.getId().equals(e.getCorrelationId()))).collectList().block();
    }

    @Test
    @DisplayName("two part refunds then the rest: ticket refunded only when the sum reaches its price, escrow and commission add up, ledger balances")
    void partialRefundsAddUp() {
        Sale sale = h.sale(new BigDecimal("100.00"));

        RefundRequest first = complete(refund(sale, "40.00"));
        assertThat(first.getRefundAmount()).isEqualByComparingTo("40.00");
        assertThat(ticket(sale).getStatus()).as("a part refund leaves the seat admissible").isEqualTo(TicketStatus.ISSUED);
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("40.00");
        assertThat(first.getCommissionShare().add(first.getEscrowDebit())).isEqualByComparingTo("40.00");

        RefundRequest second = complete(refund(sale, "35.50"));
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("75.50");
        assertThat(ticket(sale).getStatus()).isEqualTo(TicketStatus.ISSUED);

        RefundRequest rest = complete(refund(sale, null));
        assertThat(rest.getRefundAmount()).as("no amount refunds what remains").isEqualByComparingTo("24.50");
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("100.00");
        assertThat(ticket(sale).getStatus()).isEqualTo(TicketStatus.REFUNDED);

        BigDecimal commissionBack = first.getCommissionShare().add(second.getCommissionShare()).add(rest.getCommissionShare());
        BigDecimal escrowOut = first.getEscrowDebit().add(second.getEscrowDebit()).add(rest.getEscrowDebit());
        assertThat(commissionBack).as("the whole 5 percent commission is given back, no more").isEqualByComparingTo("5.00");
        assertThat(escrowOut).as("the organizer's 95.00 is entirely given back").isEqualByComparingTo("95.00");
        assertThat(escrow(sale).getCurrentBalance()).isEqualByComparingTo("0.00");
        assertThat(h.commissionRepo.findByTicketId(sale.ticket().getId()).block().getStatus()).isEqualTo(CommissionStatus.CANCELLED);
        assertBalanced(entriesFor(first, second, rest));
        assertThat(entriesFor(first, second, rest)).as("one refund entry per refund").hasSize(3);
        assertBalanced(h.journalRepo.findAll().collectList().block());

        assertThat(refusal(Mono.defer(() -> h.process.requestAsAdmin(sale.ticket().getId(), "again", "admin-1", true, new BigDecimal("1.00"))))
                .errorCode()).isIn(ErrorCode.REFUND_ALREADY_ISSUED, ErrorCode.TICKET_STATE_INVALID);
    }

    @Test
    @DisplayName("an amount of zero, below zero, finer than a ngwee, or above what remains is refused and writes nothing")
    void amountBounds() {
        Sale sale = h.sale(new BigDecimal("100.00"));
        for (String bad : new String[]{"0", "-5.00", "10.001", "100.01"}) {
            DomainRefusal refused = refusal(Mono.defer(() -> h.process.requestAsAdmin(sale.ticket().getId(), "bad amount", "admin-1", true, new BigDecimal(bad))));
            assertThat(refused.errorCode()).as(bad).isIn(ErrorCode.COMMAND_NOT_WELL_FORMED, ErrorCode.REFUND_NOT_PERMITTED);
        }
        assertThat(h.refundRepo.findByTicketId(sale.ticket().getId()).collectList().block()).isEmpty();

        RefundRequest part = complete(refund(sale, "60.00"));
        DomainRefusal over = refusal(Mono.defer(() -> h.process.requestAsAdmin(sale.ticket().getId(), "too much", "admin-1", true, new BigDecimal("40.01"))));
        assertThat(over.errorCode()).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(part.getRefundAmount()).isEqualByComparingTo("60.00");
        assertThat(h.refundRepo.findByTicketId(sale.ticket().getId()).collectList().block()).hasSize(1);
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("a refund the provider is told to complete again changes nothing: one provider call, one escrow debit, one set of ledger entries")
    void completingTwiceIsOnce() {
        Sale sale = h.sale(new BigDecimal("80.00"));
        int before = h.providerRefunds.get();
        RefundRequest done = complete(refund(sale, "30.00"));

        for (int i = 0; i < 5; i++) {
            h.refunds.handleRefundCallback(done.getPawaPayRefundId(), "COMPLETED", "prov-ref", null, null).block();
        }
        assertThatThrownBy(() -> h.refunds.processRefund(done.getId()).block())
                .as("a refund that is already complete is not processed again").hasMessageContaining("COMPLETED");

        assertThat(h.providerRefunds.get() - before).as("the provider was asked once").isEqualTo(1);
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("30.00");
        assertThat(escrow(sale).getCurrentBalance()).isEqualByComparingTo(new BigDecimal("76.00").subtract(done.getEscrowDebit()));
        assertThat(entriesFor(done)).hasSize(1);
        assertBalanced(entriesFor(done));
    }

    @Test
    @DisplayName("a refund completing from two places at the same instant is applied once")
    void simultaneousCompletionsAreOnce() {
        Sale sale = h.sale(new BigDecimal("80.00"));
        RefundRequest asked = refund(sale, "30.00");
        await().atMost(Duration.ofSeconds(20)).until(() -> h.refundRepo.findById(asked.getId()).block().getStatus() == RefundRequestStatus.PROCESSING);
        String providerId = h.refundRepo.findById(asked.getId()).block().getPawaPayRefundId();

        List<Object> outcomes = Flux.range(0, 8)
                .flatMap(i -> h.refunds.handleRefundCallback(providerId, "COMPLETED", "ref-" + i, null, null)
                        .<Object>map(r -> r).onErrorResume(e -> Mono.just(e)), 8)
                .collectList().block();
        await().atMost(Duration.ofSeconds(30)).until(() -> h.refundRepo.findById(asked.getId()).block().getStatus() == RefundRequestStatus.COMPLETED);

        assertThat(ticket(sale).getRefundedAmount()).as("credited once, whatever the outcomes %s", outcomes).isEqualByComparingTo("30.00");
        assertThat(entriesFor(asked)).as("one ledger entry for the refund, not one per caller").hasSize(1);
        assertBalanced(entriesFor(asked));
    }

    @Test
    @DisplayName("eight simultaneous refund requests for one ticket produce one refund request and one provider refund")
    void simultaneousRequestsCollapse() {
        Sale sale = h.sale(new BigDecimal("100.00"));
        int before = h.providerRefunds.get();

        List<Object> outcomes = Flux.range(0, 8)
                .flatMap(i -> asAdmin("admin-" + i, h.process.requestAsAdmin(sale.ticket().getId(), "double click", "admin-" + i, true, new BigDecimal("50.00")), "ROLE_ADMIN")
                        .<Object>map(r -> r).onErrorResume(e -> Mono.just(e)), 8)
                .collectList().block();

        assertThat(h.refundRepo.findByTicketId(sale.ticket().getId()).collectList().block()).as("outcomes %s", outcomes).hasSize(1);
        RefundRequest only = h.refundRepo.findByTicketId(sale.ticket().getId()).blockFirst();
        complete(only);
        assertThat(h.providerRefunds.get() - before).isEqualTo(1);
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("after a part refund has completed, a new part refund is a new request, not the old one handed back")
    void aCompletedRefundDoesNotBlockTheNext() {
        Sale sale = h.sale(new BigDecimal("100.00"));
        RefundRequest first = complete(refund(sale, "10.00"));
        RefundRequest next = refund(sale, "20.00");
        assertThat(next.getId()).isNotEqualTo(first.getId());
        assertThat(next.getRefundAmount()).isEqualByComparingTo("20.00");
        complete(next);
        assertThat(ticket(sale).getRefundedAmount()).isEqualByComparingTo("30.00");
    }
}
