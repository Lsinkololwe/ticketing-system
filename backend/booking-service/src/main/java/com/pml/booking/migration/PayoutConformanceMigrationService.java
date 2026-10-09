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
 * Moves payout requests onto their current collection and status set.
 *
 * <h2>Two changes, one migration</h2>
 * The collection becomes {@code booking_payout_requests}, and
 * {@code PENDING_FINANCE_APPROVAL} becomes {@code PENDING}. They ship together
 * because the enum constant is already gone from the code: a row still holding
 * the retired value cannot be deserialised at all, so moving the rows without
 * rewriting the status just relocates unreadable documents.
 *
 * <h2>Why the status collapses rather than being kept</h2>
 * A payout has exactly seven statuses. The eighth described the same
 * fact as {@code PENDING} — a request sitting in the finance queue, waiting on a
 * person. Two codes for one state means every query must remember both, and the
 * one that forgets under-reports the approval backlog. A queue that looks
 * shorter than it is does not get investigated.
 *
 * <p>The semantic moves with it. Both codes mapped to {@code INITIAL}, so this
 * one happens to be a no-op — asserted rather than assumed, because the next
 * status collapse will not be.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PayoutConformanceMigrationService {

    // As with the escrow migration, this class is the one place the
    // pre-conformance name must survive a find-and-replace: it is the thing
    // that reads it.
    private static final String OLD_COLLECTION = "payout_requests";
    private static final String NEW_COLLECTION = BookingCollections.PAYOUT_REQUESTS;

    private static final String RETIRED_STATUS = "PENDING_FINANCE_APPROVAL";
    private static final String REPLACEMENT_STATUS = "PENDING";

    /**
     * Old field name → the field's current name.
     *
     * <p>{@code netPayoutAmount → settledAmount} is not cosmetic: "net" reads as
     * "gross minus fees, computed once", and the whole point is that this figure
     * is RECOMPUTED at approval because money can move in between. The old name
     * described a guarantee the system does not make.
     */
    private static final Map<String, String> RENAMES = Map.of(
            "netPayoutAmount", "settledAmount",
            "requestedBy", "requestedById");

    private final ObjectProvider<ReactiveMongoTemplate> mongoTemplateProvider;

    /**
     * @param moved            documents relocated to the new collection
     * @param statusesRewritten rows whose retired status was collapsed
     * @param oldDropped       whether the source collection was removed
     * @param problem          set when counts disagreed and the drop was skipped
     */
    public record Result(long moved, long statusesRewritten, boolean oldDropped, String problem) {
        public boolean isClean() {
            return problem == null;
        }
    }

    public Mono<Result> migrate() {
        if (OLD_COLLECTION.equals(NEW_COLLECTION)) {
            return Mono.error(new IllegalStateException(
                    "Payout migration source and target are both '" + OLD_COLLECTION
                            + "'. Refusing to run: this would drop the collection it just wrote."));
        }

        ReactiveMongoTemplate mongo = mongoTemplateProvider.getObject();

        return mongo.collectionExists(OLD_COLLECTION)
                .flatMap(exists -> exists
                        ? move(mongo).flatMap(moved -> renameFields(mongo)
                                .then(rewriteStatuses(mongo))
                                .map(rewritten -> new Result(moved.moved(), rewritten,
                                        moved.oldDropped(), moved.problem())))
                        // Already moved: still sweep for retired statuses, which
                        // a node running the old code could have written since.
                        : renameFields(mongo)
                                .then(rewriteStatuses(mongo))
                                .map(rewritten -> new Result(0, rewritten, false, null)))
                .doOnSuccess(r -> log.info(
                        "Payout conformance (ET-FIN-003): {} moved to {}, {} statuses collapsed{}",
                        r.moved(), NEW_COLLECTION, r.statusesRewritten(),
                        r.isClean() ? "" : " — PROBLEM: " + r.problem()));
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
                                                "%s held %d documents but %s received %d — "
                                                        + "source NOT dropped",
                                                OLD_COLLECTION, oldCount, NEW_COLLECTION, newCount);
                                        log.error("Payout conformance: {}", problem);
                                        return Mono.just(new Result(newCount, 0, false, problem));
                                    }
                                    return Mono.from(db.getCollection(OLD_COLLECTION).drop())
                                            .thenReturn(new Result(newCount, 0, true, null));
                                })));
    }

    /**
     * Rename the retired field names on whatever is in the new collection.
     *
     * <p>Matches on {@code $or} of the old names rather than requiring all of
     * them, so a document part-way through — one field renamed, one not — is
     * still repaired instead of skipped for not matching the full set.
     */
    private Mono<Long> renameFields(ReactiveMongoTemplate mongo) {
        Document renameSpec = new Document();
        RENAMES.forEach(renameSpec::append);
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
                        log.info("  renamed payout fields on {} documents", n);
                    }
                })
                .defaultIfEmpty(0L);
    }

    private Mono<Long> rewriteStatuses(ReactiveMongoTemplate mongo) {
        return mongo.getMongoDatabase()
                .flatMap(db -> Mono.from(db.getCollection(NEW_COLLECTION).updateMany(
                        new Document("status", RETIRED_STATUS),
                        // statusSemantic set alongside: both codes mean INITIAL,
                        // so it does not change here, but writing it keeps the
                        // pair consistent by construction rather than by luck.
                        new Document("$set", new Document("status", REPLACEMENT_STATUS)
                                .append("statusSemantic", "INITIAL")))))
                .map(r -> r.getModifiedCount())
                .doOnNext(n -> {
                    if (n > 0) {
                        log.info("  {} -> {}: {} payout requests", RETIRED_STATUS, REPLACEMENT_STATUS, n);
                    }
                })
                .defaultIfEmpty(0L);
    }
}
