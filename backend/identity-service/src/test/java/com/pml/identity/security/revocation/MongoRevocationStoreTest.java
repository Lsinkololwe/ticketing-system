package com.pml.identity.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * A second revocation of an already-revoked identifier must not shorten the window the first one
 * set up. Against a real Mongo and Redis, with the clock under test control so the two
 * {@code revoke} calls land at known, different instants.
 */
@Tag("L2")
@Tag("ET-IDN-003")
@DisplayName("Repeating a revocation extends its window instead of leaving the earlier one in place")
class MongoRevocationStoreTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static RevocationRepository repository;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static TestClock clock;
    private static MongoRevocationStore store;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_revocation_store"));
        repository = new ReactiveMongoRepositoryFactory(mongo).getRepository(RevocationRepository.class);
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        clock = TestClock.frozenAt(Instant.parse("2026-10-10T00:00:00Z"));
        RevocationProperties properties = new RevocationProperties();
        properties.setAccessTokenLifespan(Duration.ofMinutes(5));
        properties.setClockSkew(Duration.ofSeconds(60));
        store = new MongoRevocationStore(repository, redis,
                new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl()), properties,
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
        clock.setTo(Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    @DisplayName("a later revocation of the same user pushes expiresAt forward, keeping the first reason")
    void laterRevocationExtendsTheWindow() {
        RevocationRecord first = store.revoke(RevocationType.USER, "sub-1",
                "signed out from this session", "self").block();
        clock.advance(Duration.ofMinutes(3));
        RevocationRecord second = store.revoke(RevocationType.USER, "sub-1",
                "account suspended for fraud review", "admin-1").block();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getExpiresAt()).as("the window moved out, not just stayed put")
                .isAfter(first.getExpiresAt());
        assertThat(second.getReason()).as("the original cause is kept, not overwritten")
                .isEqualTo("signed out from this session");
        assertThat(second.getRevokedBy()).isEqualTo("self");
        assertThat(second.getRevokedAt()).isEqualTo(first.getRevokedAt());
        assertThat(mongo.count(new Query(), RevocationRecord.class).block())
                .as("one row, not two").isEqualTo(1);
    }

    @Test
    @DisplayName("a second revocation that would expire earlier leaves the later window alone")
    void earlierCandidateDoesNotShortenTheWindow() {
        RevocationProperties longer = new RevocationProperties();
        longer.setAccessTokenLifespan(Duration.ofHours(1));
        MongoRevocationStore longStore = new MongoRevocationStore(repository, redis,
                new RevocationCacheTrust(redis, longer.getCacheCompletenessTtl()), longer,
                new RevocationMetrics(new SimpleMeterRegistry(), clock), clock);

        RevocationRecord first = longStore.revoke(RevocationType.USER, "sub-2",
                "account suspended, one hour of tokens to cover", "admin-1").block();
        clock.advance(Duration.ofMinutes(1));
        RevocationRecord second = store.revoke(RevocationType.USER, "sub-2",
                "a shorter-lived re-check", "self").block();

        assertThat(second.getExpiresAt()).isEqualTo(first.getExpiresAt());
        assertThat(second.getReason()).isEqualTo("account suspended, one hour of tokens to cover");
    }

    @Test
    @DisplayName("once the first row has actually expired, the next revocation replaces it rather than extending it")
    void expiredRowIsReplacedNotExtended() {
        store.revoke(RevocationType.USER, "sub-3", "first cause", "self").block();
        clock.advance(Duration.ofMinutes(10)); // past the 5-minute TTL + 60s skew
        RevocationRecord second = store.revoke(RevocationType.USER, "sub-3", "second cause", "admin-1").block();

        assertThat(second.getReason()).isEqualTo("second cause");
        assertThat(mongo.count(new Query(), RevocationRecord.class).block()).isEqualTo(1);
    }
}
