package com.pml.identity.revocation;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.revocation.support.ControllableTcpProxy;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.identity.security.revocation.RevocationRecord;
import com.pml.identity.security.revocation.RevocationRepository;
import com.pml.identity.security.revocation.RevocationCacheWarmer;
import com.pml.shared.security.revocation.CachedRevocationCheck;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the revocation stores against real Redis and MongoDB, with each connection routed
 * through a proxy so a test can take a store offline mid-run.
 *
 * <h2>What is being asserted</h2>
 * <p>The design claims that revocation survives the loss of either store and that a revocation
 * write either persists or reports failure. Neither claim is observable while both containers
 * are healthy, so most of these tests cut a dependency and assert what the system then does.</p>
 *
 * <h2>No mocks</h2>
 * <p>Every collaborator on the path is real: a real Lettuce connection, a real MongoDB driver, a
 * real circuit breaker and a real meter registry. A stub anywhere here could report a fallback
 * that does not exist.</p>
 */
@Tag("revocation")
@DisplayName("Revocation stores under partial and total outage")
class RevocationStoreIT {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8.0");

    private static GenericContainer<?> redisContainer;
    private static GenericContainer<?> mongoContainer;
    private static ControllableTcpProxy redisProxy;
    private static ControllableTcpProxy mongoProxy;

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongoTemplate;
    private static LettuceConnectionFactory redisConnectionFactory;
    private static ReactiveStringRedisTemplate redis;
    private static RevocationRepository repository;

    private MeterRegistry meterRegistry;
    private RevocationMetrics metrics;
    private RevocationProperties properties;
    private MongoRevocationStore durable;
    private RevocationCacheTrust cacheTrust;
    private RevocationCacheWarmer warmer;
    private CircuitBreaker circuitBreaker;
    private CachedRevocationCheck check;

    // =========================================================================
    // environment
    // =========================================================================

    @BeforeAll
    static void startEnvironment() {
        // Standalone rather than MongoDBContainer's replica set: a replica set advertises its
        // own host names, which topology discovery would then use directly and bypass the proxy.
        mongoContainer = new GenericContainer<>(MONGO_IMAGE)
                .withExposedPorts(27017)
                .waitingFor(Wait.forListeningPort());
        redisContainer = new GenericContainer<>(REDIS_IMAGE)
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());

        mongoContainer.start();
        redisContainer.start();

