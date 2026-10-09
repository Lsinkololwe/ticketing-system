package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.util.KeysetCursor;
import com.pml.catalog.web.graphql.query.OrganizerEventQueryResolver.OrganizerEventFilterInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** An organization's events, newest first, paged by a cursor that stays put while events are added. */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R11 · the organizer's event list pages by cursor, is scoped to the organization, and filters in the database")
class OrganizerEventFeedTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final String ORG_A = "65a1b2c3d4e5f60718293a4b";
    private static final String ORG_B = "65a1b2c3d4e5f60718293a4c";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizerEventFeed feed;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_organizer_feed");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();
        feed = new OrganizerEventFeed(template);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), Event.class).block();
    }

    private static Event event(String org, String title, EventStatus status, int createdSecond, int startsInDays) {
        Instant starts = T0.plusSeconds(startsInDays * 86_400L);
        return template.save(Event.builder().organizationId(org).organizerId("o").title(title).status(status)
                .isActive(true).totalCapacity(10).locationName("Mulungushi Hall")
                .eventDateTime(starts).endDateTime(starts.plusSeconds(3600))
                .createdAt(T0.plusSeconds(createdSecond)).build()).block();
    }

    private static OrganizerEventFeed.Page mine(TenantScope scope, OrganizerEventFilterInput filter, String after, int limit) {
        return feed.mine(filter, after, limit).contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope))).block();
    }

    private static final TenantScope A = TenantScope.of("user-a", Set.of(ORG_A));

    private static OrganizerEventFilterInput filter(EventStatus status, List<EventStatus> statuses, String search,
                                                    Instant after, Instant before) {
        return new OrganizerEventFilterInput(status, statuses, search, after, before);
    }

    @Test
    @DisplayName("pages newest first without a repeat or a gap, and a late arrival at the front does not shift the next page")
    void cursorIsStable() {
        for (int i = 0; i < 7; i++) {
            event(ORG_A, "E" + i, EventStatus.DRAFT, i, 10);
        }
        // Same instant as E6: the id breaks the tie.
        event(ORG_A, "E6b", EventStatus.DRAFT, 6, 10);

        OrganizerEventFeed.Page first = mine(A, null, null, 3);
        Event last = first.events().get(2);
        event(ORG_A, "Arrives mid-read", EventStatus.DRAFT, 500, 10);
        OrganizerEventFeed.Page second = mine(A, null, KeysetCursor.encode(last.getCreatedAt(), last.getId()), 3);

        assertThat(first.events()).extracting(Event::getTitle).containsExactly("E6b", "E6", "E5");
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
        assertThat(second.events()).extracting(Event::getTitle).containsExactly("E4", "E3", "E2");
        assertThat(second.hasPrevious()).isTrue();

        List<String> all = new ArrayList<>();
        String after = null;
        OrganizerEventFeed.Page page;
        do {
            page = mine(A, null, after, 4);
            page.events().forEach(e -> all.add(e.getTitle()));
            Event tail = page.events().get(page.events().size() - 1);
            after = KeysetCursor.encode(tail.getCreatedAt(), tail.getId());
        } while (page.hasNext());
        assertThat(all).hasSize(9).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("sees the organization's events, never another's, and nothing without a membership")
    void scope() {
        event(ORG_A, "Ours", EventStatus.PUBLISHED, 1, 10);
        event(ORG_B, "Theirs", EventStatus.PUBLISHED, 2, 10);

        assertThat(mine(A, null, null, 10).events()).extracting(Event::getTitle).containsExactly("Ours");
        assertThat(mine(TenantScope.of("both", Set.of(ORG_A, ORG_B)), null, null, 10).events()).hasSize(2);
        assertThat(mine(TenantScope.denyAll("nobody"), null, null, 10).events()).isEmpty();
        assertThat(mine(TenantScope.platformAdministrator("ops", Set.of()), null, null, 10).events())
                .as("an administrator's own list is their own organizations' events").isEmpty();
    }

    @Test
    @DisplayName("a soft-deleted event is not listed")
    void deletedHidden() {
        Event gone = event(ORG_A, "Gone", EventStatus.DRAFT, 1, 10);
        gone.setDeleted(true);
        template.save(gone).block();
        event(ORG_A, "Here", EventStatus.DRAFT, 2, 10);

        assertThat(mine(A, null, null, 10).events()).extracting(Event::getTitle).containsExactly("Here");
    }

    @Test
    @DisplayName("filters by status, statuses, text, venue text and start range")
    void filters() {
        event(ORG_A, "Jazz Night", EventStatus.PUBLISHED, 1, 10);
        event(ORG_A, "Rock Night", EventStatus.DRAFT, 2, 20);
        event(ORG_A, "Gala (VIP)", EventStatus.APPROVED, 3, 30);

        assertThat(titles(mine(A, filter(EventStatus.DRAFT, null, null, null, null), null, 10))).containsExactly("Rock Night");
        assertThat(titles(mine(A, filter(null, List.of(EventStatus.DRAFT, EventStatus.APPROVED), null, null, null), null, 10)))
                .containsExactly("Gala (VIP)", "Rock Night");
        assertThat(titles(mine(A, filter(null, null, "night", null, null), null, 10))).containsExactly("Rock Night", "Jazz Night");
        assertThat(titles(mine(A, filter(null, null, "mulungushi", null, null), null, 10))).hasSize(3);
        assertThat(titles(mine(A, filter(null, null, "(vip)", null, null), null, 10))).as("regex characters are only text")
                .containsExactly("Gala (VIP)");
        assertThat(titles(mine(A, filter(null, null, ".*", null, null), null, 10))).isEmpty();
        assertThat(titles(mine(A, filter(null, null, null, T0.plusSeconds(15 * 86_400L), T0.plusSeconds(25 * 86_400L)), null, 10)))
                .containsExactly("Rock Night");
        assertThat(titles(mine(A, filter(EventStatus.PUBLISHED, List.of(EventStatus.DRAFT), null, null, null), null, 10)))
                .as("statuses win over a single status").containsExactly("Rock Night");
    }

    @Test
    @DisplayName("a cursor the list did not issue is refused")
    void forged() {
        try {
            mine(A, null, "forged", 10);
            throw new AssertionError("expected a refusal");
        } catch (DomainRefusal refusal) {
            assertThat(refusal.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        }
    }

    @Test
    @DisplayName("the list is answered from an index, never a collection scan")
    void indexed() {
        Query query = Query.query(org.springframework.data.mongodb.core.query.Criteria.where("organizationId").in(Set.of(ORG_A)));
        Document explained = template.getCollection(com.pml.catalog.persistence.CatalogCollections.EVENTS)
                .flatMap(events -> Mono.from(events.find(query.getQueryObject())
                        .sort(new Document("createdAt", -1).append("_id", -1)).explain()))
                .block();

        String plan = explained.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        assertThat(plan).contains("IXSCAN").doesNotContain("COLLSCAN");
    }

    private static List<String> titles(OrganizerEventFeed.Page page) {
        return page.events().stream().map(Event::getTitle).toList();
    }
}
