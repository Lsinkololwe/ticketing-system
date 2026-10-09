package com.pml.booking.refund;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.it.RefundHarness;
import com.pml.booking.it.RefundHarness.Sale;
import com.pml.booking.service.TicketService;
import com.pml.booking.web.graphql.dto.CreateRefundRequestInput;
import com.pml.booking.web.graphql.mutation.RefundRequestMutationResolver;
import com.pml.booking.web.graphql.mutation.TicketMutationResolver;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.asCustomer;
import static com.pml.booking.it.BookingFixture.asOrganizer;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Who may ask for, withdraw and decide a refund: the holder asks for their own seat, only the requester
 * withdraws and only before a decision, the organizer's team refunds only its own events, and a refund goes
 * to whoever paid.
 */
@Tag("L2")
@Tag("ET-FIN-004")
@DisplayName("ET-FIN-004 · refund access: holder asks, requester withdraws, the event's organizer refunds, nobody else")
class RefundAccessTest {

    private static RefundHarness h;
    private static World world;
    private static RefundRequestMutationResolver refunds;
    private static TicketMutationResolver tickets;

    @BeforeAll
    static void start() {
        h = new RefundHarness(MongoReplicaSet.connectionString());
        world = new World();
        refunds = new RefundRequestMutationResolver(h.process);
        tickets = new TicketMutationResolver(Mockito.mock(TicketService.class), h.ticketRepo, world.access, h.process);
    }

    @AfterAll
    static void stop() {
        h.close();
    }

    private static CreateRefundRequestInput input(Sale sale) {
        return new CreateRefundRequestInput(sale.ticket().getId(), "I cannot attend", null, null);
    }

    private static Sale open(Sale sale) {
        world.event(sale.eventId(), sale.organizationId());
        return sale;
    }

    // ---- the holder asks -----------------------------------------------------------------------

