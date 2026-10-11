package com.pml.booking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.idempotency.IdempotencyGuard;
import com.pml.shared.idempotency.MongoIdempotencyLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the idempotency guard every money-moving mutation calls: a Redis fast path backed by a
 * Mongo ledger that is the actual authority (ET-PLT-007 R6).
 *
 * <p>Retention tracks {@code idem:{key}}'s own 24-hour Redis TTL — a ledger row that outlived the
 * Redis key would answer a replay Redis itself has already forgotten, so the two expire
 * together.</p>
 */
@Configuration
public class IdempotencyConfig {

    private static final Duration LEDGER_RETENTION = Duration.ofHours(24);

    /** How long a caller's request can wait for someone else's in-flight attempt on the same key. */
    private static final Duration IN_FLIGHT_WAIT = Duration.ofSeconds(30);

    @Bean
    public MongoIdempotencyLedger mongoIdempotencyLedger(ReactiveMongoTemplate mongo, Clock clock) {
        return new MongoIdempotencyLedger(mongo, BookingCollections.IDEMPOTENCY_LEDGER, clock, LEDGER_RETENTION);
    }

    @Bean
    public IdempotencyGuard idempotencyGuard(ReactiveStringRedisTemplate redis, MongoIdempotencyLedger ledger,
                                             ObjectMapper mapper) {
        return new IdempotencyGuard(redis, ledger, mapper, IN_FLIGHT_WAIT);
    }

    @Slf4j
    @RequiredArgsConstructor
    static class IndexInitializer {

        private final MongoIdempotencyLedger ledger;

        @EventListener(ApplicationReadyEvent.class)
        void ensureIndexes() {
            ledger.ensureIndexes().subscribe(
                    done -> { },
                    error -> log.error("[Idempotency] Could not create the ledger's TTL index — "
                            + "rows will not expire until this is retried", error));
        }
    }

    @Bean
    IndexInitializer idempotencyLedgerIndexInitializer(MongoIdempotencyLedger ledger) {
        return new IndexInitializer(ledger);
    }
}
