package com.pml.catalog.web.graphql;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.service.EventDiscovery;
import com.pml.catalog.service.EventService;
import com.pml.catalog.service.EventStatsService;
import com.pml.catalog.service.PendingApprovalStatsService;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.CursorPaginationInput;
import com.pml.catalog.web.graphql.dto.EventConnection;
import com.pml.catalog.web.graphql.dto.EventDiscoveryFilterInput;
import com.pml.catalog.web.graphql.dto.EventEdge;
import com.pml.catalog.web.graphql.query.EventQueryResolver;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.graphql.PageSize;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The public event feed against a MongoDB replica set carrying catalog's own indexes and validators:
 * it shows published events that have not ended, narrows by the five discovery filters, answers every
 * combination from an index, and cannot be walked end to end.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@Tag("ET-CAT-003")
@DisplayName("Discovery shows published events, filtered by index, and nothing else")
class EventDiscoveryTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String ORG = "65a1b2c3d4e5f60718293a4b";
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventQueryResolver resolver;

    @BeforeAll
    static void seed() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_event_discovery");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();

        template.insertAll(List.of(
                event("Lusaka Jazz Night", "An evening of live jazz", "MUSIC", "LUSAKA", days(10), "150.00", EventStatus.PUBLISHED),
                event("Kitwe Rock Festival", "Guitars and a mention of jazz", "MUSIC", "KITWE", days(20), "50.00", EventStatus.PUBLISHED),
                event("Lusaka Marathon", "Run the city", "SPORTS", "LUSAKA", days(30), "0.00", EventStatus.PUBLISHED),
                event("Ndola Tech Summit", "Talks on software", "TECH", "NDOLA", days(40), "500.00", EventStatus.PUBLISHED),
                event("Secret Draft Gala", "Not announced", "MUSIC", "LUSAKA", days(12), "100.00", EventStatus.DRAFT),
                event("Approved Not Published", "Waiting", "MUSIC", "LUSAKA", days(14), "100.00", EventStatus.APPROVED),
                event("Last Year's Jazz", "Already over", "MUSIC", "LUSAKA", days(-10), "100.00", EventStatus.PUBLISHED)))
                .collectList().block();
        Event deleted = event("Deleted Jazz Brunch", "Removed", "MUSIC", "LUSAKA", days(15), "80.00", EventStatus.PUBLISHED);
        deleted.setDeleted(true);
        template.insert(deleted).block();

        resolver = resolverWithDepth(1000);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static Instant days(int n) {
        return NOW.plusSeconds(n * 86_400L);
    }

    private static Event event(String title, String description, String category, String city, Instant starts,
                               String lowest, EventStatus status) {
        return Event.builder()
                .title(title).description(description).categoryId(category).cityId(city)
                .organizationId(ORG).organizerId("user-1")
                .status(status).published(status == EventStatus.PUBLISHED).isActive(true)
                .eventDateTime(starts).endDateTime(starts.plusSeconds(6 * 3600))
                .lowestTicketPrice(new BigDecimal(lowest)).totalCapacity(100).createdAt(NOW.minusSeconds(86_400))
                .build();
    }

    private static EventQueryResolver resolverWithDepth(int depth) {
        return new EventQueryResolver(Mockito.mock(EventService.class), CLOCK, Mockito.mock(EventStatsService.class),
                Mockito.mock(PendingApprovalStatsService.class), new EventDiscovery(template, CLOCK, depth));
    }

    private static EventDiscoveryFilterInput filter(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return new DefaultInputObjectMapper().mapToJavaObject(map, EventDiscoveryFilterInput.class);
    }

    private static List<String> titles(EventDiscoveryFilterInput filter) {
        return resolver.discoverEvents(filter, null, null).block().getEdges().stream()
                .map(edge -> edge.getNode().getTitle()).toList();
    }

    private static List<String> refused(EventDiscoveryFilterInput filter, CursorPaginationInput pagination) {
        try {
            resolver.discoverEvents(filter, pagination, null).block();
        } catch (ValidationRefusal refusal) {
            return refusal.violations().stream().map(FieldViolation::path).toList();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("only published events that have not ended are shown, soonest first")
    void publicFeed() {
        assertThat(titles(filter())).containsExactly(
                "Lusaka Jazz Night", "Kitwe Rock Festival", "Lusaka Marathon", "Ndola Tech Summit");
    }

    @Nested
    @DisplayName("each filter narrows the feed")
    class Filters {

        @Test
        @DisplayName("by category")
        void category() {
            assertThat(titles(filter("categoryId", "MUSIC"))).containsExactly("Lusaka Jazz Night", "Kitwe Rock Festival");
        }

        @Test
        @DisplayName("by city, from the reference data's code")
        void city() {
            assertThat(titles(filter("cityId", "LUSAKA"))).containsExactly("Lusaka Jazz Night", "Lusaka Marathon");
        }

        @Test
        @DisplayName("by start-date range")
        void dates() {
            assertThat(titles(filter("startDate", days(15), "endDate", days(35))))
                    .containsExactly("Kitwe Rock Festival", "Lusaka Marathon");
        }

        @Test
        @DisplayName("by price, compared as money and not as text: 50 is below 150")
        void price() {
            assertThat(titles(filter("minPrice", new BigDecimal("40"), "maxPrice", new BigDecimal("160"))))
                    .containsExactly("Lusaka Jazz Night", "Kitwe Rock Festival");
            assertThat(titles(filter("maxPrice", BigDecimal.ZERO))).containsExactly("Lusaka Marathon");
        }

        @Test
        @DisplayName("by free text over title and description")
        void text() {
            assertThat(titles(filter("searchQuery", "jazz"))).containsExactlyInAnyOrder("Lusaka Jazz Night", "Kitwe Rock Festival");
        }

        @Test
        @DisplayName("in combination")
        void combined() {
            assertThat(titles(filter("categoryId", "MUSIC", "cityId", "KITWE", "maxPrice", new BigDecimal("100"))))
                    .containsExactly("Kitwe Rock Festival");
        }

        @Test
        @DisplayName("a search term that looks like an operator is only a word")
        void noOperators() {
            assertThat(titles(filter("searchQuery", "-jazz"))).contains("Lusaka Jazz Night");
        }
    }

    static Stream<EventDiscoveryFilterInput> everyCombination() {
        List<EventDiscoveryFilterInput> all = new ArrayList<>();
        for (int mask = 0; mask < 32; mask++) {
            List<Object> pairs = new ArrayList<>();
            if ((mask & 1) != 0) { pairs.add("categoryId"); pairs.add("MUSIC"); }
            if ((mask & 2) != 0) { pairs.add("cityId"); pairs.add("LUSAKA"); }
            if ((mask & 4) != 0) { pairs.add("startDate"); pairs.add(days(1)); pairs.add("endDate"); pairs.add(days(90)); }
            if ((mask & 8) != 0) { pairs.add("minPrice"); pairs.add(BigDecimal.ONE); pairs.add("maxPrice"); pairs.add(new BigDecimal("999")); }
            if ((mask & 16) != 0) { pairs.add("searchQuery"); pairs.add("jazz"); }
            all.add(filter(pairs.toArray()));
        }
        return all.stream();
    }

    @ParameterizedTest
    @MethodSource("everyCombination")
    @DisplayName("every combination of the five filters is answered from an index, never a collection scan")
    void indexed(EventDiscoveryFilterInput filter) {
        Query query = new EventDiscovery(template, CLOCK, 1000).query(filter);
        Document explained = template.getCollection(CatalogCollections.EVENTS)
                .flatMap(events -> Mono.from(events.find(query.getQueryObject())
                        .sort(new Document("eventDateTime", 1).append("_id", 1)).explain()))
                .block();

        String plan = explained.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        assertThat(plan).containsAnyOf("IXSCAN", "TEXT_MATCH").doesNotContain("COLLSCAN");
    }

    @Nested
    @DisplayName("what discovery refuses")
    class Refusals {

        @ParameterizedTest
        @ValueSource(strings = {"cityName", "country", "provinceId", "organizerId"})
        @DisplayName("a filter outside the five, rather than run it unindexed")
        void unsupportedText(String field) {
            assertThat(refused(filter(field, "x"), null)).containsExactly("filter." + field);
        }

        @ParameterizedTest
        @ValueSource(strings = {"isFreeEvent", "hasAvailableTickets", "isAccessible", "isVirtual", "isFeatured"})
        @DisplayName("a flag outside the five")
        void unsupportedFlag(String field) {
            assertThat(refused(filter(field, true), null)).containsExactly("filter." + field);
        }

        @Test
        @DisplayName("a search shorter than three characters")
        void shortSearch() {
            assertThat(refused(filter("searchQuery", "ja"), null)).containsExactly("filter.searchQuery");
        }

        @Test
        @DisplayName("a page larger than 100, refused rather than quietly shortened")
        void pageSize() {
            assertThatThrownBy(() -> resolver.discoverEvents(filter(), new CursorPaginationInput(101, null, null, null), null).block())
                    .isInstanceOf(PageSize.PageSizeExceeded.class);
        }

        @Test
        @DisplayName("a cursor the feed never issued")
        void foreignCursor() {
            String forged = java.util.Base64.getUrlEncoder().encodeToString("someone-else:5".getBytes());

            assertThat(refused(filter(), new CursorPaginationInput(10, forged, null, null))).containsExactly("pagination.after");
        }
    }

    @Test
    @DisplayName("paging with the cursors visits every event once, in order")
    void paging() {
        List<String> seen = new ArrayList<>();
        String after = null;
        EventConnection page;
        do {
            page = resolver.discoverEvents(filter(), new CursorPaginationInput(3, after, null, null), null).block();
            page.getEdges().stream().map(EventEdge::getNode).map(Event::getTitle).forEach(seen::add);
            after = page.getPageInfo().getEndCursor();
        } while (page.getPageInfo().isHasNextPage());

        assertThat(seen).containsExactly("Lusaka Jazz Night", "Kitwe Rock Festival", "Lusaka Marathon", "Ndola Tech Summit");
    }

    @Test
    @DisplayName("the feed stops at its depth, and a forged cursor cannot go past it")
    void depthCap() {
        EventQueryResolver shallow = resolverWithDepth(3);
        EventConnection first = shallow.discoverEvents(filter(), new CursorPaginationInput(3, null, null, null), null).block();

        assertThat(first.getEdges()).hasSize(3);
        assertThatThrownBy(() -> shallow.discoverEvents(filter(),
                new CursorPaginationInput(1, first.getPageInfo().getEndCursor(), null, null), null).block())
                .isInstanceOf(ValidationRefusal.class);
        String deep = EventDiscovery.cursor(901);
        assertThatThrownBy(() -> resolver.discoverEvents(filter(), new CursorPaginationInput(100, deep, null, null), null).block())
                .isInstanceOf(ValidationRefusal.class);
    }
}
