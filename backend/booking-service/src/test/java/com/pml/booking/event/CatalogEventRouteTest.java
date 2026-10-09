package com.pml.booking.event;

import com.pml.booking.workflow.finance.EventFinanceProcess;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Cancelled;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Completed;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Published;
import com.pml.booking.workflow.finance.EventFinanceWorkflow.Rescheduled;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each wire name booking consumes from {@code catalog-events} becomes the finance
 * workflow's signal with exactly the envelope's payload values, carrying its envelope id; a wire name
 * booking does not consume becomes nothing.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R3 · catalog envelopes become finance-workflow signals by wire name")
class CatalogEventRouteTest {

    private static final String EVENT = "event-route-probe";
    private static final String ORG = "org-route-probe";
    private static final Instant NOW = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-12-01T18:00:00Z");
    private static final Instant MOVED_TO = Instant.parse("2026-12-08T18:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-12-02T01:00:00Z");

    @Test
    @DisplayName("published, completed, cancelled and rescheduled each become their signal")
    void eachConsumedWireNameBecomesItsSignal() {
        EventEnvelope published = EventEnvelopes.of(EventType.CATALOG_EVENT_PUBLISHED, NOW, EVENT,
                Map.of("eventId", EVENT, "organizationId", ORG, "startsAt", STARTS_AT.toString()));
        EventEnvelope completed = EventEnvelopes.of(EventType.CATALOG_EVENT_COMPLETED, NOW, EVENT,
                Map.of("eventId", EVENT, "completedAt", COMPLETED_AT.toString()));
        EventEnvelope cancelled = EventEnvelopes.of(EventType.CATALOG_EVENT_CANCELLED, NOW, EVENT,
                Map.of("eventId", EVENT, "reason", "Venue flooded"));
        EventEnvelope rescheduled = EventEnvelopes.of(EventType.CATALOG_EVENT_RESCHEDULED, NOW, EVENT,
                Map.of("eventId", EVENT, "previousStartsAt", STARTS_AT.toString(), "newStartsAt", MOVED_TO.toString()));

        assertThat(EventFinanceProcess.factOf(published))
                .isEqualTo(new Published(published.eventId(), ORG, STARTS_AT.toEpochMilli()));
        assertThat(EventFinanceProcess.factOf(completed))
                .isEqualTo(new Completed(completed.eventId(), COMPLETED_AT.toEpochMilli()));
        assertThat(EventFinanceProcess.factOf(cancelled))
                .isEqualTo(new Cancelled(cancelled.eventId(), "Venue flooded"));
        assertThat(EventFinanceProcess.factOf(rescheduled))
                .isEqualTo(new Rescheduled(rescheduled.eventId(), STARTS_AT.toEpochMilli(), MOVED_TO.toEpochMilli()));
    }

    @Test
    @DisplayName("two messages about one event carry different envelope ids, so neither dedupes the other")
    void envelopeIdsDistinguishMessagesAboutOneEvent() {
        Object first = EventFinanceProcess.factOf(EventEnvelopes.of(EventType.CATALOG_EVENT_CANCELLED, NOW, EVENT,
                Map.of("eventId", EVENT, "reason", "Venue flooded")));
        Object second = EventFinanceProcess.factOf(EventEnvelopes.of(EventType.CATALOG_EVENT_CANCELLED, NOW, EVENT,
                Map.of("eventId", EVENT, "reason", "Venue flooded")));

        assertThat(((Cancelled) first).envelopeId()).isNotEqualTo(((Cancelled) second).envelopeId());
    }

    @Test
    @DisplayName("a §4 row booking does not consume becomes no signal")
    void anUnconsumedRowBecomesNothing() {
        assertThat(EventFinanceProcess.factOf(EventEnvelopes.of(EventType.CATALOG_TICKET_TIER_CAPACITY_CHANGED,
                NOW, EVENT, Map.of("tierId", "tier-1", "eventId", EVENT, "newCapacity", 100, "previousCapacity", 80))))
                .isNull();
    }
}
