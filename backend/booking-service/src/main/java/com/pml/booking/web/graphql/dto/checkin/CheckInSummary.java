package com.pml.booking.web.graphql.dto.checkin;

import com.pml.booking.service.CheckInService;

import java.time.Instant;

/**
 * Attendance for one event's gate.
 *
 * <p>{@code admitted} counts rows in {@code booking_checkins}, which is not the
 * same as the number of people who physically walked in. A duplicate admitted
 * offline by a second device gets no check-in row — the unique index forbids it
 * — so the physical count is higher by exactly the number of DUPLICATE_SCAN
 * conflicts. Reporting the two figures side by side is the honest presentation;
 * folding them together would hide the discrepancy that matters.
 *
 * @param issued           tickets sold for the event
 * @param admitted         accepted check-ins
 * @param conflicts        refused scans of every kind
 * @param openConflicts    refused scans nobody has looked at yet
 * @param manualAdmissions admitted without a working QR; a high share means the
 *                         scanning is broken or being worked around
 */
public record CheckInSummary(
        String eventId,
        int issued,
        int admitted,
        int conflicts,
        int openConflicts,
        int manualAdmissions,
        Instant lastCheckInAt
) {
    public static CheckInSummary from(CheckInService.Summary summary) {
        return new CheckInSummary(
                summary.eventId(),
                (int) summary.issued(),
                (int) summary.admitted(),
                (int) summary.conflicts(),
                (int) summary.openConflicts(),
                (int) summary.manualAdmissions(),
                summary.lastCheckInAt());
    }
}
