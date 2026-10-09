package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.impl.InventoryServiceImpl;
import com.pml.catalog.web.rest.dto.InventoryOperationResult;
import com.pml.catalog.web.rest.dto.InventoryReservationResult;
import com.pml.shared.testing.MongoReplicaSet;
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
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tier's counters move at most once per reservation, however often a caller retries.
 *
 * <h2>Why retries are the load this is written for</h2>
 * booking calls hold, commit and release from paths that retry by design: a redelivered provider
 * answer, a recovery sweep, a workflow activity after a worker restart. Each retry that reached the
 * counters again would manufacture or destroy seats while every individual call looked correct. The
 * properties below run against a real replica set, where the conditional updates are evaluated by the
 * server exactly as in production.
 *
 * <h2>Conservation after every case</h2>
 * {@code availableQuantity + soldQuantity == capacity} and {@code reservedQuantity <= availableQuantity}
 * hold after every movement — a sold seat leaves {@code available}, a held one does not.
 */
@Tag("L2")
@Tag("ET-CAT-002")
@DisplayName("ET-CAT-002-R2 · hold, commit and release apply at most once per reservation")
class InventoryIdempotencyTest {

    private static final String TIER = "tier-idempotency-probe";
    private static final int CAPACITY = 10;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static InventoryServiceImpl inventory;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_inventory_idempotency"));
        TicketTierRepository tiers = new ReactiveMongoRepositoryFactory(template)
                .getRepository(TicketTierRepository.class);
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
        tier.setId(TIER);
        tier.setEventId("event-idempotency-probe");
        tier.setName("Idempotency probe");
        tier.setQuantity(CAPACITY);
        tier.setAvailableQuantity(CAPACITY);
        tier.setReservedQuantity(0);
        tier.setSoldQuantity(0);
        tier.setActive(true);
        template.save(tier).block();
    }

    @Test
    @DisplayName("a repeated hold for one reservation holds the seats once")
    void aRepeatedHoldHoldsOnce() {
        InventoryReservationResult first = inventory.reserveInventory(TIER, 2, "res-1").block();
        InventoryReservationResult second = inventory.reserveInventory(TIER, 2, "res-1").block();

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess())
                .as("the retry must report the hold the reservation already has, not refuse it")
                .isTrue();
        assertThat(tier().getReservedQuantity()).isEqualTo(2);
        assertThat(tier().getMovements()).singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getReservationId()).isEqualTo("res-1");
                    assertThat(movement.getQuantity()).isEqualTo(2);
                });
        assertConserved();
    }

    @Test
    @DisplayName("twenty simultaneous retries of one hold hold the seats once")
    void simultaneousRetriesHoldOnce() {
        List<InventoryReservationResult> results = Flux.range(0, 20)
                .flatMap(i -> inventory.reserveInventory(TIER, 2, "res-racing")
                        .subscribeOn(Schedulers.boundedElastic()), 20)
                .collectList()
                .block();

        assertThat(results).hasSize(20).allMatch(InventoryReservationResult::isSuccess);
        assertThat(tier().getReservedQuantity())
                .as("twenty deliveries of one hold are one hold")
                .isEqualTo(2);
        assertThat(tier().getMovements()).hasSize(1);
        assertConserved();
    }

    @Test
    @DisplayName("a repeated commit sells the seats once")
    void aRepeatedCommitSellsOnce() {
        inventory.reserveInventory(TIER, 2, "res-1").block();

        InventoryOperationResult first = inventory.commitReservedToSold(TIER, 2, "res-1").block();
        InventoryOperationResult second = inventory.commitReservedToSold(TIER, 2, "res-1").block();

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).as("a replayed commit is not an error").isTrue();
        TicketTier after = tier();
        assertThat(after.getSoldQuantity()).isEqualTo(2);
        assertThat(after.getReservedQuantity()).isZero();
        assertThat(after.getAvailableQuantity()).isEqualTo(CAPACITY - 2);
        assertConserved();
    }

    @Test
    @DisplayName("a repeated release returns the seats once")
    void aRepeatedReleaseReturnsOnce() {
        inventory.reserveInventory(TIER, 2, "res-1").block();
        inventory.reserveInventory(TIER, 3, "res-2").block();

        inventory.releaseReservedInventory(TIER, 2, "res-1").block();
        InventoryOperationResult replay = inventory.releaseReservedInventory(TIER, 2, "res-1").block();

        assertThat(replay.isSuccess()).isTrue();
        assertThat(tier().getReservedQuantity())
                .as("the second release must not take res-2's seats")
                .isEqualTo(3);
        assertConserved();
    }

    @Test
    @DisplayName("releasing a reservation that holds nothing returns nothing")
    void releasingANeverHeldReservationChangesNothing() {
        inventory.reserveInventory(TIER, 3, "res-holder").block();

        InventoryOperationResult release = inventory.releaseReservedInventory(TIER, 3, "res-stranger").block();

        assertThat(release.isSuccess()).isTrue();
        assertThat(tier().getReservedQuantity())
                .as("a release for a reservation with no hold must never free another buyer's seats")
                .isEqualTo(3);
        assertConserved();
    }

    @Test
    @DisplayName("a release after a commit reverses the sale, once")
    void releaseAfterCommitReversesTheSale() {
        inventory.reserveInventory(TIER, 2, "res-1").block();
        inventory.commitReservedToSold(TIER, 2, "res-1").block();

        inventory.releaseReservedInventory(TIER, 2, "res-1").block();
        inventory.releaseReservedInventory(TIER, 2, "res-1").block();

        TicketTier after = tier();
        assertThat(after.getSoldQuantity()).isZero();
        assertThat(after.getReservedQuantity()).isZero();
        assertThat(after.getAvailableQuantity()).isEqualTo(CAPACITY);
        assertThat(after.getMovements()).isEmpty();
        assertConserved();
    }

    @Test
    @DisplayName("a commit after a release is refused and sells nothing")
    void commitAfterReleaseIsRefused() {
        inventory.reserveInventory(TIER, 2, "res-1").block();
        inventory.releaseReservedInventory(TIER, 2, "res-1").block();

        InventoryOperationResult commit = inventory.commitReservedToSold(TIER, 2, "res-1").block();

        assertThat(commit.isSuccess()).isFalse();
        assertThat(tier().getSoldQuantity()).isZero();
        assertConserved();
    }

    @Test
    @DisplayName("a refused hold records no entry and moves no seats")
    void aRefusedHoldRecordsNothing() {
        InventoryReservationResult refused = inventory.reserveInventory(TIER, CAPACITY + 1, "res-greedy").block();

        assertThat(refused.isSuccess()).isFalse();
        assertThat(tier().getMovements()).isEmpty();
        assertThat(tier().getReservedQuantity()).isZero();
        assertConserved();
    }

    private static TicketTier tier() {
        TicketTier tier = template.findById(TIER, TicketTier.class).block();
        assertThat(tier).isNotNull();
        return tier;
    }

    private static void assertConserved() {
        TicketTier tier = tier();
        assertThat(tier.getAvailableQuantity() + tier.getSoldQuantity())
                .as("available + sold must equal capacity — a seat is either still for sale or sold")
                .isEqualTo(CAPACITY);
        assertThat(tier.getReservedQuantity())
                .as("held seats come out of the seats still for sale")
                .isBetween(0, tier.getAvailableQuantity());
    }
}
