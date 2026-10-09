package com.pml.booking.service;

import com.pml.booking.domain.SalesBuckets.Bucket;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.dto.NotificationReceipt;
import com.pml.booking.web.graphql.dto.BookingFilterInput;
import com.pml.booking.web.graphql.dto.PaymentAttemptFilterInput;
import com.pml.booking.web.graphql.dto.RefundRequestFilterInput;
import com.pml.shared.error.FieldViolation;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure parts of the booking read and operator services: what a filter means, what is safe to retry, how a series and a grid are shaped. */
@Tag("L1")
@Tag("ET-ADM-003")
@DisplayName("Booking read and operator rules · filters, retry safety, series and grid shape")
class OperationsReadRulesTest {

    private static final Instant T = Instant.parse("2026-10-05T10:00:00Z");

    private static PaymentAttempt attempt(PaymentAttemptType type, PaymentAttemptStatus status, String failureCode, boolean fulfilled) {
        PaymentAttempt attempt = PaymentAttempt.builder().attemptType(type).status(status).failureCode(failureCode).build();
        attempt.setFulfilled(fulfilled);
        return attempt;
    }

    @Test
    @DisplayName("only a collection whose answer can still change is retried in bulk: in flight, or failed because the provider was unreachable")
    void safeToRetry() {
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.PROCESSING, null, false))).isTrue();
        assertThat(PaymentOperations.safeToRetry(attempt(null, PaymentAttemptStatus.CREATED, null, false))).as("a row from before types existed is a collection").isTrue();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.CONFIRMED, null, false))).isTrue();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.CONFIRMED, null, true))).as("already fulfilled").isFalse();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.COMPLETED, null, true))).isFalse();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, "CIRCUIT_BREAKER_OPEN", false))).isTrue();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.REJECTED, "NETWORK_ERROR", false))).isTrue();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, "INSUFFICIENT_FUNDS", false)))
                .as("a decline is the provider's answer, and retrying it is arguing with it").isFalse();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.COLLECT, PaymentAttemptStatus.FAILED, null, false))).isFalse();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.REFUND, PaymentAttemptStatus.PROCESSING, null, false))).isFalse();
        assertThat(PaymentOperations.safeToRetry(attempt(PaymentAttemptType.PAYOUT, PaymentAttemptStatus.PROCESSING, null, false))).isFalse();
    }

    @Test
    @DisplayName("a payment-attempt filter is checked for sense before it is run")
    void paymentFilterChecks() {
        assertThat(PaymentOperations.check(null)).isEmpty();
        assertThat(PaymentOperations.check(new PaymentAttemptFilterInput(null, null, null, null, null, null, null, "LOW", T, T.plusSeconds(1),
                BigDecimal.ONE, BigDecimal.TEN, null, 5))).isEmpty();
        List<FieldViolation> bad = PaymentOperations.check(new PaymentAttemptFilterInput(null, null, null, null, null, null, null, "SEVERE",
                T.plusSeconds(1), T, BigDecimal.TEN, BigDecimal.ONE, null, 0));
        assertThat(bad).extracting(FieldViolation::path).containsExactlyInAnyOrder("filter.riskLevel", "filter.createdBefore",
                "filter.maxAmount", "filter.stuckForMinutes");
    }

    @Test
    @DisplayName("a booking filter: three characters to search, dates in order; a search is literal text; several statuses are alternatives")
    void bookingFilter() {
        assertThat(BookingReads.check(null)).isEmpty();
        assertThat(BookingReads.check(new BookingFilterInput(null, null, null, "ab", null, null))).extracting(FieldViolation::path).containsExactly("filter.search");
        assertThat(BookingReads.check(new BookingFilterInput(null, null, null, null, T, T.minusSeconds(1)))).extracting(FieldViolation::path)
                .containsExactly("filter.createdBefore");
        assertThat(BookingReads.conditions(null)).isEmpty();
        assertThat(BookingReads.conditions(new BookingFilterInput("ev-1", BookingStatus.CONFIRMED, List.of(BookingStatus.REFUNDED, BookingStatus.CONFIRMED), "BK-2026", T, T.plusSeconds(60))))
                .hasSize(4);
        assertThat(BookingReads.conditions(new BookingFilterInput(null, null, null, ".*", null, null)).get(0).getCriteriaObject().toString())
                .as("regular-expression characters are quoted, not interpreted").contains("\\Q.*\\E");
    }

    @Test
    @DisplayName("a refund-inbox filter narrows by every field it names, including the organizer")
    void refundFilter() {
        assertThat(RefundReads.conditions(null)).isEmpty();
        assertThat(RefundReads.conditions(new RefundRequestFilterInput("t-1", "b-1", "ev-1", "o-1", null, null, T, T.plusSeconds(1))))
                .extracting(c -> c.getCriteriaObject().keySet().iterator().next()).containsExactly("ticketId", "buyerId", "eventId", "organizerId", "requestedAt");
    }

    @Test
    @DisplayName("a sales range may not run backwards or past what the bucket can chart")
    void salesRange() {
        assertThat(SalesAnalytics.checkRange(T.minus(Duration.ofDays(30)), T, Bucket.DAY)).isEmpty();
        assertThat(SalesAnalytics.checkRange(T, T.minusSeconds(1), Bucket.DAY)).extracting(FieldViolation::path).containsExactly("to");
        assertThat(SalesAnalytics.checkRange(T.minus(Duration.ofDays(8)), T, Bucket.HOUR)).hasSize(1);
        assertThat(SalesAnalytics.checkRange(T.minus(Duration.ofDays(700)), T, Bucket.WEEK)).isEmpty();
        assertThat(SalesAnalytics.checkRange(T.minus(Duration.ofDays(800)), T, Bucket.WEEK)).hasSize(1);
    }

    @Test
    @DisplayName("the heatmap is always seven days by twenty-four hours, with the quiet cells at zero and money read from decimals")
    void heatGrid() {
        Document busy = new Document("_id", new Document("day", 3).append("hour", 14))
                .append("orders", List.of("r1", "r2")).append("tickets", 5).append("revenue", new Decimal128(new BigDecimal("250.50")));

        List<SalesAnalytics.HeatCell> grid = SalesAnalytics.grid(List.of(busy));

        assertThat(grid).hasSize(168);
        assertThat(grid.get(0)).isEqualTo(new SalesAnalytics.HeatCell(1, 0, 0, 0, BigDecimal.ZERO));
        assertThat(grid.get(2 * 24 + 14)).isEqualTo(new SalesAnalytics.HeatCell(3, 14, 2, 5, new BigDecimal("250.50")));
        assertThat(grid.stream().filter(c -> c.tickets() > 0)).hasSize(1);
        assertThat(SalesAnalytics.grid(List.of())).hasSize(168);
    }

    @Test
    @DisplayName("a series has a point for every bucket in the range, quiet ones included, in order")
    void series() {
        Instant from = Instant.parse("2026-10-03T00:00:00Z");
        Instant to = Instant.parse("2026-10-05T00:00:00Z");
        List<SalesAnalytics.SalesPoint> empty = SalesAnalytics.series(List.of(), from, to, Bucket.DAY);

        assertThat(empty).hasSizeGreaterThanOrEqualTo(2);
        assertThat(empty).allSatisfy(point -> assertThat(point.tickets()).isZero());
        assertThat(empty).extracting(SalesAnalytics.SalesPoint::bucketStart).isSorted();

        Document row = new Document("_id", Date.from(empty.get(1).bucketStart())).append("tickets", 4).append("orders", List.of("a", "b"))
                .append("gross", new Decimal128(new BigDecimal("400"))).append("net", new Decimal128(new BigDecimal("380")))
                .append("refunded", new Decimal128(new BigDecimal("50")));
        SalesAnalytics.SalesPoint filled = SalesAnalytics.series(List.of(row), from, to, Bucket.DAY).get(1);
        assertThat(filled.tickets()).isEqualTo(4);
        assertThat(filled.orders()).isEqualTo(2);
        assertThat(filled.grossRevenue()).isEqualByComparingTo("400");
        assertThat(filled.refundedAmount()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("a resend result carries the ticket's number and the masked destination, never a QR or a raw contact")
    void resendResult() {
        Ticket ticket = Ticket.builder().id("t-1").ticketNumber("TKT-1").qrCode("SECRET").buyerEmail("a@example.com").build();
        var result = TicketResendService.result(ticket, NotificationReceipt.queued("WHATSAPP", "+260 97* *** *23", 1));
        assertThat(result.toString()).doesNotContain("SECRET").doesNotContain("a@example.com");
        assertThat(result.ticketNumber()).isEqualTo("TKT-1");
        assertThat(result.destination()).isEqualTo("+260 97* *** *23");
        assertThat(TicketResendService.PER_TICKET).isEqualTo(3);
        assertThat(TicketResendService.PER_CALLER).isEqualTo(30);
    }
}
