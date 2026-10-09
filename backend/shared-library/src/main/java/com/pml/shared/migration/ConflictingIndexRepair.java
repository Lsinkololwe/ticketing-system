package com.pml.shared.migration;

import com.mongodb.client.model.IndexOptions;
import com.pml.shared.persistence.IndexSpec;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Brings an index the database already has into line with the one declared for it.
 *
 * <h2>The case this exists for</h2>
 * A collection carries an index created before the registry existed, under a hand-chosen name.
 * The registry declares the same keys under a registry name. MongoDB refuses the second with
 * error 85, and {@code IndexEnsurer} reports a conflict — correctly, because silently accepting
 * "close enough" is how a declared unique index quietly is not one.
 *
 * <p>Most of those conflicts are cosmetic: identical keys, identical options, different name.
 * <b>Two on this platform are not.</b> {@code booking_reservations} and
 * {@code identity_team_invitations} each carry a <em>plain</em> index on {@code expiresAt} where
 * the registry declares a <em>TTL</em> index. Nothing expires. Unpaid reservations hold their
 * inventory indefinitely, and the index looks correct in {@code getIndexes()} unless you read
 * the options — which is precisely why it survived this long.</p>
 *
 * <h2>Drop then create, and the window that opens</h2>
 * MongoDB will not create the declared index while the old one occupies its keys, so there is no
 * create-then-swap. Between the drop and the create <b>the collection has no index on those
 * keys</b>: queries relying on it collation-scan, and a unique index is not enforcing uniqueness.
 * On the collections here that is milliseconds. On a large production collection it is not, and a
 * unique index gap is a correctness window, not just a slow one — so run this in a maintenance
 * window there, or build the replacement out-of-band and let this step find it already correct.
 *
 * <h2>Idempotent, and it recognises "already right"</h2>
 * An index already carrying the declared name is left alone, so a second run is a no-op and the
 * migration ledger's one-shot guarantee is belt to that braces.
 */
@Slf4j
public final class ConflictingIndexRepair {

    /** What happened to one declared index. */
    public record Outcome(String collection, String declaredName, String action) {
        @Override
        public String toString() {
            return collection + "." + declaredName + ": " + action;
        }
    }

    private final ReactiveMongoTemplate mongoTemplate;

