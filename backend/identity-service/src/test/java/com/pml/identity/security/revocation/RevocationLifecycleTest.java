package com.pml.identity.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.security.revocation.CachedRevocationCheck;
import com.pml.shared.security.revocation.DurableRevocationStore;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationKeys;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * The revocation store against a throwaway MongoDB replica set and Redis: how long each key lives,
 * what a written reason must look like, that a flushed cache is rebuilt before the service is
 * ready, that the expiry index really removes rows, and that a revocation written by identity is
 * seen by another service's check within the bound.
 */
@Tag("L2")
@Tag("ET-IDN-003")
@DisplayName("Revocations are stored, cached, rebuilt and expired the way their window requires")
class RevocationLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-10-11T08:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static RevocationRepository repository;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static TestClock clock;
    private static RevocationProperties properties;
    private static RevocationCacheTrust trust;
    private static MongoRevocationStore store;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_revocation_lifecycle"));
        repository = new ReactiveMongoRepositoryFactory(mongo).getRepository(RevocationRepository.class);
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        clock = TestClock.frozenAt(T0);
        properties = new RevocationProperties();
        properties.setAccessTokenLifespan(Duration.ofMinutes(5));
        properties.setClockSkew(Duration.ofSeconds(60));
        trust = new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl());
        store = new MongoRevocationStore(repository, redis, trust, properties,
                new RevocationMetrics(new SimpleMeterRegistry(), clock), clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void clean() {
        mongo.remove(new Query(), RevocationRecord.class).block();
        redis.delete(redis.keys("pml:*")).block();
        clock.setTo(T0);
    }

    private Duration redisTtl(RevocationType type, String value) {
        Duration ttl = redis.getExpire(type.cacheKey(value)).block();
        assertThat(ttl).as("the key exists with a TTL").isNotNull();
        return ttl;
    }

    // ---- how long a key lives ---------------------------------------------------------------------------

    @Test
    @DisplayName("a token revocation is kept until that token's own expiry plus skew, in MongoDB and in Redis")
    void tokenTtlFollowsTheTokenExpiry() {
        RevocationRecord record = store.revoke(RevocationType.TOKEN, "jti-1", RevocationTrigger.LOGOUT,
                "logout", "user-1", T0.plusSeconds(30)).block();

        assertThat(record.getExpiresAt()).isEqualTo(T0.plusSeconds(90));
        assertThat(redisTtl(RevocationType.TOKEN, "jti-1"))
                .isLessThanOrEqualTo(Duration.ofSeconds(90)).isGreaterThan(Duration.ofSeconds(80));
    }

    @Test
    @DisplayName("revoking a token that already expired holds its key for the skew only")
    void expiredTokenHoldsTheSkewOnly() {
        RevocationRecord record = store.revoke(RevocationType.TOKEN, "jti-old", RevocationTrigger.LOGOUT,
                "logout", "user-1", T0.minusSeconds(900)).block();

        assertThat(record.getExpiresAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(redisTtl(RevocationType.TOKEN, "jti-old")).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("a session or user revocation is kept for a full lifespan plus skew, and Redis never outlives MongoDB")
    void sessionAndUserKeepTheFullWindow() {
        RevocationRecord session = store.revoke(RevocationType.SESSION, "sid-1", RevocationTrigger.LOGOUT,
                "logout", "user-1", null).block();
        RevocationRecord user = store.revoke(RevocationType.USER, "sub-1", RevocationTrigger.ACCOUNT_SUSPENDED,
                "account-suspended", "admin-1", null).block();

        assertThat(session.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(6)));
        assertThat(user.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(6)));
        assertThat(redisTtl(RevocationType.SESSION, "sid-1")).isLessThanOrEqualTo(Duration.ofMinutes(6));
        assertThat(redisTtl(RevocationType.USER, "sub-1")).isLessThanOrEqualTo(Duration.ofMinutes(6));
    }

    // ---- reasons and triggers ---------------------------------------------------------------------------

    @Test
    @DisplayName("a manual revocation needs a written reason of at least twenty characters")
    void manualRevocationNeedsAWrittenReason() {
        assertThatThrownBy(() -> store.revoke(RevocationType.USER, "sub-9", RevocationTrigger.ADMIN_REVOKE,
                "too short", "admin-1", null).block()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.revoke(RevocationType.USER, "sub-9", RevocationTrigger.ADMIN_REVOKE,
                "          padded          ", "admin-1", null).block())
                .as("padding does not count towards the length").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.revoke(RevocationType.USER, "sub-9",
                RevocationTrigger.SUSPECTED_COMPROMISE, null, "admin-1", null).block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count().block()).as("a refused revocation persists nothing").isZero();
        assertThat(redis.hasKey(RevocationKeys.user("sub-9")).block()).isFalse();

        RevocationRecord accepted = store.revoke(RevocationType.USER, "sub-9", RevocationTrigger.ADMIN_REVOKE,
                "stolen laptop reported by the organizer", "admin-1", null).block();
        assertThat(accepted.getReason()).isEqualTo("stolen laptop reported by the organizer");
        assertThat(accepted.getTrigger()).isEqualTo(RevocationTrigger.ADMIN_REVOKE);
        assertThat(accepted.getRevokedBy()).isEqualTo("admin-1");
        assertThat(accepted.getRevokedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("a machine trigger is stored with its explanation, so no reason is shorter than a sentence")
    void machineReasonIsExplained() {
        RevocationRecord record = store.revoke(RevocationType.SESSION, "sid-m", RevocationTrigger.LOGOUT,
                "user_logout", "user-1", null).block();

        assertThat(record.getReason()).contains("user_logout").hasSizeGreaterThanOrEqualTo(20);
        assertThat(record.getTrigger()).isEqualTo(RevocationTrigger.LOGOUT);

        RevocationRecord inferred = store.revoke(RevocationType.USER, "sub-i", "account-deleted", "system").block();
        assertThat(inferred.getTrigger()).isEqualTo(RevocationTrigger.ACCOUNT_ERASED);
    }

    @Test
    @DisplayName("an oversized reason is cut to the limit rather than stored whole")
    void reasonIsBounded() {
        RevocationRecord record = store.revoke(RevocationType.USER, "sub-big", RevocationTrigger.ADMIN_REVOKE,
                "x".repeat(5_000), "admin-1", null).block();

        assertThat(record.getReason()).hasSize(500);
    }

    // ---- injection and what is stored -------------------------------------------------------------------

    @Test
    @DisplayName("identifiers are data: operator-shaped and wildcard values match only themselves")
    void identifiersAreNeverInterpreted() {
        store.revoke(RevocationType.TOKEN, "{\"$ne\":null}", RevocationTrigger.LOGOUT, "logout", "u", null).block();
        store.revoke(RevocationType.TOKEN, ".*", RevocationTrigger.LOGOUT, "logout", "u", null).block();

        assertThat(store.check(List.of(new RevocationIdentifier(RevocationType.TOKEN, "jti-innocent"))).block())
                .isEqualTo(RevocationDecision.ACTIVE);
        assertThat(store.check(List.of(new RevocationIdentifier(RevocationType.TOKEN, "x' || '1'=='1"))).block())
                .isEqualTo(RevocationDecision.ACTIVE);
        assertThat(store.check(List.of(new RevocationIdentifier(RevocationType.TOKEN, ".*"))).block())
                .isEqualTo(RevocationDecision.REVOKED);
        assertThat(repository.count().block()).isEqualTo(2);
    }

    @Test
    @DisplayName("a revocation is the MongoDB record and the Redis key and nothing else: no event, no outbox row")
    void nothingIsWrittenToAnEventStore() {
        store.revoke(RevocationType.USER, "sub-e", RevocationTrigger.ACCOUNT_SUSPENDED, "account-suspended",
                "admin-1", null).block();
        store.revoke(RevocationType.SESSION, "sid-e", RevocationTrigger.LOGOUT, "logout", "u", null).block();

        List<String> collections = mongo.getCollectionNames().collectList().block();
        assertThat(collections).containsExactly("identity_token_revocations");
        assertThat(redis.hasKey(RevocationKeys.user("sub-e")).block()).isTrue();
        assertThat(redis.hasKey(RevocationKeys.session("sid-e")).block()).isTrue();
    }

    // ---- the cache is rebuilt before the service is ready -------------------------------------------------

    @Test
    @DisplayName("startup rebuilds a flushed cache from MongoDB, with each key's remaining life, and marks it complete")
    void startupRebuildsTheCache() {
        store.revoke(RevocationType.TOKEN, "jti-w", RevocationTrigger.LOGOUT, "logout", "u", T0.plusSeconds(60)).block();
        store.revoke(RevocationType.SESSION, "sid-w", RevocationTrigger.LOGOUT, "logout", "u", null).block();
        store.revoke(RevocationType.USER, "sub-w", RevocationTrigger.ACCOUNT_SUSPENDED, "account-suspended", "a", null).block();
        clock.advance(Duration.ofMinutes(1));
        redis.delete(redis.keys("pml:*")).block();
        assertThat(trust.isComplete().block()).as("a flushed cache is not trusted").isFalse();

        RevocationCacheWarmer warmer = new RevocationCacheWarmer(repository, redis, trust, properties, clock);
        try {
            warmer.run(null);

            assertThat(trust.isComplete().block()).isTrue();
            assertThat(redisTtl(RevocationType.TOKEN, "jti-w")).isLessThanOrEqualTo(Duration.ofSeconds(60));
            assertThat(redisTtl(RevocationType.SESSION, "sid-w")).isLessThanOrEqualTo(Duration.ofMinutes(5));
            assertThat(redisTtl(RevocationType.USER, "sub-w")).isLessThanOrEqualTo(Duration.ofMinutes(5));
        } finally {
            warmer.stopWarming();
        }
    }

    @Test
    @DisplayName("a record past its window is not loaded back into the cache")
    void expiredRecordsAreNotReloaded() {
        store.revoke(RevocationType.USER, "sub-gone", RevocationTrigger.ACCOUNT_SUSPENDED, "account-suspended",
                "a", null).block();
        clock.advance(Duration.ofMinutes(7));
        redis.delete(redis.keys("pml:*")).block();

        RevocationCacheWarmer warmer = new RevocationCacheWarmer(repository, redis, trust, properties, clock);
        try {
            warmer.run(null);
            assertThat(redis.hasKey(RevocationKeys.user("sub-gone")).block()).isFalse();
        } finally {
            warmer.stopWarming();
        }
    }

    @Test
    @DisplayName("with the cache cold, a check is answered from MongoDB and still refuses the revoked token")
    void coldCacheIsAnsweredFromMongo() {
        store.revoke(RevocationType.SESSION, "sid-c", RevocationTrigger.LOGOUT, "logout", "u", null).block();
        redis.delete(redis.keys("pml:*")).block();

        CachedRevocationCheck check = checkAsAnotherService(store);

        assertThat(check.check("jti-x", "sid-c", "sub-x").block()).isEqualTo(RevocationDecision.REVOKED);
        assertThat(check.check("jti-x", "sid-other", "sub-x").block()).isEqualTo(RevocationDecision.ACTIVE);
    }

    // ---- the expiry index removes rows -------------------------------------------------------------------

    @Test
    @DisplayName("the expiry index removes a row once its window has passed")
    void expiryIndexRemovesTheRow() {
        mongo.indexOps(RevocationRecord.class)
                .ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO)).block();
        Mono.from(client.getDatabase("admin")
                .runCommand(new Document("setParameter", 1).append("ttlMonitorSleepSecs", 1))).block();

        Instant soon = Instant.now().plusSeconds(1);
        repository.insert(RevocationRecord.builder().id("TOKEN:ttl-1").type(RevocationType.TOKEN).value("ttl-1")
                .reason("expires almost at once, for the index test").revokedBy("test")
                .trigger(RevocationTrigger.LOGOUT).revokedAt(Instant.now()).expiresAt(soon).build()).block();
        assertThat(repository.existsById("TOKEN:ttl-1").block()).isTrue();

        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        boolean gone = false;
        while (System.nanoTime() < deadline && !gone) {
            gone = Boolean.FALSE.equals(repository.existsById("TOKEN:ttl-1").block());
            if (!gone) {
                sleep(500);
            }
        }
        assertThat(gone).as("the row was removed by the expiry index").isTrue();
    }

    @Test
    @DisplayName("a lost key is put back by the periodic reload without anyone asking")
    void periodicReloadRepairsALostKey() throws Exception {
        RevocationProperties quick = new RevocationProperties();
        quick.setCacheWarmInterval(Duration.ofSeconds(1));
        store.revoke(RevocationType.SESSION, "sid-heal", RevocationTrigger.LOGOUT, "logout", "u", null).block();
        RevocationCacheWarmer warmer = new RevocationCacheWarmer(repository, redis, trust, quick, clock);
        try {
            warmer.run(null);
            redis.delete(RevocationKeys.session("sid-heal")).block();

            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            boolean back = false;
            while (System.nanoTime() < deadline && !back) {
                back = Boolean.TRUE.equals(redis.hasKey(RevocationKeys.session("sid-heal")).block());
                if (!back) {
                    sleep(200);
                }
            }
            assertThat(back).as("the key was restored from MongoDB by the scheduled reload").isTrue();
        } finally {
            warmer.stopWarming();
        }
    }

    @Test
    @DisplayName("the cached value is a constant marker: never the token, the reason or who revoked")
    void cacheCarriesNoSecrets() {
        store.revoke(RevocationType.USER, "sub-marker", RevocationTrigger.ADMIN_REVOKE,
                "a reason that must never reach the cache", "admin-private-id", null).block();

        assertThat(redis.opsForValue().get(RevocationKeys.user("sub-marker")).block()).isEqualTo("revoked");
    }

    @Test
    @DisplayName("a token whose issuer's clock ran thirty seconds fast is still covered by its revocation")
    void skewIsCovered() {
        RevocationRecord record = store.revoke(RevocationType.USER, "sub-skew", RevocationTrigger.ACCOUNT_SUSPENDED,
                "account-suspended", "admin-1", null).block();

        Instant latestExpiryOfAFastClockToken = T0.plus(properties.getAccessTokenLifespan()).plusSeconds(30);
        assertThat(record.getExpiresAt()).isAfterOrEqualTo(latestExpiryOfAFastClockToken);
    }

    @Test
    @DisplayName("a warm check costs under five milliseconds at p99")
    void warmCheckIsFast() {
        store.revoke(RevocationType.USER, "sub-fast", RevocationTrigger.ACCOUNT_SUSPENDED, "account-suspended", "a",
                null).block();
        trust.markComplete().block();
        CachedRevocationCheck check = checkAsAnotherService(unreachableDurable());
        for (int i = 0; i < 500; i++) {
            check.check("jti-" + i, "sid-" + i, "sub-" + i).block();
        }

        List<Long> micros = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            long started = System.nanoTime();
            check.check("jti-w" + i, "sid-w" + i, "sub-w" + i).block();
            micros.add((System.nanoTime() - started) / 1_000);
        }
        micros.sort(Long::compare);
        long p99 = micros.get((int) Math.ceil(micros.size() * 0.99) - 1);

        assertThat(p99).as("p99 microseconds for one warm check (three identifiers, one round trip)")
                .isLessThan(5_000);
    }

    // ---- reaching another service ------------------------------------------------------------------------

    @Test
    @DisplayName("a revocation written by identity is refused by another service's check within five seconds, at p99")
    void propagationIsBounded() {
        // The other service shares Redis and cannot reach MongoDB, so only the shared cache can answer.
        CachedRevocationCheck other = checkAsAnotherService(unreachableDurable());
        trust.markComplete().block();

        List<Long> elapsedMillis = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String sid = "sid-p-" + UUID.randomUUID();
            assertThat(other.check(null, sid, null).block()).isEqualTo(RevocationDecision.ACTIVE);

            long started = System.nanoTime();
            store.revoke(RevocationType.SESSION, sid, RevocationTrigger.LOGOUT, "logout", "u", null).block();
            RevocationDecision seen = RevocationDecision.ACTIVE;
            while (seen != RevocationDecision.REVOKED && System.nanoTime() - started < Duration.ofSeconds(10).toNanos()) {
                seen = other.check(null, sid, null).block();
            }
            assertThat(seen).isEqualTo(RevocationDecision.REVOKED);
            elapsedMillis.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
        }

        elapsedMillis.sort(Long::compare);
        long p99 = elapsedMillis.get((int) Math.ceil(elapsedMillis.size() * 0.99) - 1);
        assertThat(p99).as("p99 of write-to-refusal, milliseconds").isLessThan(5_000);
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private CachedRevocationCheck checkAsAnotherService(DurableRevocationStore durable) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        factory.afterPropertiesSet();
        ReactiveStringRedisTemplate otherRedis = new ReactiveStringRedisTemplate(factory);
        RevocationProperties other = new RevocationProperties();
        return new CachedRevocationCheck(otherRedis,
                new RevocationCacheTrust(otherRedis, other.getCacheCompletenessTtl()), durable, other,
                new RevocationMetrics(new SimpleMeterRegistry(), clock),
                CircuitBreaker.ofDefaults("other-" + UUID.randomUUID()), TimeLimiter.ofDefaults());
    }

    private static DurableRevocationStore unreachableDurable() {
        return new DurableRevocationStore() {
            @Override
            public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
                return Mono.error(new IllegalStateException("MongoDB is not reachable from this service"));
            }

            @Override
            public Mono<Void> ping() {
                return Mono.error(new IllegalStateException("unreachable"));
            }

            @Override
            public String name() {
                return "unreachable";
            }
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
