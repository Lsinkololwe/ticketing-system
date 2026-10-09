package com.pml.booking.migration;

import com.pml.booking.domain.enums.ValidationMethod;
import com.pml.booking.domain.model.CheckIn;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.CheckInRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Brings {@code booking_checkins} up to date with tickets that were already
 * admitted before the collection existed.
 *
 * <h2>Why this is needed</h2>
 * Check-in used to be a {@code validatedAt} timestamp on the ticket. Attendance
 * is now counted over {@code booking_checkins}, so without a backfill every
 * event that has already run reports <b>zero admissions</b> — the tickets say
 * VALIDATED and the new collection is empty. An organizer opening a past event
 * would see their whole gate wiped.
 *
 * <h2>Two things it does, in order</h2>
 * <ol>
 *   <li><b>Creates the indexes.</b> Unique on {@code ticketId}; unique and
 *       sparse on {@code scanId}. Auto-index-creation covers a fresh start, but
 *       an existing deployment needs them created explicitly and — importantly
 *       — created BEFORE the backfill, so the backfill itself cannot introduce
 *       the duplicates the index exists to prevent.</li>
 *   <li><b>Backfills one row per already-validated ticket.</b></li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * Re-running is safe. The unique {@code ticketId} index rejects a second row
 * for a ticket, and this treats that rejection as "already migrated" rather
 * than as a failure. That is a stronger guarantee than checking first and then
 * writing, which two operators running the migration at once would both pass.
 *
 * <h2>What it deliberately does not do</h2>
 * It does not invent conflicts. A ticket carries a single {@code validatedAt}, so
 * a historical double-admission is indistinguishable from a single one — the
 * second scan left nothing behind to recover. {@code booking_checkin_conflicts}
 * therefore starts empty and honest rather than populated with guesses.
 *
 * @see com.pml.booking.domain.model.CheckIn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInBackfillMigrationService {

    /** Ticket states that mean the holder passed the gate. */
    private static final List<TicketStatus> ADMITTED_STATES =
            List.of(TicketStatus.VALIDATED);

    private final ReactiveMongoTemplate mongoTemplate;
    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;

    /**
     * The outcome of a run.
     *
     * @param created  rows written by this run
     * @param skipped  tickets that already had a check-in row
     * @param failed   tickets that could not be migrated, for the operator to chase
     */
    public record Result(long created, long skipped, long failed) {
        public long examined() {
            return created + skipped + failed;
        }
    }

    /**
     * Create the indexes, then backfill.
     *
     * <p>Not wired to a runner. A backfill over every ticket ever sold is not
     * something that should happen silently on a pod restart — it is invoked
     * deliberately, once, by an operator who can watch it.
     */
    public Mono<Result> migrate() {
        return ensureIndexes()
                .then(backfill())
                .doOnSuccess(result -> log.info(
                        "Check-in backfill complete: {} created, {} already present, {} failed (of {} examined)",
                        result.created(), result.skipped(), result.failed(), result.examined()));
    }

    /**
     * Create the check-in indexes, under the SAME names the mapping uses.
     *
     * <p>The names are the whole point. {@code BookingIndexInitializer} declares
     * the same three indexes under the same names. MongoDB treats a repeat of an
     * identical (keys, name) pair as a no-op — but rejects the same keys under a
     * <em>different</em> name with error 85. An unnamed create here would be
     * auto-named {@code ticketId_1} against the initializer's {@code ticketId},
     * collide, and abort this migration and every one scheduled after it.
     *
     * <p>These calls earn their place because the backfill may run before the
     * initializer's pass, and the unique {@code ticketId} index is what stops the
     * backfill admitting the same ticket twice.
     */
    public Mono<Void> ensureIndexes() {
        return mongoTemplate.indexOps(CheckIn.class)
                .createIndex(new Index().on("ticketId", Sort.Direction.ASC).unique().named("ticketId"))
                .doOnNext(name -> log.info("Check-in index ready: {}", name))
                // Partial, not sparse. A check-in recorded without an upload batch stores
                // scanId as null rather than omitting it, and sparse excludes only an absent
                // field — so under `unique` the second such check-in would collide with the
                // first, and a gate that recorded one offline scan could record no others.
                .then(mongoTemplate.indexOps(CheckIn.class)
                        .createIndex(new Index().on("scanId", Sort.Direction.ASC)
                                .unique().named("scanId")
                                .partial(PartialIndexFilter.of(
                                        Criteria.where("scanId").type(JsonSchemaObject.Type.stringType())))))
                .doOnNext(name -> log.info("Check-in index ready: {}", name))
                .then(mongoTemplate.indexOps(CheckIn.class)
                        .createIndex(new Index()
                                .on("eventId", Sort.Direction.ASC)
                                .on("recordedAt", Sort.Direction.DESC)
                                .named("checkin_event_recorded_idx")))
                .doOnNext(name -> log.info("Check-in index ready: {}", name))
                .then();
    }

    private Mono<Result> backfill() {
        return ticketRepository.findAll()
                .filter(ticket -> ADMITTED_STATES.contains(ticket.getStatus()))
                .filter(ticket -> ticket.getValidatedAt() != null)
                // Sequential, not concurrent. A backfill competes with live
                // traffic for the same connection pool, and finishing a minute
                // later is a better trade than slowing down a gate.
                .concatMap(this::migrateOne)
                .reduce(new Result(0, 0, 0), (acc, outcome) -> switch (outcome) {
                    case CREATED -> new Result(acc.created() + 1, acc.skipped(), acc.failed());
                    case SKIPPED -> new Result(acc.created(), acc.skipped() + 1, acc.failed());
                    case FAILED -> new Result(acc.created(), acc.skipped(), acc.failed() + 1);
                });
    }

    private enum Outcome { CREATED, SKIPPED, FAILED }

    private Mono<Outcome> migrateOne(Ticket ticket) {
        CheckIn checkIn = CheckIn.builder()
                .ticketId(ticket.getId())
                .eventId(ticket.getEventId())
                .organizerId(ticket.getOrganizerId())
                .ticketNumber(ticket.getTicketNumber())
                // No scanId: these were never scanned by a device that had one.
                // Leaving it null is what the sparse index is for.
                .scanId(null)
                // QR_ONLINE rather than MANUAL. The old flow recorded no method,
                // so this is a reconstruction either way — but classifying every
                // historical admission as MANUAL would make the manual-ratio
                // alert fire on every past event and mean nothing.
                .method(ValidationMethod.QR_ONLINE)
                .scannedBy(null)
                .deviceId(null)
                .scannedAt(ticket.getValidatedAt())
                // The ticket's own timestamp, not now. Backfilling with the
                // migration's clock would stack every historical admission onto
                // the day the migration ran and destroy the arrival curve.
                .recordedAt(ticket.getValidatedAt())
                .reason("Backfilled from ticket.validatedAt (ET-TKT-003 migration)")
                .build();

        return checkInRepository.insert(checkIn)
                .thenReturn(Outcome.CREATED)
                // Already migrated. The index decides, not a prior read — two
                // operators running this at once would both pass a read.
                .onErrorReturn(DuplicateKeyException.class, Outcome.SKIPPED)
                .onErrorResume(error -> {
                    log.warn("Could not backfill check-in for ticket {}: {}",
                            ticket.getTicketNumber(), error.getMessage());
                    return Mono.just(Outcome.FAILED);
                });
    }
}
