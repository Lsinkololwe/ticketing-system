package com.pml.shared.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The platform's half of ordering: which key serialises which row.
 *
 * <h2>Why this exists alongside the emulator test</h2>
 * {@code SessionOrderingTest} proves what the broker does, and skips wherever the emulator is
 * not reachable — which is most CI jobs and, on this machine, all of them. The decision it
 * depends on is made here, in code, from the event registry's <em>Ordering</em> column: which rows get an
 * aggregate id as their session and which get a unique one. That decision is checkable without
 * a broker and must not be allowed to drift, because every symptom of getting it wrong is
 * silent.
 *
 * <h2>The two silent failures</h2>
 * <ul>
 *   <li><b>An ordered row treated as commutative.</b> Its messages spread across sessions and a
 *       capacity change overtakes the tier creation it depends on. Inventory then describes a
 *       tier that does not exist, and nothing errors.</li>
 *   <li><b>A commutative row given a shared session.</b> Ordering is bought where it was not
 *       needed, and the subscription serialises traffic that had no reason to wait — the "queue
 *       of one" that only shows up under on-sale load.</li>
 * </ul>
 *
 * <p>The registry is parsed rather than retyped: a hand-kept copy is a third description of
 * ordering that can disagree with both the registry and the code.</p>
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R7 · session keys match §4's Ordering column")
class SessionKeyTest {

    private static final Path SPEC =
            Path.of("../../specs/_platform/003-event-contract/spec.md");

    @Test
    @DisplayName("every §4 row's ordering decision is the one the code makes")
    void sessionKeysMatchTheRegistry() throws IOException {
        Map<String, Optional<String>> registry = orderingFromSpec();

        assertThat(registry).as("no rows parsed from §4 — the table format changed").isNotEmpty();

        List<String> problems = new ArrayList<>();
        registry.forEach((wireName, expectedKey) -> {
            Optional<EventType> type = EventType.ofWireName(wireName);
            if (type.isEmpty()) {
                problems.add(wireName + " is in §4 but not in EventType");
                return;
            }
            Optional<String> actualKey = type.get().sessionKey();
            if (!actualKey.equals(expectedKey)) {
                problems.add("%s — §4 says %s, code says %s".formatted(
                        wireName,
                        expectedKey.map("session: %s"::formatted).orElse("commutative"),
                        actualKey.map("session: %s"::formatted).orElse("commutative")));
            }
        });

        assertThat(problems)
                .as("""
                    §4's Ordering column is the justification R7 requires for every \
                    session-enabled subscription. Code that disagrees with it has either bought \
                    ordering nobody asked for or lost ordering somebody depends on.""")
                .isEmpty();
    }

    /**
     * Both counts are derived — one from the registry table, one from the enum — and compared to each
     * other rather than to a literal. A literal here would be a third, independent claim about
     * how many rows are ordered, free to disagree with the table it is supposed to describe;
     * the corpus's prose already carries such counts and they drift. The only fixed assertion is
     * the property that matters: ordering is the exception.
     */
    @Test
    @DisplayName("the ordered rows are the exception, and code and §4 agree how many there are")
    void theOrderedRowsAreTheExceptionNotTheRule() throws IOException {
        long fromSpec = orderingFromSpec().values().stream().filter(Optional::isPresent).count();
        long fromCode = Arrays.stream(EventType.values()).filter(EventType::isOrdered).count();

        assertThat(fromCode)
                .as("R7: session-enabled subscriptions are the exception, each justified in §4")
                .isEqualTo(fromSpec);

        // Doubled rather than halved: integer division would let exactly half pass as "fewer".
        assertThat(fromCode * 2)
                .as("if most rows were ordered, the platform would be buying a queue of one and "
                        + "calling it a bus")
                .isLessThan(EventType.values().length);
    }

    // ------------------------------------------------------------------ what goes on the wire

    @Test
    @DisplayName("an ordered row is sent under its aggregate's id")
    void orderedRowUsesTheAggregateId() {
        Message<?> sent = publish(EventType.CATALOG_TICKET_TIER_CAPACITY_CHANGED.wireName(),
                Map.of("tierId", "tier-77", "eventId", "ev-1",
                        "newCapacity", 200, "previousCapacity", 100));

        assertThat(sent.getHeaders().get(EventBridge.SESSION_ID_HEADER))
                .as("Service Bus serialises on this key, so it has to be the entity whose "
                        + "sequence matters — not the message, and not the event")
                .isEqualTo("tier-77");
    }

