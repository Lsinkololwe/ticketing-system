package com.pml.shared.migration;

import com.mongodb.MongoNamespace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves collections onto their registered names.
 *
 * <h2>Why one engine and not one class per collection</h2>
 * Thirty-eight collections change name. Thirty-eight near-identical migration classes is
 * thirty-eight chances for one of them to differ from the others, and the difference would not
 * announce itself — a migration that quietly does nothing reports the same clean zero as a
 * migration that had nothing to do. The table is the migration; this is the engine that runs it.
 *
 * <h2>Rename, not copy-and-drop</h2>
 * MongoDB's {@code renameCollection} is atomic, keeps the collection's indexes, and needs no
 * second copy of the data on disk. The alternative in this codebase is {@code $out} followed by
 * a drop, which is three separate failure points and briefly doubles storage for collections
 * that hold every ticket on the platform.
 *
 * <h2>What it refuses to do</h2>
 * Each of these is a case where continuing destroys data, so each stops that pair rather than
 * the whole run — the outcome is reported and the other renames still proceed:
 *
 * <ul>
 *   <li><b>Source and target the same name.</b> Cheap to check, and the alternative is a
 *       migration that drops what it has just written.</li>
 *   <li><b>Target exists and holds documents.</b> Two populated collections mean somebody has
 *       already written to the new name, and no automatic merge is safe: the rename would
 *       replace those documents wholesale. A human decides which set is authoritative.</li>
 * </ul>
 *
 * <h2>What it handles rather than refusing</h2>
 * <ul>
 *   <li><b>Target exists and is empty.</b> This is the normal case, not an anomaly: the
 *       services have already started against the renamed code, and creating an index — or a
 *       schema validator — creates the collection. Refusing here would mean the migration never
 *       runs on any environment where the application booted first, which is all of them. The
 *       empty target is dropped and the rename proceeds.</li>
 *   <li><b>Source absent.</b> Either the environment is new or the rename already ran. Both are
 *       success, and distinguishing them is what the ledger is for.</li>
 * </ul>
 */
@Slf4j
public final class CollectionRenameMigration {

    public enum Status {
        /** The collection moved. */
        RENAMED,
        /** No such source collection — a new environment, or already migrated. */
        SOURCE_ABSENT,
        /** An empty collection at the new name was removed to make way. */
        RENAMED_OVER_EMPTY,
        /** Refused: proceeding would have destroyed documents. */
        REFUSED
    }

    public record Outcome(String from, String to, Status status, String detail) {
    }

    public record Result(List<Outcome> outcomes) {

        public long renamed() {
            return outcomes.stream()
                    .filter(o -> o.status() == Status.RENAMED || o.status() == Status.RENAMED_OVER_EMPTY)
                    .count();
        }

        public List<Outcome> refusals() {
            return outcomes.stream().filter(o -> o.status() == Status.REFUSED).toList();
        }

        public boolean isClean() {
            return refusals().isEmpty();
        }

        @Override
        public String toString() {
            long absent = outcomes.stream().filter(o -> o.status() == Status.SOURCE_ABSENT).count();
            String summary = "%d renamed, %d already absent, %d refused"
                    .formatted(renamed(), absent, refusals().size());
            if (isClean()) {
                return summary;
            }
            return summary + " — " + refusals().stream()
                    .map(o -> o.from() + "→" + o.to() + ": " + o.detail())
                    .toList();
        }
    }

    private final ReactiveMongoTemplate mongoTemplate;

    public CollectionRenameMigration(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param renames source collection → its registered name, in a stable order
     */
    public Mono<Result> migrate(Map<String, String> renames) {
        return Flux.fromIterable(new LinkedHashMap<>(renames).entrySet())
                // concatMap: one at a time. Renames are cheap, and a refusal is much easier to
                // read in a log that is not interleaved with thirty-seven other collections.
                .concatMap(entry -> rename(entry.getKey(), entry.getValue()))
                .collectList()
                .map(outcomes -> {
                    Result result = new Result(List.copyOf(outcomes));
                    if (result.isClean()) {
                        log.info("Collection renames: {}", result);
                    } else {
                        log.error("Collection renames completed with refusals: {}", result);
                    }
                    return result;
                });
    }

    private Mono<Outcome> rename(String from, String to) {
        if (from.equals(to)) {
            return Mono.just(new Outcome(from, to, Status.REFUSED,
                    "source and target are the same collection"));
        }

        return mongoTemplate.collectionExists(from)
                .flatMap(sourceExists -> {
                    if (!sourceExists) {
                        return Mono.just(new Outcome(from, to, Status.SOURCE_ABSENT,
                                "nothing to move"));
                    }
                    return mongoTemplate.collectionExists(to)
                            .flatMap(targetExists -> targetExists
                                    ? renameOverExisting(from, to)
                                    : doRename(from, to, Status.RENAMED, "moved"));
                });
    }

    private Mono<Outcome> renameOverExisting(String from, String to) {
        return mongoTemplate.estimatedCount(to)
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.just(new Outcome(from, to, Status.REFUSED,
                                "target already holds %d document(s); refusing to replace them"
                                        .formatted(count)));
                    }
                    // Empty, so it was created by index or validator setup rather than by a
                    // write. Dropping it loses nothing and lets the rename carry the source's
                    // own indexes across.
                    return mongoTemplate.dropCollection(to)
                            .then(doRename(from, to, Status.RENAMED_OVER_EMPTY,
                                    "moved over an empty collection created at startup"));
                });
    }

    private Mono<Outcome> doRename(String from, String to, Status status, String detail) {
        return mongoTemplate.getMongoDatabase()
                .flatMap(database -> Mono
                        .from(database.getCollection(from)
                                .renameCollection(new MongoNamespace(database.getName(), to)))
                        .thenReturn(new Outcome(from, to, status, detail)))
                .onErrorResume(error -> Mono.just(new Outcome(from, to, Status.REFUSED,
                        "rename failed: " + error.getMessage())));
    }

    /** Convenience for the per-service tables, which are written as ordered literals. */
    public static Map<String, String> table(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("rename table must be source/target pairs");
        }
        Map<String, String> renames = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (renames.put(pairs[i], pairs[i + 1]) != null) {
                duplicates.add(pairs[i]);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate source collection(s): " + duplicates);
        }
        return renames;
    }
}
