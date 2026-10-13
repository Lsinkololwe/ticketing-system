package com.pml.shared.security.tenancy;

import com.pml.shared.persistence.IndexSpec;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * The service's own durable copy of every platform-wide reach, kept until identity has confirmed it.
 *
 * <p>A reach is written here first, in a single insert into this service's own database, and only
 * then forwarded to identity's searchable trail. That order is the point: when identity is down the
 * administrator is not held up and the reach is not lost, because the row waits here in state
 * {@code PENDING} and a workflow keeps trying until identity answers. Each row has a random,
 * permanent {@code recordId} (the document's {@code _id}), which is what lets identity treat a
 * second delivery of the same reach as the one it already holds.
 *
 * <p>A row holds exactly the facts of {@link PlatformWideAuditRecord} plus the time it happened and
 * where it stands in delivery. It has no field for a request body, a filter value, a token or free
 * text, and the collection's validator refuses any other property. Rows are never deleted by the
 * application: a delivered row is the local evidence that identity confirmed it.
 */
public class PlatformWideAuditOutbox {

    public static final String PENDING = "PENDING";
    /** Identity confirmed it, or already held it. */
    public static final String DELIVERED = "DELIVERED";
    /** Identity refused the record as malformed or too old; retrying cannot change the answer. */
    public static final String REFUSED = "REFUSED";

    private final ReactiveMongoTemplate mongo;
    private final String collection;
    private final Clock clock;

    public PlatformWideAuditOutbox(ReactiveMongoTemplate mongo, String collection, Clock clock) {
        this.mongo = mongo;
        this.collection = collection;
        this.clock = clock;
    }

    public String collection() {
        return collection;
    }

    /** The indexes a service registers for its outbox collection: the backlog is always read by state and age. */
    public static List<IndexSpec> indexes(String collection) {
        return List.of(IndexSpec.on(collection, "idx_state_occurredAt").asc("state").asc("occurredAt").build());
    }

    /**
     * Writes the reach as {@code PENDING} and answers its new {@code recordId}. This is the only
     * write the administrator's request waits for, and it is local.
     */
    public Mono<String> append(PlatformWideAuditRecord record) {
        String recordId = UUID.randomUUID().toString();
        Document row = new Document("_id", recordId)
                .append("actorSub", record.actorSub())
                .append("role", record.role())
                .append("service", record.service())
                .append("reason", record.reason().name())
                .append("operation", record.operation())
                .append("occurredAt", Date.from(record.at()))
                .append("state", PENDING)
                .append("attempts", 0)
                .append("createdAt", Date.from(clock.instant()));
        return mongo.insert(row, collection).thenReturn(recordId);
    }

    /** One stored row, as forwarding needs it. */
    public record Entry(String recordId, PlatformWideAuditRecord record, String state, int attempts) {
    }

    public Mono<Entry> find(String recordId) {
        return mongo.findById(recordId, Document.class, collection).map(PlatformWideAuditOutbox::entry);
    }

    private static Entry entry(Document row) {
        PlatformWideAccess.Reason reason = PlatformWideAccess.Reason.valueOf(row.getString("reason"));
        PlatformWideAuditRecord record = new PlatformWideAuditRecord(row.getString("actorSub"), row.getString("role"),
                row.getString("service"), reason, row.getString("operation"), row.getDate("occurredAt").toInstant());
        Number attempts = row.get("attempts", Number.class);
        return new Entry(row.getString("_id"), record, row.getString("state"), attempts == null ? 0 : attempts.intValue());
    }

    /** Identity confirmed the row. A row already past {@code PENDING} is left as it is. */
    public Mono<Void> markDelivered(String recordId) {
        return settle(recordId, DELIVERED, "deliveredAt");
    }

    /** Identity refused the row for good. The row stays, so the refusal is visible and countable. */
    public Mono<Void> markRefused(String recordId) {
        return settle(recordId, REFUSED, "refusedAt");
    }

    private Mono<Void> settle(String recordId, String state, String timeField) {
        return mongo.updateFirst(pending(recordId),
                        new Update().set("state", state).set(timeField, Date.from(clock.instant())), collection)
                .then();
    }

    /** One failed attempt, noted so an operator can see a row that keeps failing. */
    public Mono<Void> noteAttempt(String recordId) {
        return mongo.updateFirst(pending(recordId),
                        new Update().inc("attempts", 1).set("lastAttemptAt", Date.from(clock.instant())), collection)
                .then();
    }

    private static Query pending(String recordId) {
        return Query.query(Criteria.where("_id").is(recordId).and("state").is(PENDING));
    }

    /** Ids of rows still waiting, oldest first, for the one-time re-drive at start-up. */
    public Flux<String> pendingIds(int limit) {
        Query query = Query.query(Criteria.where("state").is(PENDING))
                .with(Sort.by(Sort.Direction.ASC, "occurredAt")).limit(limit);
        query.fields().include("_id");
        return mongo.find(query, Document.class, collection).map(row -> row.getString("_id"));
    }

    /** How many rows wait and when the oldest of them happened; {@code oldest} is null when none wait. */
    public record Backlog(long pending, Instant oldest) {
        public static final Backlog EMPTY = new Backlog(0, null);
    }

    public Mono<Backlog> backlog() {
        Query waiting = Query.query(Criteria.where("state").is(PENDING));
        Mono<Long> count = mongo.count(waiting, collection);
        Mono<Instant> oldest = mongo.findOne(Query.query(Criteria.where("state").is(PENDING))
                        .with(Sort.by(Sort.Direction.ASC, "occurredAt")).limit(1), Document.class, collection)
                .map(row -> row.getDate("occurredAt").toInstant());
        return count.flatMap(total -> total == 0
                ? Mono.just(Backlog.EMPTY)
                : oldest.map(at -> new Backlog(total, at)).defaultIfEmpty(new Backlog(total, null)));
    }
}
