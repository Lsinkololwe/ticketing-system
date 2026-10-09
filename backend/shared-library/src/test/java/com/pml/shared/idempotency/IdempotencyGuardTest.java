package com.pml.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L5")
@Tag("ET-PLT-007")
@DisplayName("a retried money-moving request applies once, whatever happens to Redis")
class IdempotencyGuardTest {

    record Receipt(String id, int amount) {
    }

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private IdempotencyGuard guard;
    private final AtomicInteger applied = new AtomicInteger();

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(mongoClient, "idempotency_harness"));
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void freshGuard() {
        guard = guardOver(redis);
        applied.set(0);
    }

    private static IdempotencyGuard guardOver(ReactiveStringRedisTemplate template) {
        MongoIdempotencyLedger ledger = new MongoIdempotencyLedger(mongo, "platform_idempotency_probe",
                Clock.systemUTC(), Duration.ofHours(24));
        ledger.ensureIndexes().block();
        return new IdempotencyGuard(template, ledger, MAPPER, Duration.ofSeconds(5));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private Mono<Receipt> charge(int amount) {
        return Mono.fromSupplier(() -> new Receipt("rcpt-" + applied.incrementAndGet(), amount));
    }

    private Receipt run(String scope, String key, String fingerprint, int amount) {
        return guard.execute(scope, key, fingerprint, Receipt.class, () -> charge(amount)).block();
    }

    private static void flushRedis() {
        ReactiveRedisConnection connection = redisFactory.getReactiveConnection();
        try {
            connection.serverCommands().flushAll().block();
        } finally {
            connection.close();
        }
    }

    @Test
    @DisplayName("a repeat with the same key and request returns the first response without applying again")
    void replayReturnsTheOriginal() {
        String key = newKey();

        Receipt first = run("pay:user-1", key, "fp-a", 100);
        Receipt second = run("pay:user-1", key, "fp-a", 100);

        assertThat(second).isEqualTo(first);
        assertThat(applied).hasValue(1);
    }

    @Test
    @DisplayName("the same key with a different request is refused, is not retryable, and applies nothing")
    void changedRequestIsRefused() {
        String key = newKey();
        run("pay:user-1", key, "fp-a", 100);

        assertThatThrownBy(() -> run("pay:user-1", key, "fp-b", 999))
                .isInstanceOfSatisfying(DomainRefusal.class, refusal -> {
                    assertThat(refusal.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    assertThat(refusal.retryable()).isFalse();
                });
        assertThat(applied).hasValue(1);
    }

    @Test
    @DisplayName("twenty parallel submissions of one key apply exactly once and all see that result")
    void parallelSubmissionsApplyOnce() {
        String key = newKey();

        Concurrency.Outcome<Receipt> outcome = Concurrency.inParallel(20, caller -> run("pay:user-1", key, "fp-a", 100));

        assertThat(outcome.failures()).isEmpty();
        assertThat(applied).hasValue(1);
        assertThat(outcome.successes()).hasSize(20).containsOnly(outcome.successes().get(0));
    }

    @Test
    @DisplayName("a Redis flush between attempts cannot cause a second application")
    void flushDoesNotUnguard() {
        String key = newKey();
        Receipt first = run("pay:user-1", key, "fp-a", 100);

        flushRedis();

        assertThat(run("pay:user-1", key, "fp-a", 100)).isEqualTo(first);
        assertThat(applied).hasValue(1);
        assertThatThrownBy(() -> run("pay:user-1", key, "fp-b", 100))
                .isInstanceOfSatisfying(DomainRefusal.class,
                        refusal -> assertThat(refusal.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("parallel submissions still apply once when Redis is flushed in the middle of them")
    void flushDuringContention() {
        String key = newKey();
        Concurrency.Outcome<Receipt> outcome = Concurrency.inParallel(20, caller -> {
            if (caller == 10) {
                flushRedis();
            }
            return run("pay:user-1", key, "fp-a", 100);
        });

        assertThat(outcome.failures()).isEmpty();
        assertThat(applied).hasValue(1);
    }

    @Test
    @DisplayName("with Redis unreachable the guard still applies once")
    void redisDownFailsOpenToTheLedger() {
        LettuceConnectionFactory dead = new LettuceConnectionFactory("127.0.0.1", 1);
        dead.afterPropertiesSet();
        guard = guardOver(new ReactiveStringRedisTemplate(dead));
        String key = newKey();

        Concurrency.Outcome<Receipt> outcome = Concurrency.inParallel(8, caller -> run("pay:user-1", key, "fp-a", 100));

        assertThat(outcome.failures()).isEmpty();
        assertThat(applied).hasValue(1);
        dead.destroy();
    }

    @Test
    @DisplayName("a failed operation frees the key so the retry applies")
    void failureReleasesTheKey() {
        String key = newKey();

        assertThatThrownBy(() -> guard.execute("pay:user-1", key, "fp-a", Receipt.class,
                () -> Mono.<Receipt>error(new IllegalStateException("provider timed out"))).block())
                .isInstanceOf(IllegalStateException.class);

        assertThat(run("pay:user-1", key, "fp-a", 100).amount()).isEqualTo(100);
        assertThat(applied).hasValue(1);
    }

    @Test
    @DisplayName("one actor's key never returns another actor's response")
    void actorsAreIsolated() {
        String key = newKey();
        Receipt mine = run("pay:user-1", key, "fp-a", 100);

        Receipt theirs = run("pay:user-2", key, "fp-a", 100);

        assertThat(theirs).isNotEqualTo(mine);
        assertThat(applied).hasValue(2);
    }

    @Test
    @DisplayName("the same key under another operation is a separate request")
    void operationsAreIsolated() {
        String key = newKey();
        run("pay:user-1", key, "fp-a", 100);

        run("refund:user-1", key, "fp-b", 50);

        assertThat(applied).hasValue(2);
    }

    @Test
    @DisplayName("a blank key is refused before anything runs, and so is a hostile one's effect on the store")
    void blankAndHostileKeys() {
        assertThatThrownBy(() -> run("pay:user-1", "  ", "fp-a", 1)).isInstanceOf(DomainRefusal.class);
        assertThatThrownBy(() -> run("pay:user-1", null, "fp-a", 1)).isInstanceOf(DomainRefusal.class);
        assertThat(applied).hasValue(0);

        // Operators and path characters in a key are data: they must neither match other keys nor throw.
        run("pay:user-1", "{\"$ne\":null}", "fp-a", 1);
        run("pay:user-1", "../../other", "fp-a", 1);
        assertThat(applied).hasValue(2);
    }

    @Test
    @DisplayName("a response is recorded as the service produced it, so a replay carries the same fields")
    void responseSurvivesTheLedger() {
        String key = newKey();
        Receipt first = run("pay:user-1", key, "fp-a", 4200);
        flushRedis();

        Receipt replay = run("pay:user-1", key, "fp-a", 4200);

        assertThat(replay.amount()).isEqualTo(4200);
        assertThat(replay.id()).isEqualTo(first.id());
    }
}
