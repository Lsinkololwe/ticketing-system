package com.pml.booking.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.persistence.BookingCollections;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.IndexRegistryAssertions;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking's index registry, on a real server.
 *
 * <h2>The two indexes this was written for</h2>
 * {@code booking_tier_inventory}'s unique {@code tierId} and
 * {@code booking_webhook_receipts}'s unique {@code providerEventId} did not exist. Neither is a
 * performance index:
 *
 * <ul>
 *   <li>Without the first, two reservation flows can create two inventory rows for one tier, and
 *       the conditional decrement that is supposed to be the platform's only inventory write
 *       then guards <em>one of them</em>. The tier oversells and every counter still reconciles
 *       against the row it happened to read.</li>
 *   <li>Without the second, a provider redelivering a callback — which PawaPay does by design —
 *       is not stopped at the database. The application's own check is a read followed by a
 *       write, and two concurrent deliveries both read "not seen".</li>
 * </ul>
 *
 * <p>Both fail only under concurrency, which is the load that makes them expensive.</p>
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R3 · booking's declared indexes exist and are enforced")
class BookingIndexRegistryTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static IndexRegistryAssertions indexes;

    @BeforeAll
    static void createIndexes() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_index_registry"));
        indexes = new IndexRegistryAssertions(template);

        // The Testcontainers instance is reused between runs, so the database is not empty at
        // the start of a suite. Dropping it makes this test measure the indexes these
        // declarations produce, rather than whatever indexes happen to be lying around.
        template.getMongoDatabase()
                .flatMap(database -> reactor.core.publisher.Mono.from(database.drop()))
                .block();

        IndexEnsurer.Report report = new IndexEnsurer(template)
                .ensure(BookingIndexInitializer.specifications())
                .block();

        assertThat(report).isNotNull();
        assertThat(report.isClean())
                .as("index creation reported conflicts: %s", report)
                .isTrue();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("every §4 row for booking exists with its declared uniqueness, sparseness and TTL")
    void registryIsSatisfied() {
        indexes.assertAllPresent(BookingIndexInitializer.specifications());
    }

    @Test
    @DisplayName("one inventory row per tier — the server refuses the second")
    void tierInventoryIsUniquePerTier() {
        // Asserted by inserting, not by reading `unique: true` off the index metadata. A
        // unique index built over the wrong field reports identical metadata and refuses
        // nothing, and this is the index the entire inventory guarantee rests on.
        indexes.assertRefusesDuplicate(BookingCollections.TIER_INVENTORY, "tierId", "tier-dup-probe");
    }

    @Test
    @DisplayName("a redelivered provider callback is refused at the database")
    void webhookReceiptsRejectReplays() {
        indexes.assertRefusesDuplicate(
                BookingCollections.WEBHOOK_RECEIPTS, "providerEventId", "evt-dup-probe");
    }

    @Test
    @DisplayName("one escrow account per event")
    void escrowIsUniquePerEvent() {
        indexes.assertRefusesDuplicate(BookingCollections.ESCROW_ACCOUNTS, "eventId", "event-dup-probe");
    }

    @Test
    @DisplayName("§4's hot queries plan as index scans")
    void hotQueriesUseAnIndex() {
        // These are booking's hot queries. Each is on the path that runs hardest at
        // on-sale, which is exactly when a COLLSCAN stops being a latency number.
        indexes.assertUsesIndex(BookingCollections.TIER_INVENTORY,
                new Document("tierId", "tier-1"));
        indexes.assertUsesIndex(BookingCollections.RESERVATIONS,
                new Document("tierId", "tier-1").append("status", "HELD"));
        indexes.assertUsesIndex(BookingCollections.TICKETS,
                new Document("eventId", "event-1").append("status", "ISSUED"));
        indexes.assertUsesIndex(BookingCollections.JOURNAL_LINES,
                new Document("accountCode", "4000").append("postedAt", new Document("$gte", 0)));
    }

    @Test
    @DisplayName("indexes the server carries that §4 does not declare")
    void undeclaredIndexesDoNotGrow() {
        // The registry and the server must hold the same set of indexes. This database is built
        // from the declarations alone, so equality holds here — but production also carries ~245
        // annotation-derived indexes, 28 of them unique constraints the registry does not list.
        // Those are not deleted to reach equality: dropping a live uniqueness constraint to make
        // two lists agree is the wrong way round. Until the registry lists them, this measures
        // the clean case.
        assertThat(indexes.undeclared(BookingIndexInitializer.specifications()))
                .as("an index created outside the registry is drift nobody chose")
                .isEmpty();
    }

    @Test
    @DisplayName("the declarations still match ET-PLT-002 §4, row for row")
    void declarationsMatchTheSpecification() {
        // Compared against the registry text, not against the server. Asserting that the server
        // carries what the code declared cannot catch the code drifting from the registry — change
        // a declaration and the server follows it, so both move together and stay consistent
        // with each other while agreeing with nothing.
        IndexRegistryAssertions.assertMatchesRegistry(BookingIndexInitializer.specifications(), "booking_");
    }
}
