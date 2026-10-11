package com.pml.shared.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

/**
 * A Redis that can evict keys it did not expire cannot vouch for a token by lacking its key.
 * Each case runs against its own Testcontainers Redis started with the policy under test, so no other suite's
 * cache is reconfigured.
 */
@Tag("L2")
@Tag("ET-IDN-003")
@DisplayName("A cache that may evict revocation keys is untrusted: misses go to the durable store")
class EvictionPolicyTrustTest {

    private static GenericContainer<?> evicting;
    private static GenericContainer<?> safe;

    @BeforeAll
    static void start() {
        evicting = startTestcontainersRedis("--maxmemory", "100mb", "--maxmemory-policy", "allkeys-lru");
        safe = startTestcontainersRedis("--maxmemory", "100mb", "--maxmemory-policy", "noeviction");
    }

    @AfterAll
    static void stop() {
        evicting.stop();
        safe.stop();
    }

    private static GenericContainer<?> startTestcontainersRedis(String... flags) {
        String[] command = new String[flags.length + 1];
        command[0] = "redis-server";
        System.arraycopy(flags, 0, command, 1, flags.length);
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379).withCommand(command)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
        container.start();
        return container;
    }

    /** What the check sees: a connection, the probe's verdict, and how often the durable store was asked. */
    private record Rig(ReactiveStringRedisTemplate redis, RedisEvictionPolicyProbe probe,
                       RevocationCacheTrust trust, RevocationCheck check, AtomicInteger durableAsked,
                       RevocationMetrics metrics, LettuceConnectionFactory factory) {
    }

    private static Rig rigOver(GenericContainer<?> container) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
        factory.afterPropertiesSet();
        ReactiveStringRedisTemplate redis = new ReactiveStringRedisTemplate(factory);
        RedisEvictionPolicyProbe probe = new RedisEvictionPolicyProbe(factory);
        probe.probe().block();

        RevocationProperties properties = new RevocationProperties();
        RevocationCacheTrust trust = new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl(),
                () -> probe.lastResult().safety() != RedisEvictionPolicyProbe.Safety.AT_RISK);
        AtomicInteger asked = new AtomicInteger();
        DurableRevocationStore durable = new DurableRevocationStore() {
            @Override
            public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
                asked.incrementAndGet();
                return Mono.just(RevocationDecision.REVOKED);
            }

            @Override
            public Mono<Void> ping() {
                return Mono.empty();
            }

            @Override
            public String name() {
                return "test-durable";
            }
        };
        RevocationMetrics metrics = new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC());
        RevocationCheck check = new CachedRevocationCheck(redis, trust, durable, properties, metrics,
                CircuitBreaker.ofDefaults("eviction-" + UUID.randomUUID()), TimeLimiter.ofDefaults());
        return new Rig(redis, probe, trust, check, asked, metrics, factory);
    }

    @Test
    @DisplayName("allkeys-lru with a memory limit is reported at risk, and a present sentinel does not make a miss trustworthy")
    void evictingPolicyDistrustsMisses() {
        Rig rig = rigOver(evicting);
        try {
            assertThat(rig.probe().lastResult().safety()).isEqualTo(RedisEvictionPolicyProbe.Safety.AT_RISK);
            assertThat(rig.probe().lastResult().policy()).isEqualTo("allkeys-lru");
            rig.trust().markComplete().block();

            assertThat(rig.trust().isComplete().block()).isFalse();
            assertThat(rig.check().check("jti-x", "sid-x", "sub-x").block()).isEqualTo(RevocationDecision.REVOKED);
            assertThat(rig.durableAsked().get()).as("the durable store decided").isEqualTo(1);
        } finally {
            rig.factory().destroy();
        }
    }

    @Test
    @DisplayName("noeviction keeps the fast path: a complete cache's miss is trusted without asking the durable store")
    void noEvictionTrustsMisses() {
        Rig rig = rigOver(safe);
        try {
            assertThat(rig.probe().lastResult().safety()).isEqualTo(RedisEvictionPolicyProbe.Safety.SAFE);
            rig.trust().markComplete().block();

            assertThat(rig.check().check("jti-y", "sid-y", "sub-y").block()).isEqualTo(RevocationDecision.ACTIVE);
            assertThat(rig.durableAsked().get()).isZero();
        } finally {
            rig.factory().destroy();
        }
    }

    @Test
    @DisplayName("the health endpoint reports DEGRADED and names the policy under an evicting Redis, UP under noeviction")
    void healthNamesTheCondition() {
        Rig risky = rigOver(evicting);
        Rig fine = rigOver(safe);
        try {
            assertThat(health(risky).getStatus()).isEqualTo(RevocationHealthIndicator.DEGRADED);
            assertThat(health(risky).getDetails()).containsEntry("cacheEvictionSafety", "AT_RISK")
                    .containsEntry("cacheMaxmemoryPolicy", "allkeys-lru");
            assertThat(health(fine).getStatus()).isEqualTo(Status.UP);
        } finally {
            risky.factory().destroy();
            fine.factory().destroy();
        }
    }

    private static org.springframework.boot.actuate.health.Health health(Rig rig) {
        DurableRevocationStore durable = new DurableRevocationStore() {
            @Override
            public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
                return Mono.just(RevocationDecision.ACTIVE);
            }

            @Override
            public Mono<Void> ping() {
                return Mono.empty();
            }

            @Override
            public String name() {
                return "test-durable";
            }
        };
        return new RevocationHealthIndicator(rig.redis(), durable, rig.probe(), rig.metrics(),
                CircuitBreaker.ofDefaults("health-" + UUID.randomUUID()), new RevocationProperties())
                .health().block(Duration.ofSeconds(10));
    }
}
