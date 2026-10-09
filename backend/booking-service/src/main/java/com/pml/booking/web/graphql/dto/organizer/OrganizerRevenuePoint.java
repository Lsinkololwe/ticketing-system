package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point in the organizer's revenue-over-time series.
 *
 * <p>Backs the dashboard's revenue trend tile. The series is intentionally
 * built from <em>complete</em> calendar months only: a partial current month
 * renders as a short column and reads as a collapse in revenue, which is a
 * distortion a revenue trend must never show.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerRevenuePoint {

    /**
     * First day of the month this point covers, as an ISO-8601 date
     * ({@code 2026-03-01}).
     *
     * <p>Sent as a String rather than a DateTime because a month is a date, not
     * an instant — serialising it as midnight-in-some-zone invites the label to
     * shift a month across a timezone boundary. The client formats the axis
     * label; the server never sends a pre-localised month name.
     */
    private String periodStart;

    /** Builds a point for the given month. */
    public static OrganizerRevenuePointBuilder forMonth(LocalDate firstOfMonth) {
        return OrganizerRevenuePoint.builder().periodStart(firstOfMonth.toString());
    }

    /**
     * Gross ticket revenue recognised in this month.
     */
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    /**
     * Tickets sold in this month. Carried so the client can annotate the trend
     * without a second round trip.
     */
    @Builder.Default
    private Integer ticketsSold = 0;

    @Builder.Default
    private String currency = "ZMW";
}
