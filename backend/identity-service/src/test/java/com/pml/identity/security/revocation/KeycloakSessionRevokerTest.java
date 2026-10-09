package com.pml.identity.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationKeys;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * ET-IDN-003 R7 · Keycloak logout revokes that {@code sid}, against real MongoDB and Redis.
 */
@Tag("L2")
@Tag("ET-IDN-003")
@DisplayName("ET-IDN-003-R7 · a Keycloak logout event writes a durable, cached, idempotent session revocation")
class KeycloakSessionRevokerTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static MongoRevocationStore store;
    private static RevocationRepository repository;
    private static final Clock CLOCK = Clock.systemUTC();

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_kc_logout"));
        repository = new ReactiveMongoRepositoryFactory(mongo).getRepository(RevocationRepository.class);
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        RevocationProperties properties = new RevocationProperties();
        properties.setAccessTokenLifespan(Duration.ofMinutes(5));
        store = new MongoRevocationStore(repository, redis,
                new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl()), properties,
                new RevocationMetrics(new SimpleMeterRegistry(), CLOCK), CLOCK);
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
    }

    @SuppressWarnings("unchecked")
    private static KeycloakSessionRevoker revoker(MongoRevocationStore s) {
        ObjectProvider<MongoRevocationStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(s);
        return new KeycloakSessionRevoker(provider);
    }

    private RevocationDecision decision(RevocationType type, String value) {
        return store.check(List.of(new RevocationIdentifier(type, value))).block();
    }

    @Test
    @DisplayName("LOGOUT with a sid: SESSION record in Mongo, pml:session:{sid} in Redis, only that session revoked")
    void logoutRevokesOnlyThatSession() {
        assertThat(revoker(store).revoke("LOGOUT", "sid-A", "myticketzm-admin").block()).isEqualTo(1);

        RevocationRecord record = repository.findById("SESSION:sid-A").block();
        assertThat(record).isNotNull();
        assertThat(record.getType()).isEqualTo(RevocationType.SESSION);
        assertThat(record.getValue()).isEqualTo("sid-A");
        assertThat(record.getReason()).isEqualTo("keycloak-logout");
        assertThat(record.getRevokedBy()).isEqualTo("keycloak:myticketzm-admin");
        assertThat(record.getExpiresAt()).isAfter(record.getRevokedAt());

        assertThat(redis.hasKey(RevocationKeys.session("sid-A")).block()).isTrue();
        assertThat(decision(RevocationType.SESSION, "sid-A")).isEqualTo(RevocationDecision.REVOKED);
        assertThat(decision(RevocationType.SESSION, "sid-B")).as("another session of the same user").isEqualTo(RevocationDecision.ACTIVE);
        assertThat(mongo.count(new Query(), RevocationRecord.class).block()).as("no USER or TOKEN record").isEqualTo(1);
    }

    @Test
    @DisplayName("a redelivered event is an idempotent upsert: one record, still revoked")
    void idempotent() {
        KeycloakSessionRevoker r = revoker(store);
        r.revoke("LOGOUT", "sid-A", "myticketzm").block();
        r.revoke("LOGOUT", "sid-A", "myticketzm").block();
        r.revoke("REFRESH_TOKEN_ERROR", "sid-A", "myticketzm").block();
        assertThat(mongo.count(new Query(), RevocationRecord.class).block()).isEqualTo(1);
        assertThat(decision(RevocationType.SESSION, "sid-A")).isEqualTo(RevocationDecision.REVOKED);
    }

    @Test
    @DisplayName("a blank sid writes nothing and never widens to the user")
    void blankSid() {
        assertThat(revoker(store).revoke("LOGOUT", " ", "myticketzm").block()).isZero();
        assertThat(revoker(store).revoke("LOGOUT", null, "myticketzm").block()).isZero();
        assertThat(mongo.count(new Query(), RevocationRecord.class).block()).isZero();
    }

    @Test
    @DisplayName("revocation switched off is an error, so the listener retries rather than believing it landed")
    void switchedOffIsAnError() {
        assertThatThrownBy(() -> revoker(null).revoke("LOGOUT", "sid-A", "myticketzm").block())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("only LOGOUT and REFRESH_TOKEN_ERROR end a session")
    void whichEvents() {
        assertThat(KeycloakSessionRevoker.endsSession("LOGOUT")).isTrue();
        assertThat(KeycloakSessionRevoker.endsSession("REFRESH_TOKEN_ERROR")).isTrue();
        assertThat(KeycloakSessionRevoker.endsSession("LOGIN")).isFalse();
        assertThat(KeycloakSessionRevoker.endsSession(null)).isFalse();
    }
}