    @Test
    @DisplayName("the holder asks for their own seat; someone else is told the ticket does not exist; a seat given to them is not theirs to refund")
    void holderAsks() {
        Sale sale = h.sale(new BigDecimal("60.00"));

        assertThat(refusal(asCustomer("stranger", refunds.createUserRefundRequest(input(sale)))).errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(h.refundRepo.findByTicketId(sale.ticket().getId()).collectList().block()).isEmpty();

        RefundRequest asked = asCustomer(sale.buyerId(), refunds.createUserRefundRequest(input(sale))).block();
        assertThat(asked.getStatus()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(asked.getRequestedBy()).isEqualTo(sale.buyerId());
        assertThat(asked.getRefundAmount()).isEqualByComparingTo("60.00");

        Sale gifted = h.sale(new BigDecimal("60.00"));
        Ticket seat = gifted.ticket();
        seat.setOriginalBuyerId("the-original-payer");
        h.ticketRepo.save(seat).block();
        DomainRefusal refused = refusal(asCustomer(gifted.buyerId(), refunds.createUserRefundRequest(input(gifted))));
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
    }

    // ---- the requester withdraws ---------------------------------------------------------------

    @Test
    @DisplayName("cancelRefundRequest: only the requester, only while pending; another buyer is refused; staff can withdraw; nothing moves")
    void withdraw() {
        Sale sale = h.sale(new BigDecimal("60.00"));
        RefundRequest asked = asCustomer(sale.buyerId(), refunds.createUserRefundRequest(input(sale))).block();

        assertThat(refusal(asCustomer("another-buyer", refunds.cancelRefundRequest(asked.getId(), "not mine to withdraw"))).errorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(refusal(asOrganizer("some-organizer", "org-x", refunds.cancelRefundRequest(asked.getId(), "an organizer is not the requester"))).errorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(h.refundRepo.findById(asked.getId()).block().getStatus()).isEqualTo(RefundRequestStatus.PENDING);

        RefundRequest cancelled = asCustomer(sale.buyerId(), refunds.cancelRefundRequest(asked.getId(), "changed my mind")).block();
        assertThat(cancelled.getStatus()).isEqualTo(RefundRequestStatus.CANCELLED);
        assertThat(cancelled.getRejectionReason()).startsWith("Cancelled by the requester");
        assertThat(h.ticketRepo.findById(sale.ticket().getId()).block().getRefundedAmount()).isEqualByComparingTo("0");
        assertThat(h.escrowRepo.findByEventId(sale.eventId()).block().getCurrentBalance()).isEqualByComparingTo("57.00");

        // a withdrawn request leaves the seat free to ask again
        RefundRequest again = asCustomer(sale.buyerId(), refunds.createUserRefundRequest(input(sale))).block();
        assertThat(again.getId()).isNotEqualTo(asked.getId());
        RefundRequest staffCancelled = asAdmin("admin-1", refunds.cancelRefundRequest(again.getId(), "support closed it"), "ROLE_ADMIN").block();
        assertThat(staffCancelled.getRejectionReason()).startsWith("Cancelled by staff");
    }

    @Test
    @DisplayName("once approved a buyer can no longer withdraw: the refund is on its way to the provider")
    void cannotWithdrawAfterApproval() {
        Sale sale = h.sale(new BigDecimal("60.00"));
        RefundRequest asked = asCustomer(sale.buyerId(), refunds.createUserRefundRequest(input(sale))).block();

        asAdmin("admin-1", refunds.approveRefundRequest(asked.getId(), "ok"), "ROLE_ADMIN").block();

        assertThat(refusal(asCustomer(sale.buyerId(), refunds.cancelRefundRequest(asked.getId(), "too late now"))).errorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_PERMITTED);
        await().atMost(Duration.ofSeconds(20)).until(() -> h.refundRepo.findById(asked.getId()).block().getStatus() == RefundRequestStatus.PROCESSING);
        assertThat(h.refundRepo.findById(asked.getId()).block().getStatus()).isNotEqualTo(RefundRequestStatus.CANCELLED);
    }

    // ---- the organizer's team ------------------------------------------------------------------

    @Test
    @DisplayName("refundTicket: a member of the event's organization may refund a part; another organization, a plain holder and an unknown number are all told 'no such ticket'")
    void organizerRefunds() {
        Sale sale = open(h.sale(new BigDecimal("100.00")));
        world.grant("team-member", sale.eventId());
        String number = sale.ticket().getTicketNumber();

        var outsider = refusal(asOrganizer("outsider", "other-org", tickets.refundTicket(number, "not my event", new BigDecimal("10.00"))));
        var holder = refusal(asCustomer(sale.buyerId(), tickets.refundTicket(number, "holders ask, they do not decide", new BigDecimal("10.00"))));
        var invented = refusal(asOrganizer("outsider", "other-org", tickets.refundTicket("TKT-NOPE", "no such ticket", new BigDecimal("10.00"))));
        assertThat(outsider.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(holder.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(invented.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(outsider.details()).isEqualTo(invented.details());
        assertThat(h.refundRepo.findByTicketId(sale.ticket().getId()).collectList().block()).as("nothing was requested").isEmpty();

        Ticket after = asOrganizer("team-member", sale.organizationId(), tickets.refundTicket(number, "goodwill part refund", new BigDecimal("25.00"))).block();
        assertThat(after.getId()).isEqualTo(sale.ticket().getId());
        RefundRequest request = h.refundRepo.findByTicketId(sale.ticket().getId()).blockFirst();
        assertThat(request.getRefundAmount()).isEqualByComparingTo("25.00");
        assertThat(request.getRequestedBy()).isEqualTo("team-member");
        assertThat(request.getBuyerId()).as("the money goes to whoever paid").isEqualTo(sale.buyerId());

        DomainRefusal tooMuch = refusal(asOrganizer("team-member", sale.organizationId(), tickets.refundTicket(number, "more than is left", new BigDecimal("150.00"))));
        assertThat(tooMuch.errorCode()).isIn(ErrorCode.REFUND_NOT_PERMITTED, ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("createAdminRefundRequest: staff raise a refund for any ticket, for a part, with or without bypassing the approval step")
    void staffRaise() {
        Sale sale = h.sale(new BigDecimal("80.00"));

        RefundRequest pending = asAdmin("admin-1", refunds.createAdminRefundRequest(sale.ticket().getId(), "goodwill", false, new BigDecimal("20.00")), "ROLE_ADMIN").block();
        assertThat(pending.getStatus()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(pending.getRefundAmount()).isEqualByComparingTo("20.00");

        DomainRefusal bad = refusal(asAdmin("admin-1", refunds.createAdminRefundRequest(sale.ticket().getId(), "goodwill", true, new BigDecimal("-1")), "ROLE_ADMIN"));
        assertThat(bad.errorCode()).isIn(ErrorCode.COMMAND_NOT_WELL_FORMED, ErrorCode.REFUND_NOT_PERMITTED);
        assertThat(BookingFixture.class).isNotNull();
    }
}
