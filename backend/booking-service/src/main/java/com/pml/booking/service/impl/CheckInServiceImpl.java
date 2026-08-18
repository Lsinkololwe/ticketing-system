package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.CheckInConflictStatus;
import com.pml.booking.domain.enums.CheckInConflictType;
import com.pml.booking.domain.enums.ValidationMethod;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.domain.model.CheckInConflict;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.CheckInConflictRepository;
import com.pml.booking.repository.CheckInRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.CheckInService;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gate admission.
 *
 * <h2>Where the guarantee lives</h2>
 * "A ticket admits once" is enforced by the UNIQUE index on
 * {@code booking_checkins.ticketId}, not by the status check below. The status
 * check exists to give a steward a useful sentence in the ordinary sequential
 * case; the index is what holds when two stewards scan the same ticket at the
 * same moment and neither read sees the other's write. Removing the status
 * check would cost a good error message. Removing the index would cost the
 * guarantee.
 *
 * <h2>Refusals are outcomes, not errors</h2>
 * Nothing here signals {@code Mono.error} for a refused scan. At a gate,
 * "already admitted" is an ordinary thing that happens several times a night and
 * the steward needs to know which refusal it was. Routing it through the error
 * channel collapses every refusal into the same red box.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/003-validation-and-checkin/spec.md">ET-TKT-003</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    /**
     * The statuses a ticket may be admitted from — ET-TKT-002 R7 leaves exactly one.
     *
     * <p>VALIDATED is deliberately absent, and that is the whole re-entry
     * defence: a ticket that has been through a gate is no longer admissible,
     * so presenting it again reads as a duplicate rather than as a fresh scan.
     */
    private static final Set<TicketStatus> ADMISSIBLE =
            EnumSet.of(TicketStatus.ISSUED);

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInConflictRepository conflictRepository;

    // =========================================================================
    // SCAN
    // =========================================================================

    @Override
    public Mono<ScanResult> scan(ScanCommand command) {
        if (command.scanId() != null && !command.scanId().isBlank()) {
            // Upload idempotency. A device that loses its connection mid-upload
            // retries the whole batch; without this the retry admits everyone in
            // it again, and the unique ticketId index would report those as
            // duplicate PEOPLE rather than a duplicate transmission.
            return checkInRepository.findByScanId(command.scanId())
                    .map(existing -> new ScanResult(
                            Outcome.ALREADY_RECORDED, existing, null, null,
                            "This scan was already uploaded."))
                    .switchIfEmpty(Mono.defer(() -> resolveAndAdmit(command)));
        }
        return resolveAndAdmit(command);
    }

    private Mono<ScanResult> resolveAndAdmit(ScanCommand command) {
        return ticketRepository.findByTicketNumber(command.code())
                .flatMap(ticket -> admit(command, ticket))
                .switchIfEmpty(Mono.defer(() -> recordConflict(
                        command, null, CheckInConflictType.TICKET_NOT_FOUND, null)
                        .map(conflict -> new ScanResult(
                                Outcome.NOT_FOUND, null, conflict, null,
                                "No ticket matches this code."))));
    }

    private Mono<ScanResult> admit(ScanCommand command, Ticket ticket) {
        if (!ticket.getEventId().equals(command.eventId())) {
            return recordConflict(command, ticket, CheckInConflictType.TICKET_NOT_VALID_FOR_EVENT, null)
                    .map(conflict -> new ScanResult(
                            Outcome.WRONG_EVENT, null, conflict, ticket,
                            "This ticket is for a different event."));
        }

        if (!ADMISSIBLE.contains(ticket.getStatus())) {
            // A ticket already marked VALIDATED is the common case here, and it
            // reads as a duplicate rather than a bad ticket — the person in
            // front of the steward has a real ticket that has already been used.
            boolean alreadyAdmitted = ticket.getStatus().isAdmitted();

            CheckInConflictType type = alreadyAdmitted
                    ? CheckInConflictType.DUPLICATE_SCAN
                    : CheckInConflictType.INVALID_STATE;

            return checkInRepository.findByTicketId(ticket.getId())
                    .map(CheckIn::getRecordedAt)
                    .defaultIfEmpty(ticket.getValidatedAt() == null ? LocalDateTime.MIN : ticket.getValidatedAt())
                    .flatMap(originalAt -> recordConflict(command, ticket, type,
                            LocalDateTime.MIN.equals(originalAt) ? null : originalAt))
                    .map(conflict -> new ScanResult(
                            alreadyAdmitted ? Outcome.ALREADY_ADMITTED : Outcome.INVALID_STATE,
                            null, conflict, ticket,
                            alreadyAdmitted
                                    ? "This ticket has already been admitted."
                                    : "This ticket is not admissible (" + ticket.getStatus() + ")."));
        }

        LocalDateTime now = LocalDateTime.now();
        CheckIn checkIn = CheckIn.builder()
                .ticketId(ticket.getId())
                .eventId(ticket.getEventId())
                .organizerId(ticket.getOrganizerId())
                .ticketNumber(ticket.getTicketNumber())
                .scanId(blankToNull(command.scanId()))
                .method(command.method() == null ? ValidationMethod.QR_ONLINE : command.method())
                .scannedBy(command.scannedBy())
                .deviceId(command.deviceId())
                .scannedAt(command.scannedAt() == null ? now : command.scannedAt())
                .recordedAt(now)
                .reason(blankToNull(command.reason()))
                .build();

        return checkInRepository.insert(checkIn)
                // insert, not save: save would issue an update for a document
                // that already carries an id, and the unique index would never
                // be consulted. The insert is the whole guarantee.
                .flatMap(saved -> markTicketValidated(ticket, saved.getRecordedAt())
                        .thenReturn(new ScanResult(
                                Outcome.ADMITTED, saved, null, ticket, "Admitted.")))
                .onErrorResume(DuplicateKeyException.class, error ->
                        // Lost the race. Another scan of this ticket committed
                        // between our status read and our insert — the case the
                        // status check above cannot see and the index can.
                        checkInRepository.findByTicketId(ticket.getId())
                                .flatMap(winner -> recordConflict(command, ticket,
                                        CheckInConflictType.DUPLICATE_SCAN, winner.getRecordedAt()))
                                .map(conflict -> new ScanResult(
                                        Outcome.ALREADY_ADMITTED, null, conflict, ticket,
                                        "This ticket has already been admitted.")));
    }

    /**
     * Mirrors the admission onto the ticket, conditionally.
     *
     * <p>The condition matters even though the check-in row is already the
     * authority: an unconditional update would let a late-arriving duplicate
     * overwrite {@code validatedAt} with its own, later timestamp and quietly
     * move the recorded admission time.
     */
    private Mono<Ticket> markTicketValidated(Ticket ticket, LocalDateTime at) {
        Query query = Query.query(Criteria.where("_id").is(ticket.getId())
                .and("status").is(TicketStatus.ISSUED.name()));

        Update update = new Update()
                .set("status", TicketStatus.VALIDATED.name())
                .set("validatedAt", at)
                .set("updatedAt", at)
                .inc("version", 1);

        return mongoTemplate.findAndModify(query, update, Ticket.class)
                .defaultIfEmpty(ticket);
    }

    private Mono<CheckInConflict> recordConflict(ScanCommand command,
                                                 Ticket ticket,
                                                 CheckInConflictType type,
                                                 LocalDateTime originalAt) {
        LocalDateTime now = LocalDateTime.now();
        CheckInConflict conflict = CheckInConflict.builder()
                .eventId(command.eventId())
                // From the ticket when there is one; otherwise the organizer
                // who was scanning, who is the only party entitled to see a
                // scan that matched nothing.
                .organizerId(ticket == null ? command.organizerId() : ticket.getOrganizerId())
                .ticketId(ticket == null ? null : ticket.getId())
                .presentedCode(command.code())
                .type(type)
                .method(command.method() == null ? ValidationMethod.QR_ONLINE : command.method())
                .scannedBy(command.scannedBy())
                .deviceId(command.deviceId())
                .scannedAt(command.scannedAt() == null ? now : command.scannedAt())
                .detectedAt(now)
                .originalCheckInAt(originalAt)
                .status(CheckInConflictStatus.OPEN)
                .build();

        log.warn("Check-in conflict on event {}: {} for code {}",
                command.eventId(), type, command.code());

        return conflictRepository.save(conflict);
    }

    // =========================================================================
    // OFFLINE UPLOAD
    // =========================================================================

    @Override
    public Mono<List<ScanResult>> uploadScans(List<ScanCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return Mono.just(List.of());
        }

        // Earliest scannedAt wins, ties broken by scanId lexicographically, so
        // that the same batch uploaded twice — or split across two uploads —
        // resolves to the same winner every time.
        List<ScanCommand> ordered = new ArrayList<>(commands);
        ordered.sort((a, b) -> {
            LocalDateTime left = a.scannedAt() == null ? LocalDateTime.MAX : a.scannedAt();
            LocalDateTime right = b.scannedAt() == null ? LocalDateTime.MAX : b.scannedAt();
            int byTime = left.compareTo(right);
            if (byTime != 0) {
                return byTime;
            }
            return String.valueOf(a.scanId()).compareTo(String.valueOf(b.scanId()));
        });

        // concatMap, not flatMap: two scans of one ticket inside a single batch
        // must resolve against each other in the order just established, and
        // concurrent processing would hand the win to whichever finished first.
        return Flux.fromIterable(ordered)
                .concatMap(this::scan)
                .collectList();
    }

    // =========================================================================
    // READS
    // =========================================================================

    @Override
    public Mono<Summary> summary(String eventId, String organizerId, long issuedTickets) {
        return Mono.zip(
                checkInRepository.countByEventIdAndOrganizerId(eventId, organizerId),
                conflictRepository.countByEventIdAndOrganizerId(eventId, organizerId),
                conflictRepository.countByEventIdAndOrganizerIdAndStatus(
                        eventId, organizerId, CheckInConflictStatus.OPEN),
                checkInRepository.countByEventIdAndOrganizerIdAndMethod(
                        eventId, organizerId, ValidationMethod.MANUAL),
                checkInRepository.findByEventIdAndOrganizerIdOrderByRecordedAtDesc(
                                eventId, organizerId, PageRequest.of(0, 1))
                        .next()
                        .map(CheckIn::getRecordedAt)
                        .map(List::of)
                        .defaultIfEmpty(List.of())
        ).map(t -> new Summary(
                eventId,
                issuedTickets,
                t.getT1(),
                t.getT2(),
                t.getT3(),
                t.getT4(),
                t.getT5().isEmpty() ? null : t.getT5().get(0)
        ));
    }

    @Override
    public Flux<CheckIn> recentCheckIns(String eventId, String organizerId, int limit) {
        int bounded = Math.min(Math.max(limit, 1), 100);
        return checkInRepository.findByEventIdAndOrganizerIdOrderByRecordedAtDesc(
                eventId, organizerId, PageRequest.of(0, bounded));
    }

    @Override
    public Flux<CheckInConflict> conflicts(String eventId, String organizerId, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        return conflictRepository.findByEventIdAndOrganizerIdOrderByDetectedAtDesc(
                eventId, organizerId, PageRequest.of(boundedPage, boundedSize));
    }

    @Override
    public Mono<Long> countConflicts(String eventId, String organizerId) {
        return conflictRepository.countByEventIdAndOrganizerId(eventId, organizerId);
    }

    @Override
    public Mono<CheckInConflict> reviewConflict(String conflictId, String organizerId,
                                                 String note, String reviewedBy) {
        return conflictRepository.findById(conflictId)
                // Empty rather than an error: a conflict belonging to another
                // organizer must be indistinguishable from one that does not
                // exist, or the resolver becomes a way to probe for events.
                .filter(conflict -> organizerId != null && organizerId.equals(conflict.getOrganizerId()))
                .flatMap(conflict -> {
                    conflict.setStatus(CheckInConflictStatus.REVIEWED);
                    conflict.setReviewNote(note);
                    conflict.setReviewedBy(reviewedBy);
                    conflict.setReviewedAt(LocalDateTime.now());
                    return conflictRepository.save(conflict);
                });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