        mongoProxy = ControllableTcpProxy.forwardingTo(
                mongoContainer.getHost(), mongoContainer.getMappedPort(27017));
        redisProxy = ControllableTcpProxy.forwardingTo(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));

        // Short driver timeouts: the point of the outage tests is the fallback, not how long the
        // driver is willing to wait for a server that is not coming back.
        String mongoUri = "mongodb://%s:%d/revocation_it?directConnection=true"
                .formatted(mongoProxy.getHost(), mongoProxy.getPort())
                + "&serverSelectionTimeoutMS=1500&connectTimeoutMS=1000&socketTimeoutMS=2000";

        mongoClient = MongoClients.create(mongoUri);
        mongoTemplate = new ReactiveMongoTemplate(mongoClient, "revocation_it");
        repository = new ReactiveMongoRepositoryFactory(mongoTemplate)
                .getRepository(RevocationRepository.class);

        RedisStandaloneConfiguration redisConfig =
                new RedisStandaloneConfiguration(redisProxy.getHost(), redisProxy.getPort());
        redisConnectionFactory = new LettuceConnectionFactory(redisConfig);
        // Each command takes its own connection. With a shared native connection, a socket
        // killed by the proxy would be handed to the next test before Lettuce reconnected.
        redisConnectionFactory.setShareNativeConnection(false);
        redisConnectionFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisConnectionFactory);
    }

    @AfterAll
    static void stopEnvironment() {
        if (redisConnectionFactory != null) redisConnectionFactory.destroy();
        if (mongoClient != null) mongoClient.close();
        if (redisProxy != null) redisProxy.close();
        if (mongoProxy != null) mongoProxy.close();
        if (redisContainer != null) redisContainer.stop();
        if (mongoContainer != null) mongoContainer.stop();
    }

    @BeforeEach
    void wireFreshCollaborators() {
        redisProxy.resume();
        mongoProxy.resume();

        meterRegistry = new SimpleMeterRegistry();
        metrics = new RevocationMetrics(meterRegistry);

        properties = new RevocationProperties();
        properties.setAccessTokenLifespan(Duration.ofHours(1));
        properties.setCacheTimeout(Duration.ofMillis(400));
        properties.setDurableTimeout(Duration.ofSeconds(3));

        Clock clock = Clock.systemUTC();
        cacheTrust = new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl());
        durable = new MongoRevocationStore(repository, redis, cacheTrust, properties, metrics, clock);
        warmer = new RevocationCacheWarmer(repository, redis, cacheTrust, properties, clock);

        // A fresh breaker per test: an open circuit left over from an outage test would silently
        // change what the next test is measuring.
        circuitBreaker = CircuitBreaker.of("revocationCacheTest", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());

        TimeLimiter timeLimiter = TimeLimiter.of("revocationCacheTest", TimeLimiterConfig.custom()
                .timeoutDuration(properties.getCacheTimeout())
                .cancelRunningFuture(true)
                .build());

        check = new CachedRevocationCheck(redis, cacheTrust, durable, properties, metrics,
                circuitBreaker, timeLimiter, clock);

        awaitStoresReachable();
        retrying(() -> repository.deleteAll().block(Duration.ofSeconds(10)));
        retrying(() -> redis.getConnectionFactory().getReactiveConnection().serverCommands()
                .flushDb().block(Duration.ofSeconds(10)));

        // The flush leaves an empty but accurate cache, which is what the warmer would produce.
        // Marking it complete lets the fast path serve misses, as it does in a running service.
        warmer.warm().block(Duration.ofSeconds(10));
    }

    /**
     * Waits for both stores to answer again after a previous test cut them. The drivers
     * reconnect on their own, but not instantly, and a test that started against a
     * half-recovered store would be measuring the recovery rather than the behaviour.
     */
    private static void awaitStoresReachable() {
        retrying(() -> redis.hasKey("readiness-probe").block(Duration.ofSeconds(2)));
        retrying(() -> repository.count().block(Duration.ofSeconds(3)));
    }

    private static void retrying(Runnable action) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 25; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Store did not become reachable again", last);
    }

    // =========================================================================
    // happy path
    // =========================================================================

    @Test
    @DisplayName("a revocation lands in MongoDB and is mirrored into the cache")
    void revocationIsPersistedAndCached() {
        String jti = newId();

        RevocationRecord saved = durable
                .revoke(RevocationType.TOKEN, jti, "user_logout", "tester")
                .block(Duration.ofSeconds(10));

        assertThat(saved).isNotNull();
        assertThat(saved.getExpiresAt())
                .as("a record must outlive the tokens it revokes")
                .isAfter(Instant.now().plus(Duration.ofMinutes(59)));

        assertThat(repository.findById(RevocationType.TOKEN.documentId(jti))
                .block(Duration.ofSeconds(5)))
                .as("MongoDB holds the system of record")
                .isNotNull();

        assertThat(redis.hasKey(RevocationType.TOKEN.cacheKey(jti)).block(Duration.ofSeconds(5)))
                .as("the cache is primed on write")
                .isTrue();
    }

    @Test
    @DisplayName("the cache serves the check while it is healthy")
    void cacheServesTheCheck() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "user_logout", "tester")
                .block(Duration.ofSeconds(10));

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(5)))
                .isEqualTo(RevocationDecision.REVOKED);

        assertThat(counter("identity.revocation.checks", "source", "redis"))
                .as("the fast path answered")
                .isPositive();
        assertThat(counter("identity.revocation.degraded", null, null))
                .as("no fallback was needed")
                .isZero();
    }

    @Test
    @DisplayName("an unrevoked token resolves to ACTIVE")
    void unrevokedTokenIsActive() {
        assertThat(check.check(newId(), newId(), newId()).block(Duration.ofSeconds(5)))
                .isEqualTo(RevocationDecision.ACTIVE);
    }

    // =========================================================================
    // partial outage — the property the design exists for
    // =========================================================================

    @Test
    @DisplayName("with the cache cut, the durable store still reports the token as revoked")
    void durableStoreAnswersWhenCacheIsUnreachable() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "admin_revoke", "tester")
                .block(Duration.ofSeconds(10));

        redisProxy.cut();

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .as("losing Redis must not turn a revoked token into an admitted one")
                .isEqualTo(RevocationDecision.REVOKED);

        assertThat(counter("identity.revocation.degraded", null, null))
                .as("the fallback was recorded, not silently taken")
                .isPositive();
        assertThat(counter("identity.revocation.checks", "source", "mongodb")).isPositive();
        assertThat(counter("identity.revocation.unavailable", null, null))
                .as("one store was still answering")
                .isZero();
    }

    @Test
    @DisplayName("a revocation issued while the cache is down survives the outage")
    void revocationIssuedDuringCacheOutageSurvives() {
        String jti = newId();
        redisProxy.cut();

        RevocationRecord saved = durable
                .revoke(RevocationType.TOKEN, jti, "user_logout", "tester")
                .block(Duration.ofSeconds(10));

        assertThat(saved)
                .as("the durable write must succeed even with no cache to prime")
                .isNotNull();
        assertThat(counter("identity.revocation.writes", "outcome", "cache_failed"))
                .as("the un-cached write is visible in metrics")
                .isPositive();

        redisProxy.resume();
        awaitStoresReachable();

        assertThat(cacheTrust.isComplete().block(Duration.ofSeconds(5)))
                .as("a revocation that missed the cache must withdraw the completeness guarantee, "
                        + "otherwise the cache's own miss would mask the durable record")
                .isFalse();

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .as("a revocation recorded during an outage is still in force afterwards")
                .isEqualTo(RevocationDecision.REVOKED);

        warmer.warm().block(Duration.ofSeconds(10));

        assertThat(cacheTrust.isComplete().block(Duration.ofSeconds(5)))
                .as("reloading the cache restores the fast path")
                .isTrue();
        assertThat(redis.hasKey(RevocationType.TOKEN.cacheKey(jti)).block(Duration.ofSeconds(5)))
                .as("the missed revocation is in the cache after the reload")
                .isTrue();
        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .isEqualTo(RevocationDecision.REVOKED);
    }

    @Test
    @DisplayName("an incomplete cache does not let a miss masquerade as an answer")
    void missAgainstAnIncompleteCacheIsResolvedDurably() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "admin_revoke", "tester")
                .block(Duration.ofSeconds(10));

        // Models an eviction, a flush or a restart: the key is gone while Redis stays healthy.
        redis.delete(RevocationType.TOKEN.cacheKey(jti)).block(Duration.ofSeconds(5));
        cacheTrust.invalidate().block(Duration.ofSeconds(5));

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .as("a healthy cache that has lost a key must not report the token as active")
                .isEqualTo(RevocationDecision.REVOKED);
        assertThat(counter("identity.revocation.degraded", "reason", "cache_incomplete"))
                .isPositive();
    }

    @Test
    @DisplayName("a revocation that cannot be persisted reports failure instead of succeeding")
    void writeFailsLoudlyWhenDurableStoreIsUnreachable() {
        mongoProxy.cut();

        assertThatThrownBy(() -> durable
                .revoke(RevocationType.TOKEN, newId(), "user_logout", "tester")
                .block(Duration.ofSeconds(10)))
                .as("a caller must never be told a token was revoked when it was not")
                .isInstanceOf(Exception.class);

        assertThat(counter("identity.revocation.writes", "outcome", "durable_failed"))
                .isPositive();
    }

    @Test
    @DisplayName("the breaker opens on a dead cache and checks keep resolving")
    void breakerOpensAndChecksStillResolve() {
        String jti = newId();
        durable.revoke(RevocationType.TOKEN, jti, "admin_revoke", "tester")
                .block(Duration.ofSeconds(10));

        redisProxy.cut();

        for (int i = 0; i < 6; i++) {
            assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                    .isEqualTo(RevocationDecision.REVOKED);
        }

        assertThat(circuitBreaker.getState())
                .as("a dead cache should stop being called rather than timing out per request")
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .as("an open circuit must not change the answer, only where it comes from")
                .isEqualTo(RevocationDecision.REVOKED);
        assertThat(counter("identity.revocation.degraded", "reason", "circuit_open")).isPositive();
    }

    // =========================================================================
    // total outage
    // =========================================================================

    @Test
    @DisplayName("with both stores unreachable the decision is UNKNOWN, never ACTIVE")
    void bothStoresDownYieldsUnknown() {
        redisProxy.cut();
        mongoProxy.cut();

        assertThat(check.check(newId(), null, null).block(Duration.ofSeconds(15)))
                .as("an unverifiable token must not be reported as active")
                .isEqualTo(RevocationDecision.UNKNOWN);

        assertThat(counter("identity.revocation.unavailable", null, null))
                .as("the total outage is the signal to alert on")
                .isPositive();
    }

    @Test
    @DisplayName("a token carrying no jti, sid or sub is UNKNOWN")
    void tokenWithoutIdentifiersIsUnknown() {
        assertThat(check.check(null, null, null).block(Duration.ofSeconds(5)))
                .isEqualTo(RevocationDecision.UNKNOWN);
    }

    // =========================================================================
    // expiry and identifier coverage
    // =========================================================================

    @Test
    @DisplayName("an expired record no longer revokes, even before the TTL sweep removes it")
    void expiredRecordsDoNotRevoke() {
        String jti = newId();
        Instant past = Instant.now().minus(Duration.ofMinutes(5));

        repository.save(RevocationRecord.builder()
                        .id(RevocationType.TOKEN.documentId(jti))
                        .type(RevocationType.TOKEN)
                        .value(jti)
                        .reason("user_logout")
                        .revokedBy("tester")
                        .revokedAt(past.minus(Duration.ofHours(2)))
                        .expiresAt(past)
                        .build())
                .block(Duration.ofSeconds(10));

        redisProxy.cut(); // force the durable path, where the filtering happens

        assertThat(check.check(jti, null, null).block(Duration.ofSeconds(10)))
                .isEqualTo(RevocationDecision.ACTIVE);
    }

    @Test
    @DisplayName("revoking a session covers every token minted for it")
    void sessionRevocationCoversTheSession() {
        String sid = newId();
        durable.revoke(RevocationType.SESSION, sid, "backchannel_logout", "keycloak")
                .block(Duration.ofSeconds(10));

        assertThat(check.check(newId(), sid, newId()).block(Duration.ofSeconds(5)))
                .as("a token the store has never seen is still revoked via its session")
                .isEqualTo(RevocationDecision.REVOKED);
    }

    @Test
    @DisplayName("revoking a user covers tokens from every session")
    void userRevocationCoversEveryToken() {
        String sub = newId();
        durable.revoke(RevocationType.USER, sub, "account_suspended", "admin")
                .block(Duration.ofSeconds(10));

        assertThat(check.check(newId(), newId(), sub).block(Duration.ofSeconds(5)))
                .isEqualTo(RevocationDecision.REVOKED);
    }

    @Test
    @DisplayName("revoking the same identifier twice overwrites rather than duplicating")
    void revokeIsIdempotent() {
        String jti = newId();

        durable.revoke(RevocationType.TOKEN, jti, "user_logout", "tester")
                .then(Mono.defer(() ->
                        durable.revoke(RevocationType.TOKEN, jti, "admin_revoke", "admin")))
                .block(Duration.ofSeconds(10));

        assertThat(repository.count().block(Duration.ofSeconds(5))).isEqualTo(1L);
        assertThat(repository.findById(RevocationType.TOKEN.documentId(jti))
                .block(Duration.ofSeconds(5))
                .getReason())
                .isEqualTo("admin_revoke");
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Total count for a meter, optionally filtered to one tag. */
    private double counter(String name, String tagKey, String tagValue) {
        return meterRegistry.find(name).counters().stream()
                .filter(counter -> tagKey == null
                        || tagValue.equals(counter.getId().getTag(tagKey)))
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }
}
