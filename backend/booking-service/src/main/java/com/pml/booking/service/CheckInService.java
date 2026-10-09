package com.pml.booking.service;

import com.pml.booking.domain.enums.ValidationMethod;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.domain.model.CheckInConflict;
import com.pml.booking.domain.model.Ticket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Gate admission: turning a scan into an admission, exactly once.
 *
 * @see com.pml.booking.domain.model.CheckIn
 */
public interface CheckInService {

    /**
     * What happened to a scan.
     *
     * <p>Deliberately not a boolean. A steward at a gate needs to distinguish
     * "this ticket is fake" from "this ticket was already used twenty minutes
     * ago" — the first is a refusal, the second is a conversation — and a
     * caller that only sees false will show the same message for both.
     */
    enum Outcome {
        /** Admitted. A check-in row was created by this call. */
        ADMITTED,
        /**
         * This exact scan had already been recorded — same {@code scanId}.
         * An upload retry, not a second person. Not a conflict.
         */
        ALREADY_RECORDED,
        /** The ticket was already admitted. Refused, and a conflict is recorded. */
        ALREADY_ADMITTED,
        /** The ticket exists but belongs to another event. */
        WRONG_EVENT,
        /** Refunded, cancelled, or otherwise not admissible. */
        INVALID_STATE,
        /** Nothing matched the presented code. */
        NOT_FOUND
    }

    /**
     * One scan presented at a gate.
     *
     * @param eventId    the event whose gate this is — a ticket for another
     *                   event is refused even if it is otherwise perfectly valid
     * @param code       the ticket number scanned or typed
     * @param method     how it was presented
     * @param scanId     client-generated id, for offline upload idempotency;
     *                   null for an online scan
     * @param deviceId   the scanning device, for the reconciliation report
     * @param scannedAt  device clock; null means "now, on the server"
     * @param reason     required for {@link ValidationMethod#MANUAL}
     * @param scannedBy   actor id — taken from the JWT by the caller, never from
     *                    client input
     * @param organizerId the caller's organization, also from the JWT. Used to
     *                    scope a conflict when no ticket matched and there is
     *                    nothing to denormalise from
     */
    record ScanCommand(
            String eventId,
            String code,
            ValidationMethod method,
            String scanId,
            String deviceId,
            Instant scannedAt,
            String reason,
            String scannedBy,
            String organizerId
    ) {}

    /**
     * The result of a scan.
     *
     * @param outcome  what happened
     * @param checkIn  the admission, when there is one
     * @param conflict the recorded refusal, when there is one
     * @param ticket   the matched ticket, when one matched
     * @param message  a sentence a steward can read off the screen
     */
    record ScanResult(
            Outcome outcome,
            CheckIn checkIn,
            CheckInConflict conflict,
            Ticket ticket,
            String message
    ) {
        public boolean admitted() {
            return outcome == Outcome.ADMITTED || outcome == Outcome.ALREADY_RECORDED;
        }
    }

    /** Attendance figures for an event's gate. */
    record Summary(
            String eventId,
            long issued,
            long admitted,
            long conflicts,
            long openConflicts,
            long manualAdmissions,
            Instant lastCheckInAt
    ) {}

    /**
     * Present one ticket at the gate.
     *
     * <p>Never signals an error for a refused scan. A refusal is a normal
     * outcome at a gate and the steward needs to be told which one it was; an
     * error channel collapses every refusal into a stack trace.
     */
    Mono<ScanResult> scan(ScanCommand command);

    /**
     * Reconcile a batch of offline scans.
     *
     * <p>Processed sequentially so that two scans of the same ticket within one
     * batch resolve deterministically against each other rather than racing.
     */
    Mono<List<ScanResult>> uploadScans(List<ScanCommand> commands);

    /**
     * Attendance for one event's gate.
     *
     * <p>Scoped by organizer. An organizer asking about someone else's event
     * gets zeroes rather than a refusal — the distinction between "no
     * attendance" and "not yours" is itself information about another
     * organizer's event.
     */
    Mono<Summary> summary(String eventId, String organizerId, long issuedTickets);

    /** Bounded by the caller, and capped at 100. */
    Flux<CheckIn> recentCheckIns(String eventId, String organizerId, int limit);

    Flux<CheckInConflict> conflicts(String eventId, String organizerId, int page, int size);

    Mono<Long> countConflicts(String eventId, String organizerId);

    /** One scan conflict by id, or empty when there is none. */
    Mono<CheckInConflict> findConflict(String conflictId);

    /**
     * Annotates a conflict. Never deletes it — the count is the finding.
     *
     * <p>Empty when the conflict does not belong to this organizer.
     */
    Mono<CheckInConflict> reviewConflict(String conflictId, String organizerId, String note, String reviewedBy);
}
