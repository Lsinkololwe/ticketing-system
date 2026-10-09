package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.enums.RecommendationReason;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.EventRecommendation;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What is selling, and what a buyer who holds tickets might like, against a real MongoDB. */
@Tag("L2")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R5/R12 · trending and recommendations rank upcoming published events by tickets sold")
class EventRankingTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ORG = "65a1b2c3d4e5f60718293a4b";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventRanking ranking;
    private static OrganizerProfileService profiles;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_event_ranking");
        ranking = new EventRanking(template, CLOCK);
        profiles = new OrganizerProfileService(template, CLOCK);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), Event.class).block();
    }

    private static Event event(String title, String category, int sold, int startsInDays, EventStatus status) {
        Instant starts = NOW.plusSeconds(startsInDays * 86_400L);
        return template.save(Event.builder().organizationId(ORG).organizerId("o").title(title).categoryId(category)
                .status(status).published(status == EventStatus.PUBLISHED || status == EventStatus.COMPLETED)
                .isActive(true).soldTickets(sold).totalCapacity(500)
                .eventDateTime(starts).endDateTime(starts.plusSeconds(4 * 3600)).createdAt(NOW).build()).block();
    }

    private static Event live(String title, String category, int sold, int startsInDays) {
        return event(title, category, sold, startsInDays, EventStatus.PUBLISHED);
    }

    private static List<String> titles(List<Event> events) {
        return events.stream().map(Event::getTitle).toList();
    }

    @Test
    @DisplayName("trending is the upcoming published events, most sold first, soonest breaking a tie")
    void trending() {
        live("Quiet", "MUSIC", 3, 5);
        live("Hot", "MUSIC", 400, 20);
        live("Warm B", "SPORTS", 90, 30);
        live("Warm A", "SPORTS", 90, 10);

        assertThat(titles(ranking.trending(10).collectList().block())).containsExactly("Hot", "Warm A", "Warm B", "Quiet");
        assertThat(titles(ranking.trending(2).collectList().block())).containsExactly("Hot", "Warm A");
    }

    @Test
    @DisplayName("trending leaves out drafts, ended events, cancelled events and soft-deleted ones")
    void trendingIsPublic() {
        live("Shown", "MUSIC", 1, 5);
        event("Draft", "MUSIC", 900, 5, EventStatus.DRAFT);
        event("Approved", "MUSIC", 900, 5, EventStatus.APPROVED);
        live("Already over", "MUSIC", 900, -3);
        Event cancelled = event("Cancelled", "MUSIC", 900, 5, EventStatus.CANCELLED);
        Event deleted = live("Deleted", "MUSIC", 900, 5);
        deleted.setDeleted(true);
        template.save(deleted).block();
        Event inactive = live("Inactive", "MUSIC", 900, 5);
        inactive.setActive(false);
        template.save(inactive).block();

        assertThat(cancelled.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(titles(ranking.trending(10).collectList().block())).containsExactly("Shown");
    }

    @Test
    @DisplayName("the page size is bounded and refused, not clamped")
    void bounds() {
        for (int bad : new int[]{0, -1, 51}) {
            try {
                ranking.trending(bad);
                throw new AssertionError("expected a refusal for " + bad);
            } catch (DomainRefusal refusal) {
                assertThat(refusal.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
            }
        }
    }

    @Test
    @DisplayName("recommendations lead with the categories of the events the buyer holds, each naming the event it resembles")
    void becauseYouBooked() {
        Event heldMusic = live("Held music", "MUSIC", 50, 7);
        Event heldSports = live("Held sports", "SPORTS", 50, 8);
        live("Music hit", "MUSIC", 300, 12);
        live("Music small", "MUSIC", 10, 12);
        live("Sports mid", "SPORTS", 120, 14);
        live("Food hit", "FOOD", 800, 9);

        List<EventRecommendation> found = ranking.recommended(List.of(heldMusic.getId(), heldSports.getId()), 10).block();

        assertThat(found).extracting(r -> r.event().getTitle())
                .containsExactly("Music hit", "Sports mid", "Music small", "Food hit");
        assertThat(found).extracting(EventRecommendation::reason).containsExactly(
                RecommendationReason.BECAUSE_YOU_BOOKED, RecommendationReason.BECAUSE_YOU_BOOKED,
                RecommendationReason.BECAUSE_YOU_BOOKED, RecommendationReason.TRENDING);
        assertThat(found.get(0).basedOnEventId()).isEqualTo(heldMusic.getId());
        assertThat(found.get(1).basedOnEventId()).isEqualTo(heldSports.getId());
        assertThat(found.get(3).basedOnEventId()).isNull();
    }

    @Test
    @DisplayName("never an event the buyer already holds")
    void neverTheSame() {
        Event held = live("Held", "MUSIC", 999, 7);
        live("Other", "MUSIC", 1, 9);

        assertThat(ranking.recommended(List.of(held.getId()), 10).block()).extracting(r -> r.event().getTitle())
                .containsExactly("Other");
    }

    @Test
    @DisplayName("with nothing named, or nothing in the category, it is trending")
    void fallsBackToTrending() {
        live("A", "MUSIC", 5, 5);
        live("B", "SPORTS", 50, 5);

        List<EventRecommendation> none = ranking.recommended(null, 10).block();
        assertThat(none).extracting(r -> r.event().getTitle()).containsExactly("B", "A");
        assertThat(none).extracting(EventRecommendation::reason).containsOnly(RecommendationReason.TRENDING);

        Event lonely = live("Lonely category", "RARE", 1, 5);
        List<EventRecommendation> only = ranking.recommended(List.of(lonely.getId()), 10).block();
        assertThat(only).extracting(r -> r.event().getTitle()).containsExactly("B", "A");
    }

    @Test
    @DisplayName("an unpublished event contributes no category, so a stranger's draft cannot be probed for its category")
    void unpublishedBasisIsIgnored() {
        Event secret = event("Secret draft", "SECRET_CATEGORY", 0, 30, EventStatus.DRAFT);
        live("Secret category lookalike", "SECRET_CATEGORY", 77, 5);
        live("Plain", "MUSIC", 10, 5);

        List<EventRecommendation> found = ranking.recommended(List.of(secret.getId()), 10).block();

        assertThat(found).extracting(EventRecommendation::reason).containsOnly(RecommendationReason.TRENDING);
        assertThat(found).extracting(r -> r.event().getTitle()).containsExactly("Secret category lookalike", "Plain");
    }

    @Test
    @DisplayName("a result is filled to the size asked, and no more")
    void filled() {
        Event held = live("Held", "MUSIC", 1, 1);
        for (int i = 0; i < 8; i++) {
            live("M" + i, "MUSIC", 10 + i, 10 + i);
        }
        for (int i = 0; i < 8; i++) {
            live("S" + i, "SPORTS", 100 + i, 10 + i);
        }

        List<EventRecommendation> found = ranking.recommended(List.of(held.getId()), 12).block();

        assertThat(found).hasSize(12);
        assertThat(found.subList(0, 8)).allSatisfy(r -> assertThat(r.reason()).isEqualTo(RecommendationReason.BECAUSE_YOU_BOOKED));
        assertThat(found.subList(8, 12)).allSatisfy(r -> assertThat(r.reason()).isEqualTo(RecommendationReason.TRENDING));
    }

    @Test
    @DisplayName("more than twenty events as a basis is refused")
    void basisBound() {
        List<String> many = new ArrayList<>(Collections.nCopies(21, "x"));
        for (int i = 0; i < many.size(); i++) {
            many.set(i, "65a1b2c3d4e5f60718293a" + String.format("%02x", i));
        }

        try {
            ranking.recommended(many, 10).block();
            throw new AssertionError("expected a refusal");
        } catch (DomainRefusal refusal) {
            assertThat(refusal.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        }
    }

    @Test
    @DisplayName("an organization's profile counts its live events and the ones it finished, and nobody else's")
    void profileCounts() {
        live("Live 1", "MUSIC", 0, 5);
        live("Live 2", "MUSIC", 0, 6);
        live("Over", "MUSIC", 0, -3);
        event("Draft", "MUSIC", 0, 5, EventStatus.DRAFT);
        event("Done 1", "MUSIC", 0, -10, EventStatus.COMPLETED);
        event("Done 2", "MUSIC", 0, -20, EventStatus.COMPLETED);
        event("Cancelled", "MUSIC", 0, -5, EventStatus.CANCELLED);
        template.save(Event.builder().organizationId("65a1b2c3d4e5f60718293aaa").organizerId("o").title("Elsewhere")
                .status(EventStatus.PUBLISHED).published(true).isActive(true).totalCapacity(1)
                .eventDateTime(NOW.plusSeconds(86_400)).endDateTime(NOW.plusSeconds(90_000)).createdAt(NOW).build()).block();

        assertThat(profiles.publishedEventCount(ORG).block()).isEqualTo(2);
        assertThat(profiles.completedEventCount(ORG).block()).isEqualTo(2);
        assertThat(profiles.publishedEventCount(null).block()).isZero();
        assertThat(profiles.completedEventCount("65a1b2c3d4e5f60718293bbb").block()).isZero();
    }
}
