package com.pml.booking.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves reservations onto ET-TKT-001's collection and five states.
 *
 * <h2>The one mapping that loses information</h2>
 * {@code CANCELLED → RELEASED} is safe: both mean the seats went back.
 * {@code ACTIVE → HELD} and {@code CONVERTED → CONFIRMED} are renames.
 *
 * <p>What the old set could not express is {@link
 * com.pml.shared.constants.ReservationStatus#FAILED} — a purchase that
 * resolved payment but could not be completed. Under the old four, those rows
 * were marked {@code CANCELLED}, indistinguishable from a buyer changing their
 * mind. This migration therefore cannot recover them: it maps every
 * {@code CANCELLED} to {@code RELEASED}, and any genuine failure hiding among
 * them stays hidden.
 *
 * <p>Said plainly because it is the kind of thing that gets discovered later as
 * "why did nobody chase this refund": the information was destroyed before this
 * migration ran, by a status set that had nowhere to put it. Going forward the
 * distinction exists.
 *
 * @see <a href="file:../../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReservationConformanceMigrationService {

    // The one place the pre-conformance name must survive a find-and-replace.
    private static final String OLD_COLLECTION = "ticket_reservations";
    private static final String NEW_COLLECTION = "booking_reservations";

    private static final Map<String, String> STATUS_MAPPING = new LinkedHashMap<>(Map.of(
            "ACTIVE", "HELD",
            "CONVERTED", "CONFIRMED",
            "CANCELLED", "RELEASED"));

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    public record Result(long moved, long statusesRewritten, boolean oldDropped, String problem) {
        public boolean isClean() {
            return problem == null;
        }
    }

    public Mono<Result> migrate() {
        if (OLD_COLLECTION.equals(NEW_COLLECTION)) {
            return Mono.error(new IllegalStateException(
                    "Reservation migration source and target are both '" + OLD_COLLECTION
                            + "'. Refusing to run: this would drop the collection it just wrote."));
        }

        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();

        return mongo.collectionExists(OLD_COLLECTION)
                .flatMap(exists -> exists
                        ? move(mongo).flatMap(moved -> rewriteStatuses(mongo)
                                .flatMap(n -> renameFields(mongo).thenReturn(n))
                                .map(n -> new Result(moved.moved(), n, moved.oldDropped(), moved.problem())))
                        : rewriteStatuses(mongo)
                                .flatMap(n -> renameFields(mongo).thenReturn(n))
                                .map(n -> new Result(0, n, false, null)))
                .doOnSuccess(r -> log.info(
                        "Reservation conformance (ET-TKT-001): {} moved to {}, {} statuses rewritten",
                        r.moved(), NEW_COLLECTION, r.statusesRewritten()));
    }

    private Mono<Result> move(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase().flatMap(db ->
                Mono.from(db.getCollection(OLD_COLLECTION)
                                .aggregate(List.of(new Document("$out", NEW_COLLECTION)))
                                .toCollection())
                        .then(Mono.from(db.getCollection(OLD_COLLECTION).countDocuments()))
                        .flatMap(oldCount -> Mono.from(db.getCollection(NEW_COLLECTION).countDocuments())
                                .flatMap(newCount -> {
                                    if (!oldCount.equals(newCount)) {
                                        String problem = String.format(
                                                "%s held %d documents but %s received %d — source NOT dropped",
                                                OLD_COLLECTION, oldCount, NEW_COLLECTION, newCount);
                                        log.error("Reservation conformance: {}", problem);
                                        return Mono.just(new Result(newCount, 0, false, problem));
                                    }
                                    return Mono.from(db.getCollection(OLD_COLLECTION).drop())
                                            .thenReturn(new Result(newCount, 0, true, null));
                                })));
    }

    /**
     * {@code convertedAt} → {@code confirmedAt}.
     *
     * <p>A rename rather than a new field, because leaving both would mean two
     * places recording when a purchase completed and no rule about which wins.
     * The old name also mislabelled what it held: a reservation is not
     * "converted", it is confirmed, and the distinction is the whole reason
     * ET-TKT-001 §4 gives each terminal state its own timestamp.
     *
     * <p>Reservations that ended any other way get no timestamp from this: the
     * old model had nowhere to record when a hold was released or failed, so
     * those fields stay null for historical rows. Inventing values would be
     * worse than admitting the gap.
     */
    private Mono<Long> renameFields(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(NEW_COLLECTION).updateMany(
                        new Document("convertedAt", new Document("$exists", true)),
                        new Document("$rename", new Document("convertedAt", "confirmedAt")))))
                .map(result -> {
                    long n = result.getModifiedCount();
                    if (n > 0) {
                        log.info("  convertedAt -> confirmedAt: {} reservations", n);
                    }
                    return n;
                });
    }

    private Mono<Long> rewriteStatuses(ReactiveMongoTemplate mongo) {
        return Flux.fromIterable(STATUS_MAPPING.entrySet())
                .concatMap(entry -> mongo.getMongoDatabase()
                        .flatMap(db -> Mono.from(db.getCollection(NEW_COLLECTION).updateMany(
                                new Document("status", entry.getKey()),
                                new Document("$set", new Document("status", entry.getValue())))))
                        .map(r -> {
                            long n = r.getModifiedCount();
                            if (n > 0) {
                                log.info("  {} -> {}: {} reservations", entry.getKey(), entry.getValue(), n);
                                if ("CANCELLED".equals(entry.getKey())) {
                                    log.warn("  {} reservations became RELEASED. If any were actually "
                                            + "failed purchases, that was already unrecorded — the old "
                                            + "status set had no FAILED state to put them in", n);
                                }
                            }
                            return n;
                        }))
                .reduce(0L, Long::sum);
    }
}
