package com.pml.shared.migration;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brings documents written before a field existed up to the shape the code now expects.
 *
 * <h2>Why a code change is only half of any of these</h2>
 * Adding a field to a {@code @Document} changes what is written from now on and nothing about
 * what is already stored. Each of the three operations here exists because the gap it closes is
 * silent:
 *
 * <ul>
 *   <li><b>A missing default.</b> {@code @Builder.Default} applies when an object is built, not
 *       when one is read. A document written before {@code currency} existed deserialises with
 *       {@code currency == null}, and an amount whose currency is null is an amount whose
 *       currency is a guess.</li>
 *   <li><b>A missing version.</b> {@code @Version} on a document with no {@code version} field
 *       makes Spring Data treat it as new — so the first update to an existing row is an insert
 *       attempt, and optimistic locking protects nothing until something writes it once.</li>
 *   <li><b>A stale {@code _class}.</b> Adding {@code @TypeAlias} makes new writes store the
 *       alias while old documents keep the fully-qualified class name. Both read correctly today
 *       — and the day the class moves package, every document still carrying the old name stops
 *       deserialising, which is the failure the alias was added to prevent.</li>
 * </ul>
 *
 * <h2>Only documents that need it are touched</h2>
 * Every operation filters on the absence of what it sets, so a second run matches nothing and
 * the reported count is zero rather than the collection size. That makes re-running safe, and it
 * makes the count worth reading: a non-zero result on a run that should have been a no-op means
 * something is still writing the old shape.
 */
@Slf4j
public final class DocumentBackfill {

    public record Result(Map<String, Long> updatedByOperation) {

        public long total() {
            return updatedByOperation.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public String toString() {
            if (total() == 0) {
                return "nothing to backfill";
            }
            return updatedByOperation.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList()
                    .toString();
        }
    }

    private final ReactiveMongoTemplate mongoTemplate;
    private final List<Operation> operations = new ArrayList<>();

    public DocumentBackfill(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private record Operation(String label, String collection, Document filter, Document update) {
    }

    /** Sets {@code field} to {@code value} on documents that do not have it. */
    public DocumentBackfill setWhereMissing(String collection, String field, Object value) {
        operations.add(new Operation(
                collection + "." + field,
                collection,
                new Document(field, new Document("$exists", false)),
                new Document("$set", new Document(field, value))));
        return this;
    }

    /**
     * Replaces a fully-qualified {@code _class} with the alias the document now declares.
     *
     * <p>Matched on the exact old value rather than a prefix: rewriting every {@code _class}
     * that starts with {@code com.pml} would also rewrite the discriminator of a subtype stored
     * in the same collection, collapsing two shapes into one.</p>
     */
    public DocumentBackfill rewriteClassTo(String collection, String fullyQualifiedName, String alias) {
        operations.add(new Operation(
                collection + "._class",
                collection,
                new Document("_class", fullyQualifiedName),
                new Document("$set", new Document("_class", alias))));
        return this;
    }

    public Mono<Result> run() {
        return Flux.fromIterable(operations)
                // concatMap, not flatMap: a backfill competing with itself for the same
                // documents is a lock-contention problem with no upside — these run once.
                .concatMap(operation -> mongoTemplate.getMongoDatabase()
                        .flatMap(database -> Mono.from(database.getCollection(operation.collection())
                                .updateMany(operation.filter(), operation.update())))
                        .map(update -> Map.entry(operation.label(), update.getModifiedCount()))
                        .onErrorResume(error -> {
                            log.error("Backfill {} failed: {}", operation.label(), error.getMessage());
                            return Mono.just(Map.entry(operation.label() + " (FAILED)", 0L));
                        }))
                .collectList()
                .map(entries -> {
                    Map<String, Long> byOperation = new LinkedHashMap<>();
                    entries.forEach(entry -> byOperation.merge(entry.getKey(), entry.getValue(), Long::sum));
                    Result result = new Result(Map.copyOf(byOperation));
                    log.info("Document backfill: {}", result);
                    return result;
                });
    }
}
