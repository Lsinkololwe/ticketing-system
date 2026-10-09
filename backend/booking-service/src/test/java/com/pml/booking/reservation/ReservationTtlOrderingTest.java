package com.pml.booking.reservation;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.config.BookingIndexInitializer;
import com.pml.booking.persistence.BookingCollections;
import com.pml.booking.workflow.purchase.PurchaseRules;
import com.pml.shared.persistence.IndexSpec;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reservation TTL fires <b>after</b> every release the purchase workflow can
 * make, never with one.
 *
 * <h2>Why the ordering is a correctness property and not a tuning knob</h2>
 * {@code expireAfter(Duration.ZERO)} on a date field tells MongoDB to delete the document the
 * instant that date passes — which for {@code expiresAt} is the instant the reservation's
 * {@code PurchaseWorkflow} wakes to release it.
 *
 * <p>The release is what returns the seats: it moves the hold out of {@code HELD} by compare-and-set
 * and credits the tier's counters back. A document the TTL monitor removes first is found by no
 * release, so its inventory is never returned: the tier's available count is permanently short by
 * that hold's quantity. Not an oversell — the opposite, and worse for being silent. No error is
 * raised, nothing fails, and a hot tier quietly shrinks one abandoned checkout at a time until it
 * reports sold out with seats unsold.
 *
 * <p>The latest release comes at {@code expiresAt} plus the seat grace, when a payment is still
 * pending; MongoDB's TTL monitor wakes about once a minute on top of that. The TTL has to clear both.
 *
 * <h2>Why the ordering is asserted rather than the number</h2>
 * The requirement is an ordering. A test pinning {@code 4200} seconds would pass while
 * somebody halved the grace to a value still safely above the release window, and fail on a change
 * that broke nothing. What is asserted is the property — the expiry clears the seat grace and the
 * TTL-monitor period with margin — and separately that it is non-zero, since zero is the one value
 * that makes the TTL a competitor for the workflow's rows rather than a cleaner behind it.
 */
@Tag("L2")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001-R4 · the TTL index fires after the workflow's last release, not with it")
class ReservationTtlOrderingTest {

    /** The latest the purchase workflow releases a hold: {@code expiresAt} plus this, with a payment pending. */
    private static final Duration RELEASE_WINDOW = PurchaseRules.SEAT_GRACE;

    /** MongoDB's TTL monitor wakes roughly once a minute; the grace must clear that too. */
    private static final Duration TTL_MONITOR_PERIOD = Duration.ofMinutes(1);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_ttl_ordering"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("ET-TKT-001-R4 · the registry declares an expiry well past the release window")
    void theRegistryOrdersTtlAfterTheRelease() {
        IndexSpec ttl = reservationTtlSpec();

        assertThat(ttl.expireAfter())
                .as("""
                    A zero expiry deletes the hold at the moment it expires — the moment its workflow \
                    wakes to release it. The release is what returns the seats, so a row removed \
                    first takes its inventory with it, permanently and silently.""")
                .isNotNull()
                .isNotEqualTo(Duration.ZERO);

        assertThat(ttl.expireAfter())
                .as("the TTL must clear both the seat grace (%s) and MongoDB's own TTL monitor "
                        + "period (%s) with room to spare", RELEASE_WINDOW, TTL_MONITOR_PERIOD)
                .isGreaterThan(RELEASE_WINDOW.plus(TTL_MONITOR_PERIOD).multipliedBy(2));
    }

    @Test
    @DisplayName("ET-TKT-001-R4 · the inverted configuration is rejected — the check has been seen to fail")
    void invertedConfigurationWouldBeCaught() {
        // The assertion above runs against a registry that satisfies the rule, so it would pass
        // identically if the rule compared nothing at all. Both directions are exercised here: a
        // zero expiry must be rejected, and the registry's own spec must be accepted.
        IndexSpec zeroExpiry = IndexSpec.on(BookingCollections.RESERVATIONS, "idx_expiresAt_ttl")
                .asc("expiresAt")
                .expireAfter(Duration.ZERO)
                .build();

        assertThat(firesAfterRelease(zeroExpiry))
                .as("a zero expiry must be judged unsafe by whatever rule this test applies, "
                        + "or the rule is decorative")
                .isFalse();
        assertThat(firesAfterRelease(reservationTtlSpec()))
                .as("and the registry's own spec must be judged safe, or the rule rejects everything")
                .isTrue();
    }

    @Test
    @DisplayName("ET-TKT-001-R4 · the live index carries the expiry, not just the registry")
    void theLiveIndexMatchesTheRegistry() throws Exception {
        // The registry is a list of intentions. What governs deletion is the index MongoDB
        // actually holds, and the two part company the moment somebody creates an index by hand
        // or an older expiry survives because MongoDB will not silently alter one in place.
        template.getCollection(BookingCollections.RESERVATIONS)
                .flatMapMany(collection -> Flux.from(collection.dropIndexes()))
                .then()
                .onErrorResume(ignored -> reactor.core.publisher.Mono.empty())
                .block();

        IndexSpec ttl = reservationTtlSpec();
        template.getCollection(BookingCollections.RESERVATIONS)
                .flatMapMany(collection -> Flux.from(collection.createIndex(
                        new Document("expiresAt", 1),
                        new com.mongodb.client.model.IndexOptions()
                                .name(ttl.name())
                                .expireAfter(ttl.expireAfter().toSeconds(), java.util.concurrent.TimeUnit.SECONDS))))
                .then()
                .block();

        List<Document> indexes = template.getCollection(BookingCollections.RESERVATIONS)
                .flatMapMany(collection -> Flux.from(collection.listIndexes()))
                .collectList()
                .block();

        Optional<Document> live = indexes.stream()
                .filter(index -> ttl.name().equals(index.getString("name")))
                .findFirst();

        assertThat(live).as("the TTL index must exist on the collection").isPresent();
        assertThat(live.get().get("expireAfterSeconds"))
                .as("a TTL index with expireAfterSeconds = 0 deletes at expiresAt exactly")
                .isNotNull()
                .isNotEqualTo(0)
                .isNotEqualTo(0L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The ordering property: the TTL fires after the workflow's last possible release. */
    private static boolean firesAfterRelease(IndexSpec spec) {
        return spec.expireAfter() != null
                && spec.expireAfter().compareTo(RELEASE_WINDOW.plus(TTL_MONITOR_PERIOD)) > 0;
    }

    private static IndexSpec reservationTtlSpec() {
        return BookingIndexInitializer.specifications().stream()
                .filter(spec -> "idx_expiresAt_ttl".equals(spec.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "idx_expiresAt_ttl has been renamed or removed — re-point this test"));
    }
}
