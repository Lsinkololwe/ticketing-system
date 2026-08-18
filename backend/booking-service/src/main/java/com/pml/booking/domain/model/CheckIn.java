package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.ValidationMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One accepted admission of one ticket.
 *
 * <h2>Why this collection exists at all</h2>
 * Check-in used to be a {@code validatedAt} timestamp written onto the ticket by
 * a read-modify-write: load the ticket, see it is PURCHASED, set it VALIDATED,
 * save. Two stewards scanning the same ticket a moment apart both read PURCHASED
 * before either wrote, so both wrote, and both were told the ticket was good.
 * The ticket admitted twice and nothing anywhere recorded that it had.
 *
 * <p>The guarantee now lives where it can actually be enforced: a UNIQUE index
 * on {@code ticketId}. The second insert loses to the first at the database, not
 * at a check in application code that a concurrent request can walk straight
 * past. That is the whole point of the collection — a row here <em>is</em> the
 * admission, and there can only ever be one per ticket.
 *
 * <h2>Indexes</h2>
 * <ul>
 *   <li>{@code ticketId} — UNIQUE. One accepted check-in per ticket.</li>
 *   <li>{@code scanId} — UNIQUE, SPARSE. Upload idempotency for the offline
 *       gate: a device that uploads the same batch twice must not admit the
 *       same person twice. Sparse because online scans carry no scan id, and a
 *       non-sparse unique index would treat every one of those nulls as a
 *       duplicate of the others and make the collection unwritable.</li>
 *   <li>{@code (eventId, recordedAt)} — the summary and recent-check-ins reads,
 *       both of which are per-event and time-ordered.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../../specs/ticketing/003-validation-and-checkin/spec.md">ET-TKT-003</a>
 */
@Document(collection = "booking_checkins")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(name = "checkin_event_recorded_idx", def = "{'eventId': 1, 'recordedAt': -1}")
})
public class CheckIn {

    @Id
    private String id;

    /**
     * The admitted ticket.
     *
     * <p>UNIQUE. This single constraint is the "a ticket admits once" guarantee.
     * Everything else in this class is reporting.
     */
    @Indexed(unique = true)
    private String ticketId;

    /** Denormalised so the summary and recent-scans reads never join. */
    @Indexed
    private String eventId;

    /**
     * The event's organizer, denormalised from the ticket.
     *
     * <p>Carried so every read can be scoped to the caller. Without it the gate
     * log is addressable by event id alone, and any organizer who can guess or
     * see one can read another's attendance — which is commercially sensitive
     * and none of their business.
     */
    @Indexed
    private String organizerId;

    /** Human-facing code, carried so the organizer's list needs no lookup. */
    private String ticketNumber;

    /**
     * Client-generated id for one physical scan.
     *
     * <p>UNIQUE and SPARSE. An offline device that loses its connection
     * mid-upload retries the whole batch; without this, the retry admits
     * everyone in it a second time. Sparse because online scans do not carry one.
     */
    @Indexed(unique = true, sparse = true)
    private String scanId;

    private ValidationMethod method;

    /** Who scanned it — taken from the JWT, never from client input. */
    private String scannedBy;

    /** Device that produced the scan, for the offline reconciliation report. */
    private String deviceId;

    /**
     * When the DEVICE says the scan happened.
     *
     * <p>For an offline scan this is the only ordering information available,
     * and it is also the tie-breaker for conflicts — which makes a device with a
     * slow clock win every conflict it is part of. That is a known and
     * exploitable ordering, recorded in the spec's risks; server receipt order
     * was the alternative and is non-deterministic across upload batches.
     */
    private LocalDateTime scannedAt;

    /** When the SERVER accepted it. Never client-supplied. */
    private LocalDateTime recordedAt;

    /** Required for MANUAL admissions; null otherwise. */
    private String reason;
}
