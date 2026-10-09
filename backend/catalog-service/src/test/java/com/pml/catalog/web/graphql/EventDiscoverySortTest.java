package com.pml.catalog.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.enums.EventDiscoverySort;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.service.EventDiscovery;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.EventDiscoveryFilterInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The orders of the public feed, over events that differ in start, publication, price and tickets sold. */
@Tag("L2")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R4 · the feed can be ordered by start, publication, price either way and tickets sold")
class EventDiscoverySortTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ORG = "65a1b2c3d4e5f60718293a4b";
    private static final EventDiscoveryFilterInput NO_FILTER = new DefaultInputObjectMapper()
            .mapToJavaObject(Map.of(), EventDiscoveryFilterInput.class);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventDiscovery discovery;

    @BeforeAll
    static void seed() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_discovery_sort");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();

        event("Alpha", "100.00", 5, -5, 10, EventStatus.PUBLISHED);
        event("Bravo", "20.00", 50, -1, 20, EventStatus.PUBLISHED);
        event("Charlie", "20.00", 10, -3, 5, EventStatus.PUBLISHED);
        event("Delta", null, 99, -2, 30, EventStatus.PUBLISHED);
        event("Over", "10.00", 500, -4, -3, EventStatus.PUBLISHED);
        event("Draft", "10.00", 500, -4, 7, EventStatus.DRAFT);
        discovery = new EventDiscovery(template, CLOCK, 1000);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static void event(String title, String price, int sold, int publishedDaysAgo, int startsInDays, EventStatus status) {
        Instant starts = NOW.plusSeconds(startsInDays * 86_400L);
        template.save(Event.builder().title(title).description("An evening of " + title).categoryId("MUSIC").cityId("LUSAKA")
                .organizationId(ORG).organizerId("o").status(status).published(status == EventStatus.PUBLISHED)
                .isActive(true).soldTickets(sold).totalCapacity(500)
                .publishedAt(status == EventStatus.PUBLISHED ? NOW.plusSeconds(publishedDaysAgo * 86_400L) : null)
                .lowestTicketPrice(price == null ? null : new BigDecimal(price))
                .eventDateTime(starts).endDateTime(starts.plusSeconds(4 * 3600)).createdAt(NOW.minusSeconds(86_400)).build()).block();
    }

    private static List<String> order(EventDiscoverySort sort) {
        return discovery.find(NO_FILTER, sort, 10, null).block().events().stream().map(Event::getTitle).toList();
    }

    @Test
    @DisplayName("soonest is the default and orders by start")
    void soonest() {
        assertThat(order(EventDiscoverySort.SOONEST)).containsExactly("Charlie", "Alpha", "Bravo", "Delta");
        assertThat(order(null)).containsExactly("Charlie", "Alpha", "Bravo", "Delta");
    }

    @Test
    @DisplayName("newest orders by publication, latest first")
    void newest() {
        assertThat(order(EventDiscoverySort.NEWEST)).containsExactly("Bravo", "Delta", "Charlie", "Alpha");
    }

    @Test
    @DisplayName("price orders by the cheapest tier on sale, soonest breaking a tie, and leaves out an event with no price")
    void price() {
        assertThat(order(EventDiscoverySort.PRICE_ASC)).containsExactly("Charlie", "Bravo", "Alpha");
        assertThat(order(EventDiscoverySort.PRICE_DESC)).containsExactly("Alpha", "Charlie", "Bravo");
    }

    @Test
    @DisplayName("popular orders by tickets sold")
    void popular() {
        assertThat(order(EventDiscoverySort.POPULAR)).containsExactly("Delta", "Bravo", "Charlie", "Alpha");
    }

    @Test
    @DisplayName("a price order still honours the price filter, which is the same field")
    void priceOrderWithPriceFilter() {
        EventDiscoveryFilterInput filter = new EventDiscoveryFilterInput(null, null, null, null, null, null, null, null, null,
                new BigDecimal("50"), null, null, null, null, null, null, null);

        List<String> found = discovery.find(filter, EventDiscoverySort.PRICE_ASC, 10, null).block().events().stream()
                .map(Event::getTitle).toList();

        assertThat(found).containsExactly("Alpha");
    }

    @ParameterizedTest
    @EnumSource(EventDiscoverySort.class)
    @DisplayName("each order pages by cursor, visiting every event once")
    void paging(EventDiscoverySort sort) {
        List<String> seen = new ArrayList<>();
        String after = null;
        EventDiscovery.Page page;
        do {
            page = discovery.find(NO_FILTER, sort, 2, after).block();
            page.events().stream().map(Event::getTitle).forEach(seen::add);
            after = EventDiscovery.cursor(page.start() + page.events().size());
        } while (page.hasNext());

        assertThat(seen).doesNotHaveDuplicates().isEqualTo(order(sort));
    }

    @ParameterizedTest
    @EnumSource(EventDiscoverySort.class)
    @DisplayName("each order is answered from an index, never a collection scan")
    void indexed(EventDiscoverySort sort) {
        Query query = discovery.query(NO_FILTER, sort);
        Document explained = template.getCollection(CatalogCollections.EVENTS)
                .flatMap(events -> Mono.from(events.find(query.getQueryObject())
                        .sort(EventDiscovery.orderOf(sort).stream().reduce(new Document(),
                                (doc, o) -> doc.append(o.getProperty(), o.isAscending() ? 1 : -1), (a, b) -> a)).explain()))
                .block();

        String plan = explained.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        assertThat(plan).containsAnyOf("IXSCAN", "TEXT_MATCH").doesNotContain("COLLSCAN");
    }
}
