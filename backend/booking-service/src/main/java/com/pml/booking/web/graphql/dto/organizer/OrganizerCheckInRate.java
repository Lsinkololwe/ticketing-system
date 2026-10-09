package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Gate attendance for an organizer's most recent event that has already run.
 *
 * <p>Both the numerator and the denominator are sent. The client never receives
 * only a percentage: a rate without its denominator cannot be checked, and the
 * dashboard prints "1,044 of 1,200" beside the figure for exactly that reason.
 *
 * <p>Scans are deduplicated by ticket id, so re-scanning a ticket at the gate
 * cannot push the rate above 100%.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerCheckInRate {

    private String eventId;

    private String eventTitle;

    private Instant eventDateTime;

    /**
     * Tickets issued for the event and still valid at gate time — the
     * denominator. Cancelled and refunded tickets are excluded, since nobody
     * expected their holders to arrive.
     */
    @Builder.Default
    private Integer issued = 0;

    /**
     * Distinct tickets scanned at the gate — the numerator.
     */
    @Builder.Default
    private Integer scanned = 0;

    /**
     * Convenience percentage, rounded to one decimal. Derived from the two
     * counts above; it is never the only figure sent.
     */
    public Float getRatePercent() {
        if (issued == null || issued == 0) {
            return 0.0f;
        }
        return Math.round((scanned * 1000.0f) / issued) / 10.0f;
    }
}
