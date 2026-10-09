package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.impl.InventoryServiceImpl;
import com.pml.shared.constants.EventStatus;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An event's sales totals move with its tiers, once per reservation: the figures the ranking orders by
 * and the organizer reads. Against a real replica set, where the atomic updates are the server's.
 */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-CAT-002")
@DisplayName("ET-CAT-004-R12 · an event's sold tickets, gross sales and commission follow booking's commits, releases and refunds")
class InventoryEventTotalsTest {

    private static final String ORG = "65a1b2c3d4e5f60718293a4b";
    private static final String TIER = "tier-totals-probe";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TicketTierRepository tiers;
    private static InventoryServiceImpl inventory;
    private String eventId;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = com.pml.catalog.testing.CatalogWiring.platformTemplate(client, "catalog_inventory_totals");
        tiers = new ReactiveMongoRepositoryFactory(template).getRepository(TicketTierRepository.class);
        inventory = new InventoryServiceImpl(template, tiers);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), TicketTier.class).block();
        template.remove(new Query(), Event.class).block();
        eventId = template.save(Event.builder().organizationId(ORG).organizerId("o").title("Jazz")
                .status(EventStatus.PUBLISHED).published(true).isActive(true).totalCapacity(10).availableTickets(10)
                .eventDateTime(Instant.parse("2026-12-01T18:00:00Z")).endDateTime(Instant.parse("2026-12-01T22:00:00Z"))
                .createdAt(Instant.parse("2026-10-01T10:00:00Z")).build()).block().getId();
        TicketTier tier = new TicketTier();
        tier.setId(TIER);
        tier.setEventId(eventId);
        tier.setOrganizationId(ORG);
        tier.setName("GA");
        tier.setQuantity(10);
        tier.setAvailableQuantity(10);
        tier.setActive(true);
        template.save(tier).block();
    }

    private Event event() {
        return template.findById(eventId, Event.class).block();
    }

    private void commit(String reservation, int quantity, String gross, String commission) {
        assertThat(inventory.reserveInventory(TIER, quantity, reservation).block().isSuccess()).isTrue();
        assertThat(inventory.commitReservedToSold(TIER, quantity, reservation,
                gross == null ? null : new BigDecimal(gross), commission == null ? null : new BigDecimal(commission))
                .block().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("a commit adds the tickets and the money to the event")
    void commitMovesTheEvent() {
        long versionBefore = event().getVersion();

        commit("res-1", 2, "200.00", "10.00");

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(2);
        assertThat(after.getAvailableTickets()).isEqualTo(8);
        assertThat(after.getGrossSales()).isEqualByComparingTo("200.00");
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("10.00");
        assertThat(after.getVersion()).as("a stale organizer save must not be able to write the old totals back")
                .isGreaterThan(versionBefore);
    }

    @Test
    @DisplayName("a replayed commit adds nothing")
    void replayAddsNothing() {
        commit("res-1", 2, "200.00", "10.00");

        assertThat(inventory.commitReservedToSold(TIER, 2, "res-1", new BigDecimal("200.00"), new BigDecimal("10.00"))
                .block().isSuccess()).isTrue();
        Flux.range(0, 10).flatMap(i -> inventory.commitReservedToSold(TIER, 2, "res-1", new BigDecimal("200.00"),
                new BigDecimal("10.00")).subscribeOn(Schedulers.boundedElastic()), 10).collectList().block();

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(2);
        assertThat(after.getGrossSales()).isEqualByComparingTo("200.00");
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("a commit that reports no money still counts the tickets")
    void ticketsWithoutMoney() {
        commit("res-1", 3, null, null);

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(3);
        assertThat(after.getAvailableTickets()).isEqualTo(7);
        assertThat(after.getGrossSales()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("reservations add up")
    void reservationsAdd() {
        commit("res-1", 2, "200.00", "10.00");
        commit("res-2", 1, "100.00", "5.00");

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(3);
        assertThat(after.getGrossSales()).isEqualByComparingTo("300.00");
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("releasing a committed reservation reverses exactly the money that sale reported")
    void releaseReverses() {
        commit("res-1", 2, "200.00", "10.00");
        commit("res-2", 1, "120.00", "6.00");

        assertThat(inventory.releaseReservedInventory(TIER, 2, "res-1").block().isSuccess()).isTrue();

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(1);
        assertThat(after.getAvailableTickets()).isEqualTo(9);
        assertThat(after.getGrossSales()).isEqualByComparingTo("120.00");
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("releasing a hold that never sold touches only the hold")
    void releaseOfAHold() {
        inventory.reserveInventory(TIER, 2, "res-1").block();

        assertThat(inventory.releaseReservedInventory(TIER, 2, "res-1").block().isSuccess()).isTrue();

        Event after = event();
        assertThat(after.getSoldTickets()).isZero();
        assertThat(after.getAvailableTickets()).isEqualTo(10);
    }

    @Test
    @DisplayName("a refund restores the tickets and takes the refunded money off")
    void restoreSubtracts() {
        commit("res-1", 3, "300.00", "15.00");

        assertThat(inventory.restoreSoldInventory(TIER, 1, "REFUND", new BigDecimal("100.00"), new BigDecimal("5.00"))
                .block().isSuccess()).isTrue();

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(2);
        assertThat(after.getAvailableTickets()).isEqualTo(8);
        assertThat(after.getGrossSales()).isEqualByComparingTo("200.00");
        assertThat(after.getCommissionAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("a refused restore leaves the event alone")
    void refusedRestore() {
        commit("res-1", 1, "100.00", "5.00");

        assertThat(inventory.restoreSoldInventory(TIER, 5, "REFUND", new BigDecimal("500.00"), BigDecimal.ZERO)
                .block().isSuccess()).isFalse();

        Event after = event();
        assertThat(after.getSoldTickets()).isEqualTo(1);
        assertThat(after.getGrossSales()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("the tier mirror recomputes sold tickets from the tiers, so a missed bump heals on the next tier write")
    void mirrorHeals() {
        commit("res-1", 4, "400.00", "20.00");
        template.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(eventId)),
                new org.springframework.data.mongodb.core.query.Update().set("soldTickets", 99), Event.class).block();

        new EventTierMirror(tiers, template, Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC))
                .refresh(eventId).block();

        assertThat(event().getSoldTickets()).isEqualTo(4);
        assertThat(event().getAvailableTickets()).isEqualTo(6);
    }
}
