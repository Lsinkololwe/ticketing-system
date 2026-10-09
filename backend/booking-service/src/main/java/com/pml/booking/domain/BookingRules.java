package com.pml.booking.domain;

import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import org.springframework.data.mongodb.core.query.Criteria;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Pure rules for bookings: how a number reads and what status a viewer is shown.
 *
 * <p>The status is derived from stored facts and the same derivation exists twice — once over a
 * loaded {@link Booking} ({@link #effectiveStatus}) and once as a database predicate
 * ({@link #criteriaFor}) — so a list filtered by {@code REFUNDED} contains exactly the bookings
 * that print as {@code REFUNDED}. {@code BookingRulesTest} holds the two together.
 */
public final class BookingRules {

    private BookingRules() {
    }

    /** The counter a booking number is drawn from: one sequence per calendar year (UTC). */
    public static String sequenceFor(Instant at) {
        return "booking-" + at.atZone(ZoneOffset.UTC).getYear();
    }

    /** {@code BK-2026-00001234}: the year, then the sequence zero-padded to eight digits. */
    public static String format(Instant at, long sequence) {
        return "BK-%d-%08d".formatted(at.atZone(ZoneOffset.UTC).getYear(), sequence);
    }

    /** States of the automatic refund of a payment that arrived after the hold lapsed. */
    public static final List<String> LATE_REFUND_ACTIVE = List.of("REQUESTED", "PROCESSING", "COMPLETED");

    /** What the booking is, for whoever is looking at it. */
    public static BookingStatus effectiveStatus(Booking booking) {
        BookingStatus stored = booking.getStatus() == null ? BookingStatus.PENDING : booking.getStatus();
        switch (stored) {
            case EXPIRED -> {
                return booking.getLateRefundStatus() != null && LATE_REFUND_ACTIVE.contains(booking.getLateRefundStatus())
                        ? BookingStatus.PAID_AFTER_EXPIRY_AUTO_REFUNDED
                        : BookingStatus.EXPIRED;
            }
            case CONFIRMED -> {
                BigDecimal refunded = booking.getRefundedAmount() == null ? BigDecimal.ZERO : booking.getRefundedAmount();
                BigDecimal total = booking.getTotalAmount() == null ? BigDecimal.ZERO : booking.getTotalAmount();
                if (refunded.signum() > 0) {
                    return refunded.compareTo(total) >= 0 ? BookingStatus.REFUNDED : BookingStatus.PARTIALLY_REFUNDED;
                }
                if (booking.getTicketCount() > 0 && booking.getCancelledTicketCount() >= booking.getTicketCount()) {
                    return BookingStatus.CANCELLED;
                }
                return BookingStatus.CONFIRMED;
            }
            default -> {
                return stored;
            }
        }
    }

    /** The database predicate that selects exactly the bookings whose {@link #effectiveStatus} is {@code wanted}. */
    public static Criteria criteriaFor(BookingStatus wanted) {
        Criteria confirmed = Criteria.where("status").is(BookingStatus.CONFIRMED);
        Criteria noRefund = new Criteria().orOperator(
                Criteria.where("refundedAmount").exists(false),
                Criteria.where("refundedAmount").lte(BigDecimal.ZERO));
        return switch (wanted) {
            case PENDING, FAILED -> Criteria.where("status").is(wanted);
            case CANCELLED -> new Criteria().orOperator(
                    Criteria.where("status").is(BookingStatus.CANCELLED),
                    new Criteria().andOperator(confirmed, noRefund,
                            Criteria.where("ticketCount").gt(0),
                            new Criteria().expr(org.springframework.data.mongodb.core.aggregation.ComparisonOperators
                                    .valueOf("cancelledTicketCount")
                                    .greaterThanEqualTo("ticketCount"))));
            case EXPIRED -> new Criteria().andOperator(Criteria.where("status").is(BookingStatus.EXPIRED),
                    new Criteria().orOperator(
                            Criteria.where("lateRefundStatus").exists(false),
                            Criteria.where("lateRefundStatus").nin(LATE_REFUND_ACTIVE)));
            case PAID_AFTER_EXPIRY_AUTO_REFUNDED -> new Criteria().andOperator(
                    Criteria.where("status").is(BookingStatus.EXPIRED),
                    Criteria.where("lateRefundStatus").in(LATE_REFUND_ACTIVE));
            case REFUNDED -> new Criteria().andOperator(confirmed,
                    Criteria.where("refundedAmount").gt(BigDecimal.ZERO),
                    new Criteria().expr(org.springframework.data.mongodb.core.aggregation.ComparisonOperators
                            .valueOf("refundedAmount").greaterThanEqualTo("totalAmount")));
            case PARTIALLY_REFUNDED -> new Criteria().andOperator(confirmed,
                    Criteria.where("refundedAmount").gt(BigDecimal.ZERO),
                    new Criteria().expr(org.springframework.data.mongodb.core.aggregation.ComparisonOperators
                            .valueOf("refundedAmount").lessThan("totalAmount")));
            case CONFIRMED -> new Criteria().andOperator(confirmed, noRefund,
                    new Criteria().orOperator(
                            Criteria.where("ticketCount").lte(0),
                            new Criteria().expr(org.springframework.data.mongodb.core.aggregation.ComparisonOperators
                                    .valueOf("cancelledTicketCount").lessThan("ticketCount"))));
        };
    }
}
