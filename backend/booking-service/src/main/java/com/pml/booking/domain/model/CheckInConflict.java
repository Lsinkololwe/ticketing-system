package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;

import com.pml.booking.domain.enums.CheckInConflictStatus;
import com.pml.booking.domain.enums.CheckInConflictType;
import com.pml.booking.domain.enums.ValidationMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A scan that was refused, kept so the organizer can see it.
 *
 * <h2>Why a refusal is worth storing</h2>
 * A duplicate scan is the interesting one. Online, the second scan is refused
 * and the person is turned away, so the conflict row is a record of an attempt.
 * Offline it is worse: two devices each admitted the ticket, both were right as
 * far as they could tell, and the duplicate only surfaces when both upload. The
 * losing device's admission gets no check-in row — the unique index forbids it —
 * so this row is the <em>only</em> evidence that a second person walked in.
 *
 * <p>Which means the attendance count and the physical admission count can
 * legitimately disagree, and the size of the disagreement is exactly the number
 * of DUPLICATE_SCAN conflicts. That is a reporting fact worth being explicit
 * about rather than a bug to be reconciled away.
 *
 * <p>Reviewing a conflict annotates it. It never deletes it: the count is the
 * finding.
 */
@Document(collection = BookingCollections.CHECKIN_CONFLICTS)
@TypeAlias("checkin_conflicts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CheckInConflict {

    @Id
    private String id;

    private String eventId;

    /**
     * The event's organizer, so conflict reads can be scoped to the caller.
     *
     * <p>When no ticket matched there is nothing to denormalise from, so this
     * falls back to the organizer who was doing the scanning — who is by
     * definition the one entitled to see it.
     */
    private String organizerId;

    /**
     * Null when the scanned code matched no ticket at all — which is itself the
     * conflict, so the row still has to exist.
     */
    private String ticketId;

    /** What was actually scanned or typed, so an organizer can recognise it. */
    private String presentedCode;

    private CheckInConflictType type;

    private ValidationMethod method;

    private String scannedBy;

    private String deviceId;

    /** Device clock, as reported. See {@link CheckIn#getScannedAt()}. */
    private Instant scannedAt;

    /** Server clock. */
    private Instant detectedAt;

    /**
     * When the winning check-in happened, for a DUPLICATE_SCAN.
     *
     * <p>Present so the organizer can see how far apart the two presentations
     * were: ten seconds is a steward double-tapping, two hours is two people.
     */
    private Instant originalCheckInAt;

    @Builder.Default
    private CheckInConflictStatus status = CheckInConflictStatus.OPEN;

    private String reviewNote;

    private String reviewedBy;

    private Instant reviewedAt;
}
