package com.pml.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Applies an operation at most once per client-supplied key.
 *
 * <p>Three outcomes for a key: a new one runs the operation; the same key with the same request
 * returns the response recorded the first time without running anything; the same key with a
 * different request is refused with {@code IDEMPOTENCY_KEY_REUSED}.</p>
 *
 * <h2>Two layers, one authority</h2>
 * <p>A Redis {@code SET NX} on {@code idem:{scope}:{key}} turns away an obvious reuse without a
 * database round trip and marks the key as in flight. It is only an accelerator: Redis can be
 * flushed or unreachable, and then the guard falls back to the {@link IdempotencyLedger}, whose
 * unique constraint is what actually guarantees a single application. The tests flush Redis
 * between attempts to prove it.</p>
 *
 * <h2>Concurrent submissions</h2>
 * <p>When a second caller presents a key whose first submission has not finished, it waits for the
 * recorded result rather than running the operation again. If the first is still running when the
 * wait ends the caller gets a retryable refusal; a later retry will find the result.</p>
 *
 * <h2>A failed operation frees its key</h2>
 * <p>If the operation errors, the claim is withdrawn so the client's retry can apply. An operation
 * handed to this guard must therefore be atomic: an error has to mean nothing took effect.</p>
 */
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final ReactiveStringRedisTemplate redis;
    private final IdempotencyLedger ledger;
    private final ObjectMapper mapper;
    private final Duration inFlightWait;

    public IdempotencyGuard(ReactiveStringRedisTemplate redis, IdempotencyLedger ledger,
                            ObjectMapper mapper, Duration inFlightWait) {
        this.redis = redis;
        this.ledger = ledger;
        this.mapper = mapper;
        this.inFlightWait = inFlightWait;
    }

    /**
     * @param scope       the operation name, so one key can be reused across different operations
     * @param fingerprint {@link Fingerprint#of} of the request
     * @param operation   the work; subscribed to at most once per key
     */
    public <T> Mono<T> execute(String scope, String key, String fingerprint, Class<T> type, Supplier<Mono<T>> operation) {
        if (key == null || key.isBlank()) {
            return Mono.error(new ValidationRefusal(List.of(new FieldViolation("idempotencyKey", "must not be blank"))));
        }
        return markInFlight(scope, key, fingerprint)
                .then(Mono.defer(() -> ledger.claim(scope, key, fingerprint)))
                .flatMap(claim -> claim.won()
                        ? run(scope, key, fingerprint, operation)
                        : replay(scope, key, fingerprint, claim.entry(), type));
    }

    /** Best effort: a Redis failure must not block a purchase, the ledger still guards it. */
    private Mono<Void> markInFlight(String scope, String key, String fingerprint) {
        String redisKey = redisKey(scope, key);
        return redis.opsForValue().setIfAbsent(redisKey, fingerprint, REDIS_TTL)
                .flatMap(acquired -> acquired ? Mono.<Void>empty()
                        : redis.opsForValue().get(redisKey)
                                .filter(existing -> !existing.equals(fingerprint))
                                .flatMap(existing -> Mono.<Void>error(new IdempotencyKeyReusedRefusal(scope))))
                .onErrorResume(error -> !(error instanceof IdempotencyKeyReusedRefusal), error -> {
                    log.warn("idempotency fast path unavailable, relying on the ledger: {}", error.toString());
                    return Mono.empty();
                });
    }

    private <T> Mono<T> run(String scope, String key, String fingerprint, Supplier<Mono<T>> operation) {
        return Mono.defer(operation)
                .flatMap(result -> serialise(result)
                        .flatMap(json -> ledger.complete(scope, key, json))
                        .thenReturn(result))
                .onErrorResume(error -> withdraw(scope, key).then(Mono.error(error)))
                // An operation with no result still has to record that it finished.
                .switchIfEmpty(Mono.defer(() -> ledger.complete(scope, key, null).then(Mono.<T>empty())));
    }

    private Mono<Void> withdraw(String scope, String key) {
        return ledger.release(scope, key)
                .then(redis.delete(redisKey(scope, key)).onErrorResume(error -> Mono.just(0L)).then());
    }

    private <T> Mono<T> replay(String scope, String key, String fingerprint, IdempotencyLedger.Entry entry, Class<T> type) {
        if (!entry.fingerprint().equals(fingerprint)) {
            return Mono.error(new IdempotencyKeyReusedRefusal(scope));
        }
        if (entry.completed()) {
            return deserialise(entry.response(), type);
        }
        return awaitCompletion(scope, key, fingerprint, type);
    }

    private <T> Mono<T> awaitCompletion(String scope, String key, String fingerprint, Class<T> type) {
        return Mono.defer(() -> ledger.find(scope, key))
                .filter(IdempotencyLedger.Entry::completed)
                .repeatWhenEmpty(attempts -> attempts.delayElements(POLL_INTERVAL))
                .timeout(inFlightWait, Mono.error(new IdempotentRequestInFlightRefusal(scope)))
                .flatMap(done -> done.fingerprint().equals(fingerprint)
                        ? deserialise(done.response(), type)
                        : Mono.<T>error(new IdempotencyKeyReusedRefusal(scope)));
    }

    private <T> Mono<String> serialise(T result) {
        try {
            return Mono.just(mapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("the operation's response could not be recorded", e));
        }
    }

    private <T> Mono<T> deserialise(String json, Class<T> type) {
        if (json == null) {
            return Mono.empty();
        }
        try {
            return Mono.just(mapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("the recorded response could not be read back", e));
        }
    }

    private static String redisKey(String scope, String key) {
        return "idem:" + scope + ":" + key;
    }
}
