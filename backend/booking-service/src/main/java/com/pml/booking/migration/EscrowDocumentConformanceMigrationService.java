package com.pml.booking.migration;

import com.pml.booking.persistence.BookingCollections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Moves the escrow collection and its field names onto the {@code EventEscrowAccount} shape.
 *
 * <h2>Why the rename and the move are one migration</h2>
 * The model now reads {@code booking_escrow_accounts} with {@code totalCredited}
 * and {@code holdUntil}. Doing either half alone leaves the application pointed
 * at something that does not exist: rename the fields without moving and the old
 * collection is orphaned; move without renaming and every balance projection
 * reads null, which for a money field means a dashboard of zeros rather than an
 * error. They ship together because neither is safe alone.
 *
 * <h2>Copy, then verify, then drop</h2>
 * The old collection is not renamed in place. It is copied to the new name,
 * the counts are compared, and only then is the original dropped — and if the
 * counts disagree the drop is skipped and the discrepancy is reported. A rename
 * that half-completes on the collection holding organizers' money is not
 * something to discover later from a support ticket.
 *
 * <h2>Idempotent</h2>
 * If the old collection is already gone the migration reports zero and does
 * nothing. Re-running after a successful run is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EscrowDocumentConformanceMigrationService {

    // This class is the ONE place the pre-conformance name must survive: it is
    // the thing that reads it. A find-and-replace across the service that
    // "helpfully" updates this line makes the source and target identical, and
    // the migration then copies the collection onto itself and drops it.
    private static final String OLD_COLLECTION = "event_escrow_accounts";
    private static final String NEW_COLLECTION = BookingCollections.ESCROW_ACCOUNTS;

    /** Old field name → the field's current name. */
    private static final Map<String, String> RENAMES = Map.of(
            "totalDeposits", "totalCredited",
            "totalWithdrawals", "totalDebited",
            "totalRefunds", "totalRefunded",
            "lockUntil", "holdUntil");

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /**
     * @param copied    documents moved into the new collection
     * @param renamed   documents whose field names were rewritten
     * @param oldDropped whether the source collection was removed
     * @param problem   set when the counts disagreed and the drop was skipped
     */
    public record Result(long copied, long renamed, boolean oldDropped, String problem) {
        public boolean isClean() {
            return problem == null;
        }
    }

    public Mono<Result> migrate() {
        // Refuse rather than destroy. $out replaces its target wholesale, so if
        // these two names are ever equal this method drops the collection it
        // just wrote — every escrow on the platform, silently, in one call.
        // Cheap to check, and the failure it prevents is unrecoverable.
        if (OLD_COLLECTION.equals(NEW_COLLECTION)) {
            return Mono.error(new IllegalStateException(
                    "Escrow migration source and target are both '" + OLD_COLLECTION
                            + "'. Refusing to run: this would drop the collection it just wrote."));
        }

        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();

        return mongo.collectionExists(OLD_COLLECTION)
                .flatMap(exists -> {
                    if (!exists) {
                        log.info("Escrow document conformance: {} is already gone, nothing to do",
                                OLD_COLLECTION);
                        return renameFieldsInPlace(mongo)
                                .map(renamed -> new Result(0, renamed, false, null));
                    }
                    return copyAcross(mongo);
                });
    }

    /**
     * Handles the case where the collection has already moved but a document
     * written by an older node still carries the old field names.
     */
    private Mono<Long> renameFieldsInPlace(ReactiveMongoTemplate mongo) {
        Document renameSpec = new Document();
        RENAMES.forEach(renameSpec::append);

        // $exists on ANY old name — an $or, so a document part-way through is
        // still caught rather than skipped for not matching all four.
        List<Document> anyOldField = RENAMES.keySet().stream()
                .map(old -> new Document(old, new Document("$exists", true)))
                .toList();

        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(NEW_COLLECTION).updateMany(
                        new Document("$or", anyOldField),
                        new Document("$rename", renameSpec))))
                .map(r -> r.getModifiedCount())
                .doOnNext(n -> {
                    if (n > 0) {
                        log.info("  renamed fields on {} documents already in {}", n, NEW_COLLECTION);
                    }
                })
                .defaultIfEmpty(0L);
    }

    private Mono<Result> copyAcross(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase().flatMap(db -> {
            Document renameSpec = new Document();
            RENAMES.forEach(renameSpec::append);

            // $out replaces the target wholesale, so this is not additive — it
            // is only safe because the new collection cannot yet hold anything
            // the old one does not. Guarded below by the count check.
            List<Document> pipeline = List.of(
                    new Document("$set", new Document()
                            .append("totalCredited", "$totalDeposits")
                            .append("totalDebited", "$totalWithdrawals")
                            .append("totalRefunded", "$totalRefunds")
                            .append("holdUntil", "$lockUntil")),
                    new Document("$unset", List.of(
                            "totalDeposits", "totalWithdrawals", "totalRefunds", "lockUntil")),
                    new Document("$out", NEW_COLLECTION));

            return Mono.from(db.getCollection(OLD_COLLECTION).aggregate(pipeline).toCollection())
                    .then(Mono.from(db.getCollection(OLD_COLLECTION).countDocuments()))
                    .flatMap(oldCount -> Mono.from(db.getCollection(NEW_COLLECTION).countDocuments())
                            .flatMap(newCount -> {
                                if (!oldCount.equals(newCount)) {
                                    String problem = String.format(
                                            "%s held %d documents but %s received %d — "
                                                    + "source NOT dropped, investigate before retrying",
                                            OLD_COLLECTION, oldCount, NEW_COLLECTION, newCount);
                                    log.error("Escrow document conformance: {}", problem);
                                    return Mono.just(new Result(newCount, newCount, false, problem));
                                }
                                return Mono.from(db.getCollection(OLD_COLLECTION).drop())
                                        .thenReturn(new Result(newCount, newCount, true, null))
                                        .doOnSuccess(r -> log.info(
                                                "Escrow document conformance (ET-FIN-001): "
                                                        + "{} documents moved to {}, fields renamed, "
                                                        + "{} dropped",
                                                r.copied(), NEW_COLLECTION, OLD_COLLECTION));
                            }));
        });
    }
}
