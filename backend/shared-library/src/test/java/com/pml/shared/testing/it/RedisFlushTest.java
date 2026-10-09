package com.pml.shared.testing.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.security.revocation.CachedRevocationCheck;
import com.pml.shared.security.revocation.DurableRevocationStore;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis holds nothing the platform cannot lose.
 *
 * <h2>Why a flush is the only test that settles this</h2>
 * Every key the platform writes carries a TTL, which is easy to verify by reading the code and
 * proves less than it appears: a value with a TTL is still authoritative if nothing else holds
 * it. The compose file runs Redis with {@code --appendonly yes}, so it survives restarts too —
 * which means a service that wrongly kept business state here would look correct indefinitely.
 * Only removing the data distinguishes a cache from a database.
 *
 * <h2>The case that matters is revocation</h2>
 * {@code identity_token_revocations} is the system of record and Redis is a read-through cache
 * over it. If a flush left a revoked token looking active, {@code FLUSHALL} would silently
 * un-revoke every token on the platform — a security failure with no error, no alert, and no
 * symptom until the wrong person is let in. So this asserts the decision, not the key.
 *
 * <h2>Its own container</h2>
 * {@link RedisNode}, never {@code dev_redis}: the development instance is shared with two other
 * projects, and a test that can destroy data outside its own fixture is one people switch off.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R7 · a FLUSHALL loses no business data")
class RedisFlushTest {

    private static final String BUSINESS_COLLECTION = "booking_tickets";
    private static final String REVOKED_JTI = "jti-revoked-probe";

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static CachedRevocationCheck revocationCheck;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(mongoClient, "flush_harness"));

        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);

        RevocationProperties properties = new RevocationProperties();
        revocationCheck = new CachedRevocationCheck(
                redis,
                new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl()),
                durableStoreBackedByMongo(),
                properties,
                new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC()),
                CircuitBreaker.ofDefaults("revocation-cache"),
                TimeLimiter.ofDefaults());
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
        redisFactory.destroy();
    }

    @Test
    @DisplayName("business documents survive a flush; every cached key does not")
    void flushLosesOnlyTheCache() {
        mongo.remove(new Query(), BUSINESS_COLLECTION).block();
        seedBusinessData();
        seedEverythingRedisLegitimatelyHolds();

        assertThat(keyCount()).as("the fixture must actually be in Redis").isGreaterThan(0);

        flushAll();

        assertThat(keyCount())
                .as("if the flush did not empty Redis, the assertions below prove nothing")
                .isZero();

        List<Document> tickets = mongo.find(new Query(), Document.class, BUSINESS_COLLECTION)
                .collectList().block();
        assertThat(tickets)
                .as("business data lives in MongoDB and a cache flush must not touch it")
                .hasSize(3);
        assertThat(tickets.stream().map(t -> t.getString("ticketReference")).toList())
                .containsExactlyInAnyOrder("TKT-1", "TKT-2", "TKT-3");
    }

    @Test
    @DisplayName("a revoked token is still revoked after the cache is emptied")
    void revocationSurvivesAFlush() {
        seedRevocation(REVOKED_JTI);

        // Warm the cache and confirm the revocation is visible through it first, so that the
        // assertion after the flush is about the fall-through and not about a check that was
        // never working.
        assertThat(revocationCheck.check(REVOKED_JTI, null, null).block())
                .isEqualTo(RevocationDecision.REVOKED);

        flushAll();

        assertThat(revocationCheck.check(REVOKED_JTI, null, null).block())
                .as("""
                    Redis is a read-through cache over identity_token_revocations, not the \
                    record itself. A flush that left this token ACTIVE would un-revoke every \
                    session on the platform at once, with no error and no symptom until \
                    somebody who was signed out is let back in.""")
                .isEqualTo(RevocationDecision.REVOKED);
    }

    @Test
    @DisplayName("a token that was never revoked is not reported as revoked after a flush")
    void flushDoesNotDenyEveryone() {
        // The opposite failure, and the one a fail-closed cache produces: after a flush the
        // check has no cached answer, and treating "I don't know" as REVOKED would sign out
        // every user on the platform until the warmer ran.
        flushAll();

        assertThat(revocationCheck.check("jti-never-revoked", null, null).block())
                .isNotEqualTo(RevocationDecision.REVOKED);
    }

    // --------------------------------------------------------------------- fixture

    private static void seedBusinessData() {
        Flux.just("TKT-1", "TKT-2", "TKT-3")
                .concatMap(reference -> mongo.insert(
                        new Document("ticketReference", reference).append("status", "ISSUED"),
                        BUSINESS_COLLECTION))
                .then().block();
    }

    /** Every key shape the Redis registry names — all ephemeral, all with a TTL. */
    private static void seedEverythingRedisLegitimatelyHolds() {
        Mono.when(
                redis.opsForValue().set("otp:+260970000001", "123456", Duration.ofMinutes(5)),
                redis.opsForValue().set("otp:cooldown:+260970000001", "1", Duration.ofMinutes(1)),
                redis.opsForValue().set("idem:checkout-abc", "in-flight", Duration.ofHours(24)),
                redis.opsForValue().set("evt:seen:booking:evt-1", "1", Duration.ofDays(7)),
                redis.opsForValue().set("lock:sweep:reservations", "held", Duration.ofSeconds(30)),
                redis.opsForValue().set("ratelimit:ip:127.0.0.1", "7", Duration.ofMinutes(1)),
                redis.opsForValue().set("cache:event:event-1", "{}", Duration.ofMinutes(5)),
                redis.opsForValue().set("cache:tier:tier-1", "42", Duration.ofSeconds(30))
        ).block();
    }

    private static void seedRevocation(String jti) {
        // The _id is derived from (type, value), not the raw jti — RevocationType.documentId.
        // That is what makes revoking the same token twice an idempotent upsert and the durable
        // lookup a primary-key read, so the fixture has to use the same derivation.
        String id = com.pml.shared.security.revocation.RevocationType.TOKEN.documentId(jti);
        mongo.remove(Query.query(Criteria.where("_id").is(id)), "identity_token_revocations").block();
        mongo.insert(new Document("_id", id).append("type", "TOKEN").append("value", jti),
                "identity_token_revocations").block();
    }

    private static void flushAll() {
        org.springframework.data.redis.connection.ReactiveRedisConnection connection =
                redisFactory.getReactiveConnection();
        try {
            connection.serverCommands().flushAll().block();
        } finally {
            connection.close();
        }
    }

    private static Long keyCount() {
        return redis.keys("*").count().block();
    }

    /**
     * The durable half of the read-through pair, reading the collection that is the system of
     * record. Deliberately not a stub returning a constant: the point of the test is that the
     * answer comes from MongoDB once Redis cannot supply it.
     */
    private static DurableRevocationStore durableStoreBackedByMongo() {
        return new DurableRevocationStore() {
            @Override
            public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
                return Flux.fromIterable(identifiers)
                        .concatMap(identifier -> mongo.exists(
                                Query.query(Criteria.where("_id").is(identifier.storageId())),
                                "identity_token_revocations"))
                        .any(Boolean::booleanValue)
                        .map(found -> found ? RevocationDecision.REVOKED : RevocationDecision.ACTIVE);
            }

            @Override
            public String name() {
                return "mongo";
            }

            @Override
            public Mono<Void> ping() {
                return mongo.exists(new Query(), "identity_token_revocations").then();
            }
        };
    }
}
