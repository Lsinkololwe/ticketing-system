package com.pml.booking.web.graphql.dto.checkin;

import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.domain.model.CheckInConflict;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.service.CheckInService;

/**
 * What happened to a scan, as the gate screen needs it.
 *
 * <p>{@code admitted} is derived rather than supplied so the client cannot
 * disagree with the outcome: ADMITTED and ALREADY_RECORDED both mean the person
 * goes in, and every other outcome means they do not.
 *
 * @param outcome  the specific result
 * @param admitted whether this person goes in
 * @param message  a sentence a steward can read straight off the screen
 * @param checkIn  the admission, when there is one
 * @param conflict the recorded refusal, when there is one
 * @param ticket   the matched ticket, when one matched
 */
public record ValidationResult(
        CheckInService.Outcome outcome,
        boolean admitted,
        String message,
        CheckIn checkIn,
        CheckInConflict conflict,
        Ticket ticket
) {
    public static ValidationResult from(CheckInService.ScanResult result) {
        return new ValidationResult(
                result.outcome(),
                result.admitted(),
                result.message(),
                result.checkIn(),
                result.conflict(),
                result.ticket());
    }
}
