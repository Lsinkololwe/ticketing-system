package com.pml.catalog.service;

import com.pml.catalog.domain.enums.EventDiscoverySort;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.valueobject.RunningOrderItem;
import com.pml.catalog.web.graphql.dto.CheckoutSettingsInput;
import com.pml.catalog.web.graphql.dto.EventFaqInput;
import com.pml.catalog.web.graphql.dto.RunningOrderItemInput;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.shared.error.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The rules for what an organizer says about an event, with no database. */
@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R2/R4 · event page content is bounded, and each feed order is a stable sort")
class EventContentRulesTest {

    private static final Instant STARTS = Instant.parse("2026-12-01T18:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private static Event event() {
        return Event.builder().eventDateTime(STARTS).endDateTime(STARTS.plusSeconds(3 * 3600)).build();
    }

    private static List<String> paths(Event event) {
        return EventDetails.check(event, true).stream().map(FieldViolation::path).toList();
    }

    private static UpdateEventInput update(String tagline, List<EventFaqInput> faqs, List<RunningOrderItemInput> order,
                                           CheckoutSettingsInput checkout) {
        return new UpdateEventInput(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, tagline, null, null, null, faqs, order, null, null, null,
                checkout, null);
    }

    @Test
    @DisplayName("an event with none of the new content is valid")
    void emptyIsValid() {
        assertThat(paths(event())).isEmpty();
    }

    @Test
    @DisplayName("the age restriction is one of the five the validator accepts")
    void ageRestriction() {
        for (String ok : List.of("ALL_AGES", "13+", "16+", "18+", "21+")) {
            Event e = event();
            e.setAgeRestriction(ok);
            assertThat(paths(e)).as(ok).isEmpty();
        }
        Event bad = event();
        bad.setAgeRestriction("adults only");
        assertThat(paths(bad)).containsExactly("ageRestriction");
        assertThat(EventDetails.AGE_RESTRICTIONS).containsExactlyInAnyOrder("ALL_AGES", "13+", "16+", "18+", "21+");
    }

    @Test
    @DisplayName("doors open no later than the start, and the go-live time is before the start")
    void times() {
        Event late = event();
        late.setDoorsOpenAt(STARTS.plusSeconds(60));
        late.setPublishAt(STARTS);
        assertThat(paths(late)).containsExactlyInAnyOrder("doorsOpenAt", "publishAt");

        Event fine = event();
        fine.setDoorsOpenAt(STARTS);
        fine.setPublishAt(STARTS.minusSeconds(1));
        assertThat(paths(fine)).isEmpty();
    }

    @Test
    @DisplayName("a go-live time that has passed is refused when the organizer sets it")
    void publishAtMustBeAhead() {
        assertThat(EventDetails.checkPublishAt(NOW.minusSeconds(1), NOW)).extracting(FieldViolation::path).containsExactly("publishAt");
        assertThat(EventDetails.checkPublishAt(NOW, NOW)).hasSize(1);
        assertThat(EventDetails.checkPublishAt(NOW.plusSeconds(1), NOW)).isEmpty();
        assertThat(EventDetails.checkPublishAt(null, NOW)).isEmpty();
    }

    @Test
    @DisplayName("an update copies what was sent, trims it, and leaves the rest alone")
    void updateCopies() {
        Event e = event();
        e.setTagline("old");
        e.setGettingThere("Bus 12");

        EventDetails.apply(e, update("  New tagline  ",
                List.of(new EventFaqInput(" Is parking free? ", " Yes. ")),
                List.of(new RunningOrderItemInput("19:00", " Doors "), new RunningOrderItemInput("20:30", "Headliner")),
                new CheckoutSettingsInput(6, true, " Dietary needs? ")));

        assertThat(e.getTagline()).isEqualTo("New tagline");
        assertThat(e.getFaqs()).singleElement().satisfies(faq -> {
            assertThat(faq.question()).isEqualTo("Is parking free?");
            assertThat(faq.answer()).isEqualTo("Yes.");
        });
        assertThat(e.getRunningOrder()).extracting(RunningOrderItem::time).containsExactly("19:00", "20:30");
        assertThat(e.getRunningOrder().get(0).title()).isEqualTo("Doors");
        assertThat(e.getCheckoutSettings().maxTicketsPerOrder()).isEqualTo(6);
        assertThat(e.getCheckoutSettings().collectHolderNames()).isTrue();
        assertThat(e.getCheckoutSettings().extraQuestion()).isEqualTo("Dietary needs?");
        assertThat(e.getGettingThere()).as("not sent, not changed").isEqualTo("Bus 12");
    }

    @Test
    @DisplayName("an empty list clears FAQs; null leaves them")
    void emptyClears() {
        Event e = event();
        EventDetails.apply(e, update(null, List.of(new EventFaqInput("Q?", "A.")), null, null));
        assertThat(e.getFaqs()).hasSize(1);

        EventDetails.apply(e, update(null, null, null, null));
        assertThat(e.getFaqs()).as("null leaves them").hasSize(1);

        EventDetails.apply(e, update(null, List.of(), null, null));
        assertThat(e.getFaqs()).as("empty clears them").isEmpty();
    }

    @Test
    @DisplayName("too many FAQs or running-order lines, or a long tagline, are refused")
    void bounds() {
        Event e = event();
        e.setFaqs(java.util.Collections.nCopies(21, new com.pml.catalog.domain.valueobject.EventFaq("Q", "A")));
        e.setRunningOrder(java.util.Collections.nCopies(41, new RunningOrderItem("10:00", "x")));
        e.setTagline("t".repeat(101));

        assertThat(paths(e)).containsExactlyInAnyOrder("faqs", "runningOrder", "tagline");
    }

    @Test
    @DisplayName("every feed order breaks ties by id, so a page boundary never repeats or skips an event")
    void ordersEndWithTheId() {
        for (EventDiscoverySort order : EventDiscoverySort.values()) {
            Sort sort = EventDiscovery.orderOf(order);
            assertThat(sort.stream().reduce((first, second) -> second).orElseThrow().getProperty())
                    .as(order.name()).isEqualTo("_id");
        }
        assertThat(EventDiscovery.orderOf(null)).isEqualTo(EventDiscovery.orderOf(EventDiscoverySort.SOONEST));
        assertThat(EventDiscovery.orderOf(EventDiscoverySort.POPULAR).getOrderFor("soldTickets").isDescending()).isTrue();
        assertThat(EventDiscovery.orderOf(EventDiscoverySort.PRICE_DESC).getOrderFor("lowestTicketPrice").isDescending()).isTrue();
        assertThat(EventDiscovery.orderOf(EventDiscoverySort.PRICE_ASC).getOrderFor("lowestTicketPrice").isAscending()).isTrue();
        assertThat(EventDiscovery.orderOf(EventDiscoverySort.NEWEST).getOrderFor("publishedAt").isDescending()).isTrue();
    }
}
