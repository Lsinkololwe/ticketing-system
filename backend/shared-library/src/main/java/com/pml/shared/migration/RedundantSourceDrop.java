package com.pml.shared.migration;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes a pre-registry collection once its contents are provably already in the registry one.
 *
 * <h2>Why this exists separately from the rename</h2>
 * {@link CollectionRenameMigration} refuses to move a collection onto a target that already holds
 * documents, because {@code renameCollection} would destroy them. That refusal is correct and it
 * leaves both collections in place — which is safe, and is not a resting state. Something has to
 * decide which population is authoritative.
 *
 * <h2>It proves redundancy; it does not assume it</h2>
 * The source is dropped only when <b>every</b> {@code _id} in it is already present in the
 * target. Anything else — one document the target does not have — and the drop is refused, with
 * the count reported, because that document is the whole reason the question is interesting.
 *
 * <p>The alternative reading, "the target is newer so the source is stale", is the one that
 * loses data: a collection can be newer and still be missing rows the older one has, and there is
 * no signal at drop time that would distinguish the two. Comparing ids is cheap and it is the
 * only thing here that is actually evidence.</p>
 *
 * <h2>_id is the right key, and its limits</h2>
 * Two documents with the same {@code _id} are the same document, so id containment answers
 * "is anything here that is not there". It does <b>not</b> answer "does the target hold a
 * different <em>version</em> of this document" — if the same id carries different fields in each,
 * this reports redundant and the target's copy wins. That is the intended outcome when the target
 * is the collection the code now reads, and it is why the report names counts rather than
 * claiming the two are identical.
 */
@Slf4j
public final class RedundantSourceDrop {

    /**
     * How many unmatched ids to gather before stopping.
     *
     * <p>The scan stops here because the report is for a human deciding what to do, and the
     * decision is the same at 25 as at 25,000: this collection is not redundant. Gathering the
     * rest would walk the whole collection to change nothing.</p>
     */
    private static final int SAMPLE_LIMIT = 25;

    private final ReactiveMongoTemplate mongoTemplate;

    public RedundantSourceDrop(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Runs {@link #dropIfRedundant} for each pair and joins the reports. */
    public Mono<String> dropAll(List<String[]> sourceTargetPairs) {
        return Flux.fromIterable(sourceTargetPairs)
                .concatMap(pair -> dropIfRedundant(pair[0], pair[1]))
                .collectList()
                .map(reports -> String.join("; ", reports));
    }

    public Mono<String> dropIfRedundant(String source, String target) {
        return collectionExists(source)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.just(source + ": already absent");
                    }
                    return idsNotIn(source, target)
                            .flatMap(missing -> {
                                if (!missing.isEmpty()) {
                                    // "at least" once the cap is reached. A capped count printed
                                    // as an exact one is worse than no count: it reads as a
                                    // small, surveyable difference and can stand for any number.
                                    String count = missing.size() >= SAMPLE_LIMIT
                                            ? "at least " + SAMPLE_LIMIT
                                            : String.valueOf(missing.size());
                                    String report = ("%s: KEPT — %s document(s) are not in %s "
                                            + "(first: %s). Reconcile before dropping.")
                                            .formatted(source, count, target, missing.get(0));
                                    log.warn(report);
                                    return Mono.just(report);
                                }
                                return drop(source)
                                        .thenReturn("%s: dropped — every document was already in %s"
                                                .formatted(source, target));
                            });
                });
    }

    private Mono<Boolean> collectionExists(String collection) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> database.listCollectionNames())
                .any(collection::equals);
    }

    /**
     * The source {@code _id}s the target does not have, capped at a readable number.
     *
     * <p>Capped because the report is for a human deciding what to do; a thousand ids in a log
     * line is the same information as ten plus a count, and less usable.</p>
     */
    private Mono<List<Object>> idsNotIn(String source, String target) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> Flux.from(
                                database.getCollection(source)
                                        .find()
                                        .projection(new Document("_id", 1)))
                        .map(document -> document.get("_id"))
                        .concatMap(id -> Mono.from(database.getCollection(target)
                                        .find(new Document("_id", id))
                                        .projection(new Document("_id", 1))
                                        .first())
                                .map(found -> List.of())
                                .defaultIfEmpty(List.of(id))
                                .flatMapMany(Flux::fromIterable)))
                .take(SAMPLE_LIMIT)
                .collectList();
    }

    private Mono<Void> drop(String collection) {
        return mongoTemplate.getMongoDatabase()
                .flatMap(database -> Mono.from(database.getCollection(collection).drop()))
                .then();
    }
}
