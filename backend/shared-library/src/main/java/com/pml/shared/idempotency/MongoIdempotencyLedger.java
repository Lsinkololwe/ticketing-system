package com.pml.shared.idempotency;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Date;

/**
 * Keeps the ledger in a MongoDB collection whose {@code _id} is the scoped key.
 *
 * <p>Using the key as {@code _id} makes the unique constraint the primary key itself, so there is
 * no separate index that could be missing or built after traffic started. A TTL index on
 * {@code expiresAt} retires entries after the retention window; an entry past that window and not
 * yet swept is treated as absent so behaviour does not depend on when the sweeper last ran.</p>
 */
public class MongoIdempotencyLedger implements IdempotencyLedger {

    private final ReactiveMongoTemplate mongo;
    private final String collection;
    private final Clock clock;
    private final Duration retention;

    public MongoIdempotencyLedger(ReactiveMongoTemplate mongo, String collection, Clock clock, Duration retention) {
        this.mongo = mongo;
        this.collection = collection;
        this.clock = clock;
        this.retention = retention;
    }

    /** Creates the TTL index. Idempotent; call once at start-up. */
    public Mono<Void> ensureIndexes() {
        return mongo.indexOps(collection)
                .ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("idx_expiresAt_ttl"))
                .then();
    }

    @Override
    public Mono<Claim> claim(String scope, String key, String fingerprint) {
        Date now = Date.from(clock.instant());
        Document pending = new Document("_id", id(scope, key))
                .append("fingerprint", fingerprint)
                .append("completed", false)
                .append("createdAt", now)
                .append("expiresAt", Date.from(clock.instant().plus(retention)));
        return mongo.insert(pending, collection)
                .map(inserted -> new Claim(true, new Entry(fingerprint, false, null)))
                .onErrorResume(DuplicateKeyException.class, duplicate -> find(scope, key)
                        .map(existing -> new Claim(false, existing))
                        // The earlier entry expired between the insert attempt and the read.
                        .switchIfEmpty(Mono.defer(() -> claim(scope, key, fingerprint))));
    }

    @Override
    public Mono<Entry> find(String scope, String key) {
        Query live = Query.query(Criteria.where("_id").is(id(scope, key))
                .and("expiresAt").gt(Date.from(clock.instant())));
        return mongo.findOne(live, Document.class, collection)
                .map(found -> new Entry(found.getString("fingerprint"),
                        Boolean.TRUE.equals(found.getBoolean("completed")), found.getString("response")));
    }

    @Override
    public Mono<Void> complete(String scope, String key, String response) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(id(scope, key))),
                        new Update().set("completed", true).set("response", response), collection)
                .then();
    }

    @Override
    public Mono<Void> release(String scope, String key) {
        return mongo.remove(Query.query(Criteria.where("_id").is(id(scope, key)).and("completed").is(false)), collection)
                .then();
    }

    private static String id(String scope, String key) {
        return scope + "|" + key;
    }
}
