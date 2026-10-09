package com.pml.shared.migration;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Date;

/**
 * Which migrations have run in one service, and how they went.
 *
 * <h2>Why a ledger and not just idempotent migrations</h2>
 * Every migration in this platform is already safe to re-run, so the ledger is
 * not what makes repetition harmless. It is what makes the history
 * <em>legible</em>. Without it nobody can answer the question that actually gets
 * asked during an incident — "did the reservation migration run on this
 * environment, and what did it move?" — because an idempotent migration's second
 * run looks identical to a first run against already-migrated data: zero rows,
 * clean result.
 *
 * <p>It also stops a slow migration from being re-attempted on every restart of
 * a crash-looping pod, which turns a data conversion into a load problem.
 *
 * <h2>The id is the migration name</h2>
 * Not a generated id. Two runners starting at once — two replicas booting
 * together — both try to insert the same {@code _id} and exactly one wins. The
 * loser's duplicate-key is how it learns another instance is already running the
 * migration, which is cheaper and more reliable than a separate lock.
 *
 * <h2>Raw documents, deliberately</h2>
 * This reads and writes {@link Document} rather than a mapped entity. A mapped
 * entity would carry Spring Data's {@code _class} type hint, and the rows
 * already in {@code booking_migrations} carry the hint of a class in
 * booking-service — so promoting the entity to shared-library would make nine
 * successfully-applied migrations fail to deserialise and silently re-run. A
 * ledger is an audit table; it does not need object mapping, and not having it
 * is what lets one implementation serve every service.
 */
@Slf4j
public class MigrationLedger {

    /** Field names, fixed by the rows already in production. */
    private static final String ID = "_id";
    private static final String STATUS = "status";
    private static final String STARTED_AT = "startedAt";
    private static final String FINISHED_AT = "finishedAt";
    private static final String SUMMARY = "summary";
    private static final String FAILURE = "failure";

    private final ReactiveMongoTemplate mongoTemplate;
    private final String collection;

    /**
     * The platform clock. The ledger's STARTED_AT/FINISHED_AT are audit facts, so they must be
     * movable by a test: a migration's timing is exactly the sort of thing a frozen-clock
     * test asserts, and Instant.now() would make that impossible at this call site.
     */
    private final Clock clock;

    public MigrationLedger(ReactiveMongoTemplate mongoTemplate, String collection, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.collection = collection;
        this.clock = clock;
    }

    public enum Status {
        /** Claimed by a runner and still going, or abandoned by a crash. */
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    /** @param name the migration's stable name — renaming one re-runs it, so don't */
    public record Entry(String name, Status status, String summary, String failure) {}

    public Mono<Entry> find(String name) {
        return mongoTemplate.getCollection(collection)
                .flatMap(c -> Mono.from(c.find(new Document(ID, name)).first()))
                .map(MigrationLedger::toEntry);
    }

    public Flux<Entry> findAll() {
        return mongoTemplate.getCollection(collection)
                .flatMapMany(c -> Flux.from(c.find()))
                .map(MigrationLedger::toEntry);
    }

    /**
     * Insert a RUNNING row, or fail because somebody else already did.
     *
     * @return true when this caller won the claim
     */
    public Mono<Boolean> claim(String name) {
        Document claim = new Document(ID, name)
                .append(STATUS, Status.RUNNING.name())
                .append(STARTED_AT, Date.from(clock.instant()));

        return mongoTemplate.getCollection(collection)
                .flatMap(c -> Mono.from(c.insertOne(claim)))
                .thenReturn(true)
                // The index on _id decides, not a prior read: two replicas
                // booting together would both pass a read-then-write.
                .onErrorResume(com.mongodb.MongoWriteException.class, e -> {
                    log.info("Migration '{}' was claimed by another instance — skipping", name);
                    return Mono.just(false);
                })
                .onErrorResume(org.springframework.dao.DuplicateKeyException.class, e -> {
                    log.info("Migration '{}' was claimed by another instance — skipping", name);
                    return Mono.just(false);
                });
    }

    public Mono<Void> succeed(String name, String summary) {
        return set(name, new Document(STATUS, Status.SUCCEEDED.name())
                .append(FINISHED_AT, Date.from(clock.instant()))
                .append(SUMMARY, summary));
    }

    public Mono<Void> fail(String name, String failure) {
        return set(name, new Document(STATUS, Status.FAILED.name())
                .append(FINISHED_AT, Date.from(clock.instant()))
                .append(FAILURE, failure));
    }

    private Mono<Void> set(String name, Document fields) {
        return mongoTemplate.getCollection(collection)
                .flatMap(c -> Mono.from(c.updateOne(
                        new Document(ID, name), new Document("$set", fields))))
                .then();
    }

    private static Entry toEntry(Document doc) {
        String raw = doc.getString(STATUS);
        Status status;
        try {
            status = raw == null ? null : Status.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // A status this code does not recognise must not read as "not run".
            // Treated as FAILED so the runner refuses rather than re-applying.
            log.error("Migration ledger row '{}' carries unknown status '{}'", doc.get(ID), raw);
            status = Status.FAILED;
        }
        return new Entry(String.valueOf(doc.get(ID)), status,
                doc.getString(SUMMARY), doc.getString(FAILURE));
    }
}
