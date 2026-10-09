package com.pml.shared.testing.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.Concurrency;
import com.pml.shared.testing.Harness;
import com.pml.shared.testing.Inventory;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contention, against a real replica set, with real threads released together.
 *
 * <h2>What this proves, and what it does not</h2>
 * The contention scenarios that matter — inventory, payment intents, payouts, escrow — live in
 * the services. What this class proves is the primitive every one of them rests on — the
 * conditional atomic update — and, more importantly, that {@link Concurrency} actually produces
 * contention.
 *
 * <p>So this class runs the 200-against-50 scenario twice: once with the conditional
 * {@code findAndModify} the specs mandate, and once with the read-modify-write they forbid.
 * The second <strong>must oversell</strong>. If it did not, the harness would be incapable
 * of detecting the defect it exists to catch, and every contention test written against it
 * afterwards would be decoration.
 */
@Tag("L5")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006-R5 · concurrency is proven under real contention")
class ContentionTest {

    private static final String COLLECTION = "booking_tier_inventory";
    private static final String TIER = "tier-contended";
    private static final int CAPACITY = 50;
    private static final int CALLERS = 200;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "harness_contention"));
    }

    @AfterAll
    static void disconnect() {
        Harness.unbind();
        client.close();
    }

    @BeforeEach
    void bind() {
        Harness.bind(template);
    }

    @Test
    @DisplayName("200 callers against 50 seats yield exactly 50, and the counters still sum to capacity")
    void conditionalUpdateHoldsUnderContention() {
        // Five runs. One pass is luck: a concurrency test which has passed once has told you
        // nothing.
        Concurrency.repeat(5, () -> {
            seed(CAPACITY);

            Concurrency.Outcome<String> outcome =
                    Concurrency.inParallel(CALLERS, caller -> reserveAtomically());

            assertThat(outcome.successCount())
                    .as("exactly the capacity may be sold, never more")
                    .isEqualTo(CAPACITY);
            assertThat(outcome.failureCount())
                    .as("everyone else is refused")
                    .isEqualTo(CALLERS - CAPACITY);
            assertThat(outcome.countFailuresMatching("TIER_SOLD_OUT"))
                    .as("and refused for the right reason")
                    .isEqualTo(CALLERS - CAPACITY);

            Inventory.assertConserved(TIER);
        });
    }

    @Test
    @DisplayName("the same scenario with a read-modify-write oversells — so the test has teeth")
    void readModifyWriteOversells() {
        // Deliberately the forbidden read-modify-write implementation. If this
        // does NOT oversell, the harness cannot see the bug it exists to see.
        boolean oversoldAtLeastOnce = false;
        int observedWorst = CAPACITY;

        for (int run = 0; run < 3 && !oversoldAtLeastOnce; run++) {
            seed(CAPACITY);
            Concurrency.Outcome<String> outcome =
                    Concurrency.inParallel(CALLERS, caller -> reserveByReadThenWrite());
            observedWorst = Math.max(observedWorst, outcome.successCount());
            oversoldAtLeastOnce = outcome.successCount() > CAPACITY;
        }

        assertThat(oversoldAtLeastOnce)
                .as("""
                    a read-modify-write decrement must oversell under 200 simultaneous callers. \
                    It did not, which means this harness is not generating real contention — and \
                    every contention test written against it would pass on a broken platform. \
                    Highest success count seen: %d against a capacity of %d.""",
                        observedWorst, CAPACITY)
                .isTrue();

        // And conservation is broken, which is what Inventory.assertConserved is for.
        assertThatThrownBy(() -> Inventory.assertConserved(TIER))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("not conserved");
    }

    // --------------------------------------------------------------- the two implementations

    /**
     * The one the specs mandate: a single conditional {@code findAndModify} filtering on
     * {@code availableQuantity >= 1}. An empty result is a refusal, not an error.
     */
    private String reserveAtomically() {
        Document moved = template.findAndModify(
                Query.query(Criteria.where("_id").is(TIER).and("availableQuantity").gte(1)),
                new Update().inc("availableQuantity", -1).inc("reservedQuantity", 1),
                FindAndModifyOptions.options().returnNew(true),
                Document.class, COLLECTION).block();

        if (moved == null) {
            throw new IllegalStateException("TIER_SOLD_OUT");
        }
        return moved.getString("_id");
    }

    /**
     * The one they forbid. Read, decide, write — with a network round trip in the gap where
     * every other caller reads the same stale number.
     */
    private String reserveByReadThenWrite() {
        Document current = template.findOne(
                Query.query(Criteria.where("_id").is(TIER)), Document.class, COLLECTION).block();

        int available = current == null ? 0 : ((Number) current.get("availableQuantity")).intValue();
        if (available < 1) {
            throw new IllegalStateException("TIER_SOLD_OUT");
        }

        template.updateFirst(
                Query.query(Criteria.where("_id").is(TIER)),
                new Update().set("availableQuantity", available - 1).inc("reservedQuantity", 1),
                COLLECTION).block();
        return TIER;
    }

    private void seed(int capacity) {
        template.save(new Document("_id", TIER)
                .append("capacity", capacity)
                .append("availableQuantity", capacity)
                .append("reservedQuantity", 0)
                .append("soldQuantity", 0), COLLECTION).block();
    }
}
