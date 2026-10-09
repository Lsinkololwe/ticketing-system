package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.shared.persistence.BoundedRetry;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @Version} refuses the second writer, and the retry that follows is bounded.
 *
 * <h2>What optimistic locking buys, precisely</h2>
 * Not the absence of a lost update — the <em>visibility</em> of one. Two callers read the same
 * document, both modify it, and the second {@code save} is rejected instead of quietly winning.
 * Without it the first caller's change disappears and nothing anywhere records that it did.
 *
 * <h2>Why it is not what protects inventory</h2>
 * Proven by mutation while writing {@link InventoryContentionTest}: replacing the conditional
 * {@code findAndModify} with read-modify-write did not oversell, because {@code @Version} caught
 * every collision — it raised {@code OptimisticLockingFailureException} 150 times instead.
 * Removing <em>both</em> guards produced 200 successful reservations against 50 seats.
 *
 * <p>So the two do different jobs. {@code @Version} converts a lost update into an error, which
 * at checkout is a 500 the customer sees. The conditional {@code findAndModify} converts it into
 * a refusal, which is an answer. That is why the inventory write uses the second, and
 * why this test exists separately for the first.</p>
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R6 · a concurrent modification is refused, and the retry is bounded")
class OptimisticLockingTest {

    private static final String TIER_ID = "tier-locking-probe";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_locking"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedTier() {
        template.remove(new Query(), TicketTier.class).block();

        TicketTier tier = new TicketTier();
        tier.setId(TIER_ID);
        tier.setEventId("event-locking-probe");
        tier.setName("Locking probe");
        tier.setAvailableQuantity(10);
        tier.setActive(true);
        template.save(tier).block();
    }

    @Test
    @DisplayName("the second writer of a stale document is refused")
    void staleWriteIsRefused() {
        // Both callers hold the document as it was at version n. This is not a contrived
        // scenario — it is every read-then-update, whenever two of them overlap.
        TicketTier first = template.findById(TIER_ID, TicketTier.class).block();
        TicketTier second = template.findById(TIER_ID, TicketTier.class).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        first.setName("renamed by the first caller");
        template.save(first).block();

        second.setName("renamed by the second caller");

        assertThatThrownBy(() -> template.save(second).block())
                .as("without this the second save wins silently and the first caller's change "
                        + "is gone, with nothing recording that it ever happened")
                .isInstanceOf(OptimisticLockingFailureException.class);

        TicketTier stored = template.findById(TIER_ID, TicketTier.class).block();
        assertThat(stored).isNotNull();
        assertThat(stored.getName()).isEqualTo("renamed by the first caller");
    }

    @Test
    @DisplayName("a bounded retry gives up rather than looping")
    void retryIsBounded() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<String> alwaysConflicts = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new OptimisticLockingFailureException("contended"));
        });

        assertThatThrownBy(() -> BoundedRetry.onVersionConflict(alwaysConflicts, 3).block())
                .as("an exhausted retry must surface as the original failure, or a caller that "
                        + "handles OptimisticLockingFailureException stops recognising it")
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(attempts.get())
                .as("""
                    Under sustained contention an unbounded retry never completes and never \
                    fails: requests accumulate, p99 climbs, and the service looks slow rather \
                    than stuck. A bound turns that into an error somebody can act on.""")
                .isEqualTo(4);   // the first attempt plus three retries
    }

    @Test
    @DisplayName("a retry that succeeds on the second attempt does not surface the conflict")
    void retrySucceedsWhenContentionClears() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<String> conflictsOnce = Mono.defer(() ->
                attempts.incrementAndGet() == 1
                        ? Mono.error(new OptimisticLockingFailureException("contended"))
                        : Mono.just("written"));

        assertThat(BoundedRetry.onVersionConflict(conflictsOnce).block()).isEqualTo("written");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("only version conflicts are retried")
    void otherFailuresAreNotRetried() {
        AtomicInteger attempts = new AtomicInteger();

        Mono<String> broken = Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("not a contention problem"));
        });

        assertThatThrownBy(() -> BoundedRetry.onVersionConflict(broken).block())
                .isInstanceOf(IllegalStateException.class);

        assertThat(attempts.get())
                .as("retrying an error that will never clear wastes the budget and delays the "
                        + "report of a fault that has nothing to do with concurrency")
                .isEqualTo(1);
    }
}