    @Test
    @DisplayName("a commutative row is sent under a session of its own, not a shared one")
    void commutativeRowGetsAUniqueSession() {
        Message<?> first = publish(EventType.BOOKING_PAYMENT_COMPLETED.wireName(),
                Map.of("paymentIntentId", "pi-1", "reservationId", "r-1",
                        "userId", "u-1", "amount", "350.00", "currency", "ZMW"));
        Message<?> second = publish(EventType.BOOKING_PAYMENT_COMPLETED.wireName(),
                Map.of("paymentIntentId", "pi-2", "reservationId", "r-2",
                        "userId", "u-2", "amount", "120.00", "currency", "ZMW"));

        Object firstSession = first.getHeaders().get(EventBridge.SESSION_ID_HEADER);
        Object secondSession = second.getHeaders().get(EventBridge.SESSION_ID_HEADER);

        assertThat(firstSession)
                .as("""
                    A session-enabled subscription rejects a message with no session id, and four \
                    of the six §4 subscriptions carry a mix of ordered and commutative rows. So \
                    every message needs one.""")
                .isNotNull();
        assertThat(firstSession)
                .as("""
                    ...but it must be unique, or two unrelated payments serialise behind each \
                    other. A constant here would pass every ordering test and turn the \
                    subscription into a queue of one.""")
                .isNotEqualTo(secondSession);
    }

    @Test
    @DisplayName("two entities of the same ordered row get different sessions")
    void twoEntitiesDoNotShareASession() {
        Message<?> tierA = publish(EventType.CATALOG_TICKET_TIER_PUBLISHED.wireName(),
                tierPayload("tier-A"));
        Message<?> tierB = publish(EventType.CATALOG_TICKET_TIER_PUBLISHED.wireName(),
                tierPayload("tier-B"));

        assertThat(tierA.getHeaders().get(EventBridge.SESSION_ID_HEADER))
                .as("ordering per entity is what stops one slow tier holding up every other one")
                .isNotEqualTo(tierB.getHeaders().get(EventBridge.SESSION_ID_HEADER));
    }

    @Test
    @DisplayName("an ordered row missing its session key is refused, not sent under 'null'")
    void missingSessionKeyIsRefused() {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);
        EventBridge bridge = new EventBridge(streamBridge, "catalogEvents-out-0");

        // tierId absent — the key that serialises this row.
        EventEnvelope envelope = new EventEnvelope("evt-1",
                EventType.CATALOG_TICKET_TIER_CAPACITY_CHANGED.wireName(), 1,
                Instant.parse("2026-08-19T09:00:00Z"), "corr-1", null, "catalog",
                Map.of("eventId", "ev-1", "newCapacity", 200, "previousCapacity", 100));

        assertThat(catchFailure(() -> bridge.publish(envelope).block()))
                .as("""
                    String.valueOf(null) is "null", a perfectly valid session id. Every ordered \
                    message missing its key would then land in one session called "null" — \
                    serialised against each other and against nothing else. The broker accepts \
                    it, the send succeeds, and the ordering guarantee is quietly inverted.""")
                .isNotNull()
                .hasMessageContaining("tierId");
    }

    // --------------------------------------------------------------------- helpers

    private static Throwable catchFailure(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static Map<String, Object> tierPayload(String tierId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tierId", tierId);
        payload.put("eventId", "ev-1");
        payload.put("capacity", 500);
        payload.put("price", "150.00");
        payload.put("currency", "ZMW");
        payload.put("salesStartAt", "2026-09-01T08:00:00Z");
        payload.put("salesEndAt", "2026-09-30T08:00:00Z");
        return payload;
    }

    /** Publishes through a mocked {@link StreamBridge} and returns the message it was handed. */
    private static Message<?> publish(String wireName, Map<String, Object> payload) {
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(true);

        new EventBridge(streamBridge, "test-out-0")
                .publish(new EventEnvelope("evt-" + wireName + "-" + payload.hashCode(), wireName, 1,
                        Instant.parse("2026-08-19T09:00:00Z"), "corr-1", null,
                        wireName.substring(0, wireName.indexOf('.')), payload))
                .block();

        ArgumentCaptor<Message<?>> captured = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(anyString(), captured.capture());
        return captured.getValue();
    }

    /** Wire name → the session key the Ordering column names, or empty for commutative. */
    private static Map<String, Optional<String>> orderingFromSpec() throws IOException {
        String section = Files.readString(SPEC)
                .split("### Cross-service event registry")[1]
                .split("### The publication shape")[0];

        Matcher row = Pattern.compile(
                        "\\|\\s*`((?:catalog|booking|identity)\\.\\w+)`\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|([^|]*)\\|")
                .matcher(section);

        Map<String, Optional<String>> ordering = new LinkedHashMap<>();
        while (row.find()) {
            Matcher key = Pattern.compile("session:\\s*`(\\w+)`").matcher(row.group(2));
            ordering.put(row.group(1), key.find() ? Optional.of(key.group(1)) : Optional.empty());
        }
        return ordering;
    }
}
