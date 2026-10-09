package com.pml.booking.reads;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.SalesBuckets.Bucket;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.service.BookingReads;
import com.pml.booking.service.BookingStore;
import com.pml.booking.service.RefundReads;
import com.pml.booking.service.SalesAnalytics;
import com.pml.booking.web.graphql.dto.BookingFilterInput;
import com.pml.booking.web.graphql.dto.RefundRequestFilterInput;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.TestClock;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.asCustomer;
import static com.pml.booking.it.BookingFixture.asOrganizer;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whose bookings, refund requests and sales a caller can read: a buyer their own, an organizer their own
 * organization's events, platform staff all. A record outside the caller's scope is answered exactly as
 * one that does not exist.
 */
@Tag("L2")
@Tag("ET-TKT-002")
@DisplayName("ET-TKT-002 · bookings, refund inbox and sales analytics are scoped to the buyer or the organizer's own events")
class ReadsScopingTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static World world;
    private static BookingReads bookings;
    private static RefundReads refundReads;
    private static SalesAnalytics analytics;

    // two organizations, each with an event, an organizer and a buyer
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String ORG_A = "org-a-" + RUN;
    private static final String ORG_B = "org-b-" + RUN;
    private static final String EVENT_A = "ev-a-" + RUN;
    private static final String EVENT_B = "ev-b-" + RUN;
    private static final String ORGANIZER_A = "organizer-a-" + RUN;
    private static final String ORGANIZER_B = "organizer-b-" + RUN;
    private static final String BUYER_A = "buyer-a-" + RUN;
    private static final String BUYER_B = "buyer-b-" + RUN;
    private static Booking bookingA;
    private static Booking bookingB;
    private static RefundRequest refundA;
    private static RefundRequest refundB;

    @BeforeAll
    static void seed() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_reads");
        world = new World();
        world.event(EVENT_A, ORG_A);
        world.event(EVENT_B, ORG_B);
        world.grant(ORGANIZER_A, EVENT_A);
        world.grantOrganization(ORGANIZER_A, ORG_A);
        world.grant(ORGANIZER_B, EVENT_B);
        world.grantOrganization(ORGANIZER_B, ORG_B);
        var clock = TestClock.frozenAt(NOW);
        bookings = new BookingReads(new BookingStore(template, clock), template, world.access);
        refundReads = new RefundReads(template, world.access);
        analytics = new SalesAnalytics(template, world.access, clock);

        bookingA = booking("BK-2026-A" + RUN, EVENT_A, ORG_A, BUYER_A);
        bookingB = booking("BK-2026-B" + RUN, EVENT_B, ORG_B, BUYER_B);
        refundA = refund(EVENT_A, ORG_A, BUYER_A);
        refundB = refund(EVENT_B, ORG_B, BUYER_B);
        sale(EVENT_A, ORG_A, "res-a1", "100.00", NOW.minus(Duration.ofDays(1)), TicketStatus.ISSUED, "0");
        sale(EVENT_A, ORG_A, "res-a1", "100.00", NOW.minus(Duration.ofDays(1)), TicketStatus.ISSUED, "0");
        sale(EVENT_A, ORG_A, "res-a2", "50.00", NOW.minus(Duration.ofDays(2)), TicketStatus.REFUNDED, "50.00");
        sale(EVENT_B, ORG_B, "res-b1", "70.00", NOW.minus(Duration.ofDays(1)), TicketStatus.ISSUED, "0");
    }

    @AfterAll
    static void stop() {
        client.close();
    }

    private static Booking booking(String number, String eventId, String orgId, String buyer) {
        return template.insert(Booking.builder().bookingNumber(number).reservationId("res-" + number).eventId(eventId).organizationId(orgId)
                .buyerId(buyer).contactName("Buyer " + buyer).contactEmail(buyer + "@example.com").status(BookingStatus.CONFIRMED)
                .totalAmount(BigDecimal.TEN).ticketCount(1).createdAt(NOW.minusSeconds(60)).build()).block();
    }

    private static RefundRequest refund(String eventId, String orgId, String buyer) {
        return template.insert(RefundRequest.builder().ticketId("t-" + UUID.randomUUID()).eventId(eventId).organizationId(orgId)
                .organizerId("organizer-" + orgId).buyerId(buyer).refundAmount(BigDecimal.TEN).status(RefundRequestStatus.PENDING)
                .requestedBy(buyer).requestedAt(NOW.minusSeconds(60)).createdAt(NOW.minusSeconds(60)).build()).block();
    }

    private static void sale(String eventId, String orgId, String reservation, String price, Instant purchasedAt, TicketStatus status, String refunded) {
        template.insert(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("T-" + UUID.randomUUID()).eventId(eventId)
                .organizationId(orgId).reservationId(reservation + "-" + RUN).price(new BigDecimal(price)).netAmount(new BigDecimal(price).multiply(new BigDecimal("0.95")))
                .refundedAmount(new BigDecimal(refunded)).status(status).purchaseDate(purchasedAt).buyerId("b").build()).block();
    }

    // ---- bookings ------------------------------------------------------------------------------

    @Test
    @DisplayName("booking(id): the buyer, the event's organizer and staff read it; another buyer and another organization are told it does not exist")
    void bookingById() {
        assertThat(asCustomer(BUYER_A, bookings.byId(bookingA.getId())).block().getId()).isEqualTo(bookingA.getId());
        assertThat(asCustomer(BUYER_A, bookings.byNumber(bookingA.getBookingNumber())).block()).isNotNull();
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, bookings.byId(bookingA.getId())).block()).isNotNull();
        assertThat(asAdmin("admin-1", bookings.byId(bookingA.getId()), "ROLE_ADMIN").block()).isNotNull();

        var otherBuyer = refusal(asCustomer(BUYER_B, bookings.byId(bookingA.getId())));
        var otherOrganizer = refusal(asOrganizer(ORGANIZER_B, ORG_B, bookings.byId(bookingA.getId())));
        var invented = refusal(asCustomer(BUYER_B, bookings.byId("no-such-booking")));
        assertThat(otherBuyer.errorCode()).isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(otherOrganizer.errorCode()).isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(otherBuyer.details()).isEqualTo(invented.details());
        assertThat(refusal(asCustomer(BUYER_B, bookings.byNumber(bookingA.getBookingNumber()))).errorCode()).isEqualTo(ErrorCode.BOOKING_UNKNOWN);
    }

    @Test
    @DisplayName("the order's contact is shown in full to the buyer and staff, never to an organizer")
    void contactVisibility() {
        assertThat(asCustomer(BUYER_A, BookingReads.seesFullContact(bookingA)).block()).isTrue();
        assertThat(asAdmin("admin-1", BookingReads.seesFullContact(bookingA), "ROLE_ADMIN").block()).isTrue();
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, BookingReads.seesFullContact(bookingA)).block()).isFalse();
        assertThat(asCustomer(BUYER_B, BookingReads.seesFullContact(bookingA)).block()).isFalse();
    }

    @Test
    @DisplayName("bookingsByBuyer: a buyer lists only their own; naming someone else is refused like an unknown booking; staff may list anyone's")
    void bookingsByBuyer() {
        assertThat(asCustomer(BUYER_A, bookings.byBuyer(BUYER_A, null, null)).block().data()).extracting(Booking::getId).containsExactly(bookingA.getId());
        assertThat(refusal(asCustomer(BUYER_A, bookings.byBuyer(BUYER_B, null, null))).errorCode()).isEqualTo(ErrorCode.BOOKING_UNKNOWN);
        assertThat(asAdmin("admin-1", bookings.byBuyer(BUYER_B, null, null), "ROLE_ADMIN").block().data()).extracting(Booking::getId).containsExactly(bookingB.getId());
        assertThat(asCustomer(BUYER_B, bookings.mine(null, null)).block().data()).extracting(Booking::getId).containsExactly(bookingB.getId());
        assertThat(asCustomer(BUYER_A, bookings.mine(new BookingFilterInput(null, BookingStatus.FAILED, null, null, null, null), null)).block().data()).isEmpty();
    }

    @Test
    @DisplayName("bookingsByOrganizer: an organizer lists only their organization's; another organization or event is refused; staff list across")
    void bookingsByOrganizer() {
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, bookings.byOrganizer(ORG_A, null, null)).block().data())
                .extracting(Booking::getId).containsExactly(bookingA.getId());
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, bookings.byOrganizer(null, null, null)).block().data())
                .as("no organization named means all of mine, never everyone's").extracting(Booking::getId).containsExactly(bookingA.getId());
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, bookings.byOrganizer(ORG_B, null, null))).errorCode()).isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, bookings.byOrganizer(ORG_A,
                new BookingFilterInput(EVENT_B, null, null, null, null, null), null))).errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, bookings.byOrganizer(ORG_A,
                new BookingFilterInput(EVENT_A, null, null, null, null, null), null)).block().data()).hasSize(1);

        List<String> all = asAdmin("admin-1", bookings.byOrganizer(null, null, null), "ROLE_ADMIN").block().data().stream().map(Booking::getId).toList();
        assertThat(all).contains(bookingA.getId(), bookingB.getId());
        assertThat(asAdmin("admin-1", bookings.byOrganizer(ORG_B, null, null), "ROLE_ADMIN").block().data())
                .extracting(Booking::getId).containsExactly(bookingB.getId());
    }

    @Test
    @DisplayName("a search or date range that makes no sense is refused before it is run")
    void filterValidation() {
        assertThat(refusal(asCustomer(BUYER_A, bookings.mine(new BookingFilterInput(null, null, null, "ab", null, null), null))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asCustomer(BUYER_A, bookings.mine(new BookingFilterInput(null, null, null, null, NOW, NOW.minusSeconds(5)), null))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        // a search is literal text: regex metacharacters match nothing rather than everything
        assertThat(asCustomer(BUYER_A, bookings.mine(new BookingFilterInput(null, null, null, ".*.*", null, null), null)).block().data()).isEmpty();
    }

    // ---- refund inbox --------------------------------------------------------------------------

    @Test
    @DisplayName("refundRequestsByOrganizer: only the caller's own organization's requests; another organization is refused; staff see all")
    void refundInbox() {
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, refundReads.byOrganizer(ORG_A, null, null)).block().data())
                .extracting(RefundRequest::getId).containsExactly(refundA.getId());
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, refundReads.byOrganizer(null, null, null)).block().data())
                .extracting(RefundRequest::getId).containsExactly(refundA.getId());
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, refundReads.byOrganizer(ORG_B, null, null))).errorCode()).isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN);
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, refundReads.byOrganizer(ORG_A,
                new RefundRequestFilterInput(null, BUYER_B, null, null, null, null, null, null), null)).block().data())
                .as("a filter narrows within the organization and cannot widen it").isEmpty();
        assertThat(asAdmin("admin-1", refundReads.byOrganizer(null, null, null), "ROLE_ADMIN").block().data())
                .extracting(RefundRequest::getId).contains(refundA.getId(), refundB.getId());
        assertThat(asOrganizer(ORGANIZER_A, ORG_A, refundReads.byOrganizer(ORG_A,
                new RefundRequestFilterInput(null, null, null, "someone-else", null, null, null, null), null)).block().data())
                .as("the organizer filter is applied").isEmpty();
    }

    // ---- sales analytics -----------------------------------------------------------------------

    @Test
    @DisplayName("salesOverTime: gross, net, refunds and orders per Lusaka day for the organizer's own event; quiet days are present as zero")
    void salesOverTime() {
        List<SalesAnalytics.SalesPoint> series = asOrganizer(ORGANIZER_A, ORG_A,
                analytics.salesOverTime(EVENT_A, NOW.minus(Duration.ofDays(3)), NOW, Bucket.DAY)).block();

        assertThat(series.stream().mapToInt(SalesAnalytics.SalesPoint::tickets).sum()).isEqualTo(3);
        assertThat(series.stream().map(SalesAnalytics.SalesPoint::grossRevenue).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("250.00");
        assertThat(series.stream().map(SalesAnalytics.SalesPoint::refundedAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("50.00");
        assertThat(series.stream().mapToInt(SalesAnalytics.SalesPoint::orders).sum()).as("two reservations").isEqualTo(2);
        assertThat(series.stream().filter(point -> point.tickets() == 0)).as("days with no sale are still points").isNotEmpty();
    }

    @Test
    @DisplayName("salesOverTime: another organization's event is 'unknown event'; staff may read any; a range past the bucket's limit is refused")
    void salesOverTimeScope() {
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.salesOverTime(EVENT_B, NOW.minus(Duration.ofDays(3)), NOW, Bucket.DAY))).errorCode())
                .isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.salesOverTime("ev-never", null, null, null))).errorCode())
                .isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(asAdmin("admin-1", analytics.salesOverTime(EVENT_B, NOW.minus(Duration.ofDays(3)), NOW, Bucket.DAY), "ROLE_ADMIN").block()
                .stream().mapToInt(SalesAnalytics.SalesPoint::tickets).sum()).isEqualTo(1);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.salesOverTime(EVENT_A, NOW.minus(Duration.ofDays(30)), NOW, Bucket.HOUR))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.salesOverTime(EVENT_A, NOW, NOW.minusSeconds(1), Bucket.DAY))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("purchasesByDayAndHour: a full week grid; scoped to the named event or organization; the whole platform only for staff")
    void heatmap() {
        var byEvent = asOrganizer(ORGANIZER_A, ORG_A, analytics.purchasesByDayAndHour(NOW.minus(Duration.ofDays(5)), NOW, EVENT_A, null)).block();
        assertThat(byEvent).hasSize(168);
        assertThat(byEvent.stream().mapToInt(SalesAnalytics.HeatCell::tickets).sum()).isEqualTo(3);

        var byOrganization = asOrganizer(ORGANIZER_A, ORG_A, analytics.purchasesByDayAndHour(NOW.minus(Duration.ofDays(5)), NOW, null, ORG_A)).block();
        assertThat(byOrganization.stream().mapToInt(SalesAnalytics.HeatCell::tickets).sum()).isEqualTo(3);

        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.purchasesByDayAndHour(null, null, EVENT_B, null))).errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.purchasesByDayAndHour(null, null, null, ORG_B))).errorCode()).isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN);
        assertThat(refusal(asOrganizer(ORGANIZER_A, ORG_A, analytics.purchasesByDayAndHour(null, null, null, null))).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);

        var everything = asAdmin("admin-1", analytics.purchasesByDayAndHour(NOW.minus(Duration.ofDays(5)), NOW, null, null), "ROLE_ADMIN").block();
        assertThat(everything.stream().mapToInt(SalesAnalytics.HeatCell::tickets).sum()).isGreaterThanOrEqualTo(4);
    }
}
