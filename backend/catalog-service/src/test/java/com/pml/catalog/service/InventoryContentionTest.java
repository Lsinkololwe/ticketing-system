package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.impl.InventoryServiceImpl;
import com.pml.catalog.web.rest.dto.InventoryReservationResult;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inventory decrement, under the contention that breaks it.
 *
 * <h2>The failure this exists to catch</h2>
 * Read-modify-write oversells, and it oversells <em>only</em> at on-sale: two requests read the
 * same remaining count, both find room, both write back. Every functional test passes, the
 * counters reconcile against whichever document was read last, and the tier sells more tickets
 * than the venue holds. The correct shape is one {@code findAndModify} whose filter and update
 * are evaluated together by the server, in one round trip — which is what
 * {@link InventoryServiceImpl#reserveInventory} does, and what this proves it does.
 *
 * <h2>Against a replica set, and repeatedly</h2>
 * Against a standalone {@code mongod} a transaction is silently inert, so a
 * contention test there measures nothing. And a concurrency test that passes once has told you
 * very little — an interleaving that happens one run in ten is still an oversell in production
 * every day. {@link RepeatedTest} runs it several times; the assertion is exact, not "about 50".
 *
 * <h2>Exactly 50, not at most 50</h2>
 * Both bounds matter. More than 50 successes is an oversell. Fewer means the filter is rejecting
 * requests it should accept — inventory that exists but cannot be sold, which nobody reports as
 * a bug because the only symptom is a slightly smaller number.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R6 · 200 concurrent reservations against 50 seats yield exactly 50")
class InventoryContentionTest {

    private static final int CAPACITY = 50;
    private static final int CALLERS = 200;
    private static final String TIER_ID = "tier-contention-probe";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static InventoryService inventory;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_contention"));

        // Only findById is reached, and only on the failure path where the service explains
        // why a reservation was refused. Stubbing it keeps the test on the atomic write.
        TicketTierRepository tiers = Mockito.mock(TicketTierRepository.class);
        Mockito.when(tiers.findById(Mockito.anyString()))
                .thenAnswer(invocation -> template.findById(TIER_ID, TicketTier.class));

        inventory = new InventoryServiceImpl(template, tiers);
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
        tier.setEventId("event-contention-probe");
        tier.setName("Contention probe");
        tier.setAvailableQuantity(CAPACITY);
        tier.setReservedQuantity(0);
        tier.setSoldQuantity(0);
        tier.setActive(true);

        template.save(tier).block();
    }

    @RepeatedTest(3)
    @DisplayName("no interleaving lets a 51st reservation through")
    void twoHundredCallersOnFiftySeats() {
        List<InventoryReservationResult> results = Flux.range(0, CALLERS)
                // subscribeOn per call, on the elastic pool: flatMap alone would interleave
                // cooperatively on one thread and never produce the real race.
                .flatMap(i -> inventory.reserveInventory(TIER_ID, 1, "reservation-" + i)
                                .subscribeOn(Schedulers.boundedElastic()),
                        CALLERS)
                .collectList()
                .block();

        assertThat(results).hasSize(CALLERS);

        long granted = results.stream().filter(InventoryReservationResult::isSuccess).count();

        assertThat(granted)
                .as("%d callers competed for %d seats. More than %d is an oversell; fewer means "
                        + "the filter refused inventory that existed.", CALLERS, CAPACITY, CAPACITY)
                .isEqualTo(CAPACITY);

        TicketTier after = template.findById(TIER_ID, TicketTier.class).block();
        assertThat(after).isNotNull();
        assertThat(after.getReservedQuantity())
                .as("the document must agree with the count of successful callers — a counter "
                        + "that drifts from the decisions made against it reconciles to nothing")
                .isEqualTo(CAPACITY);
        assertThat(after.getTrueAvailableQuantity())
                .as("nothing left to sell")
                .isZero();
    }

    @Test
    @DisplayName("the reservation is refused once the tier is exhausted, not merely slowed")
    void refusalIsARefusal() {
        Flux.range(0, CAPACITY)
                .concatMap(i -> inventory.reserveInventory(TIER_ID, 1, "fill-" + i))
                .then().block();

        InventoryReservationResult afterExhaustion =
                inventory.reserveInventory(TIER_ID, 1, "one-too-many").block();

        assertThat(afterExhaustion).isNotNull();
        assertThat(afterExhaustion.isSuccess())
                .as("an empty findAndModify result means the filter did not match: sold out. "
                        + "That is a refusal to be reported, not an error to be retried.")
                .isFalse();
    }

    @Test
    @DisplayName("a multi-seat request is all-or-nothing")
    void partialFulfilmentNeverHappens() {
        // The filter compares the whole requested quantity, so a request for more than remains
        // must take nothing at all. Granting part of it would leave a caller holding seats it
        // was never told it had.
        Mono<InventoryReservationResult> tooLarge =
                inventory.reserveInventory(TIER_ID, CAPACITY + 1, "greedy");

        assertThat(tooLarge.block()).isNotNull()
                .extracting(InventoryReservationResult::isSuccess).isEqualTo(false);

        TicketTier after = template.findById(TIER_ID, TicketTier.class).block();
        assertThat(after).isNotNull();
        assertThat(after.getReservedQuantity())
                .as("a refused request must not have moved the counter at all")
                .isZero();
    }
}