    public ConflictingIndexRepair(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * @param specs the registry's declarations; only those in conflict are touched
     * @return one line per index that was repaired, or a note that nothing needed it
     */
    public Mono<String> repair(List<IndexSpec> specs) {
        return Flux.fromIterable(specs)
                // concatMap: two specs can name the same collection, and MongoDB serialises
                // index builds per collection anyway. Doing it in order keeps the log readable.
                .concatMap(this::repairOne)
                .collectList()
                .map(outcomes -> {
                    // Only what was acted on. "already declared" and "absent, not conflicting"
                    // are both no-ops, and reporting them would make a run that changed nothing
                    // read as a run that did something.
                    List<Outcome> repaired = outcomes.stream()
                            .filter(outcome -> outcome.action().startsWith("replaced")
                                    || outcome.action().startsWith("FAILED"))
                            .toList();
                    if (repaired.isEmpty()) {
                        return "no index needed repair";
                    }
                    log.warn("Index repair: {}", repaired);
                    return repaired.toString();
                });
    }

    private Mono<Outcome> repairOne(IndexSpec spec) {
        return existingIndexes(spec.collection())
                .flatMap(existing -> {
                    // The declared name being present is not the same as the declared index being
                    // present. An index can carry the right name and the wrong options — the
                    // server then refuses the declaration with error 86 rather than 85, and a
                    // check that stopped at the name would report "already declared" while the
                    // constraint on disk is a different one. So the options are compared too.
                    Document underDeclaredName = existing.stream()
                            .filter(index -> spec.name().equals(index.getString("name")))
                            .findFirst().orElse(null);
                    if (underDeclaredName != null && optionsMatch(spec, underDeclaredName)) {
                        return Mono.just(new Outcome(spec.collection(), spec.name(), "already declared"));
                    }

                    List<String> occupying = existing.stream()
                            .filter(index -> occupiesTheSameKeys(spec, index)
                                    || spec.name().equals(index.getString("name")))
                            .map(index -> index.getString("name"))
                            .filter(name -> !"_id_".equals(name))
                            .toList();

                    if (occupying.isEmpty()) {
                        // Nothing holds these keys, so IndexEnsurer's next pass creates it
                        // normally. Not this step's job.
                        return Mono.just(new Outcome(spec.collection(), spec.name(), "absent, not conflicting"));
                    }

                    // The definitions of what is about to be dropped, kept so the drop can be
                    // undone. Without this a failed create leaves the collection with NO index
                    // on those keys — a unique constraint silently not enforced, and a query
                    // path that was indexed a moment ago now scanning. A repair that can leave
                    // things worse than it found them is not one worth running.
                    List<Document> replaced = existing.stream()
                            .filter(index -> occupying.contains(index.getString("name")))
                            .toList();

                    return dropAll(spec.collection(), occupying)
                            .then(create(spec))
                            .thenReturn(new Outcome(spec.collection(), spec.name(),
                                    "replaced " + occupying))
                            .onErrorResume(failed -> restore(spec.collection(), replaced)
                                    .thenReturn(new Outcome(spec.collection(), spec.name(),
                                            "FAILED, original restored: " + failed.getMessage())));
                })
                .onErrorResume(error -> Mono.just(new Outcome(
                        spec.collection(), spec.name(), "FAILED: " + error.getMessage())));
    }

    /**
     * Whether {@code existing} holds the keys {@code spec} wants.
     *
     * <h2>Text indexes do not store the keys you gave them</h2>
     * A declaration of {@code text("title", "description")} is stored by the server as
     * {@code key: { _fts: "text", _ftsx: 1 }}, with the field names moved into a separate
     * {@code weights} document. Comparing the declared key document to the stored one therefore
     * never matches for a text index — the repair finds nothing to do while
     * {@code IndexEnsurer} goes on reporting the conflict, and the two disagree in the log with
     * no indication which is right.
     *
     * <p>A collection may hold at most one text index, so {@code _fts} being present is
     * sufficient: it is the text index, whatever it is called and whatever it covers.</p>
     */
    /**
     * Whether the index on disk enforces what the declaration asks for.
     *
     * <p>Only the options that change <em>behaviour</em> are compared. {@code unique} and the
     * partial filter decide what is permitted; the TTL decides what disappears; {@code sparse}
     * decides what is covered. A difference in any of them is a different constraint wearing the
     * same name, which is the case worth catching — an index named {@code idx_email} that is
     * sparse where the registry says partial admits a second user with a null address and reads
     * as correct in every listing.</p>
     */
    private static boolean optionsMatch(IndexSpec spec, Document existing) {
        boolean unique = Boolean.TRUE.equals(existing.getBoolean("unique"));
        boolean sparse = Boolean.TRUE.equals(existing.getBoolean("sparse"));
        Number ttl = existing.get("expireAfterSeconds", Number.class);
        Document partial = existing.get("partialFilterExpression", Document.class);

        Long declaredTtl = spec.expireAfter() == null ? null : spec.expireAfter().toSeconds();
        Long actualTtl = ttl == null ? null : ttl.longValue();

        return unique == spec.unique()
                && sparse == spec.sparse()
                && java.util.Objects.equals(declaredTtl, actualTtl)
                && java.util.Objects.equals(spec.partialFilter(), partial)
                && (!spec.text() || sameWeights(spec.weights(), existing.get("weights", Document.class)));
    }

    /** A text index ranks by its weights, so a changed weight is a different index. */
    private static boolean sameWeights(Document declared, Document stored) {
        if (stored == null || declared.size() != stored.size()) {
            return false;
        }
        return declared.entrySet().stream().allMatch(entry -> stored.get(entry.getKey()) instanceof Number weight
                && weight.intValue() == ((Number) entry.getValue()).intValue());
    }

    private static boolean occupiesTheSameKeys(IndexSpec spec, Document existing) {
        Object storedKey = existing.get("key");
        if (spec.text()) {
            return storedKey instanceof Document key && key.containsKey("_fts");
        }
        return spec.keyDocument().equals(storedKey);
    }

    private Mono<List<Document>> existingIndexes(String collection) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> database.getCollection(collection).listIndexes())
                .collectList()
                .onErrorResume(missing -> Mono.just(List.of()));
    }

    /**
     * Recreates indexes from the definitions {@code listIndexes} returned.
     *
     * <p>Rebuilt from the server's own description rather than from anything reconstructed, so
     * the restored index is the one that was there — including options this class does not model.
     * {@code createIndexes} takes the same shape {@code listIndexes} emits, minus {@code v} and
     * {@code key}, which is why this can round-trip without understanding the contents.</p>
     */
    private Mono<Void> restore(String collection, List<Document> definitions) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> Flux.fromIterable(definitions)
                        .concatMap(definition -> {
                            Document key = definition.get("key", Document.class);
                            IndexOptions options = new IndexOptions()
                                    .name(definition.getString("name"));
                            if (Boolean.TRUE.equals(definition.getBoolean("unique"))) {
                                options.unique(true);
                            }
                            if (Boolean.TRUE.equals(definition.getBoolean("sparse"))) {
                                options.sparse(true);
                            }
                            Number ttl = definition.get("expireAfterSeconds", Number.class);
                            if (ttl != null) {
                                options.expireAfter(ttl.longValue(), TimeUnit.SECONDS);
                            }
                            Document partial = definition.get("partialFilterExpression", Document.class);
                            if (partial != null) {
                                options.partialFilterExpression(partial);
                            }
                            Document weights = definition.get("weights", Document.class);
                            if (weights != null) {
                                options.weights(weights);
                            }
                            return Mono.from(database.getCollection(collection)
                                    .createIndex(key, options));
                        }))
                .then()
                .onErrorResume(unrecoverable -> {
                    // Reported, never swallowed: the collection is now missing an index it had,
                    // and that has to reach a human rather than the migration's return value.
                    log.error("Could not restore {} on {} after a failed repair — the collection "
                            + "is missing an index it had before this ran", definitions, collection,
                            unrecoverable);
                    return Mono.empty();
                });
    }

    private Mono<Void> dropAll(String collection, List<String> names) {
        return mongoTemplate.getMongoDatabase()
                .flatMapMany(database -> Flux.fromIterable(names)
                        .concatMap(name -> Mono.from(
                                database.getCollection(collection).dropIndex(name))))
                .then();
    }

    private Mono<String> create(IndexSpec spec) {
        IndexOptions options = new IndexOptions().name(spec.name());
        if (spec.unique()) {
            options.unique(true);
        }
        if (spec.sparse()) {
            options.sparse(true);
        }
        if (spec.expireAfter() != null) {
            options.expireAfter(spec.expireAfter().toSeconds(), TimeUnit.SECONDS);
        }
        if (spec.partialFilter() != null) {
            options.partialFilterExpression(spec.partialFilter());
        }
        if (spec.text()) {
            options.weights(spec.weights());
        }
        return mongoTemplate.getMongoDatabase()
                .flatMap(database -> Mono.from(
                        database.getCollection(spec.collection())
                                .createIndex(spec.keyDocument(), options)));
    }
}
