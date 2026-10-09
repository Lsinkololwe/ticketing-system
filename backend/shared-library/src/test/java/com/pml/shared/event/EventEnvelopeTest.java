package com.pml.shared.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The envelope survives a round trip, for every wire name the event registry declares.
 *
 * <h2>The registry is parsed, not copied into the test</h2>
 * The event registry is closed. A list of names retyped here would be a second registry,
 * and the two would diverge the first time somebody adds a row to one of them — at which point
 * the test is enforcing a contract nobody is reading. Parsing the table means adding a row to
 * the registry and forgetting the enum fails this build.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R3 · every §4 event round-trips through the envelope")
class EventEnvelopeTest {

    private static final Path SPEC = Path.of("../../specs/_platform/003-event-contract/spec.md");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-18T09:15:30Z");

    @Test
    @DisplayName("the enum holds exactly the wire names §4 declares — no more, no fewer")
    void registryMatchesTheSpecification() throws IOException {
        String section = Files.readString(SPEC)
                .split("### Cross-service event registry")[1]
                .split("### The publication shape")[0];

        List<String> declared = new java.util.ArrayList<>();
        Matcher row = Pattern.compile("\\|\\s*`((?:catalog|booking|identity)\\.\\w+)`\\s*\\|").matcher(section);
        while (row.find()) {
            declared.add(row.group(1));
        }

        assertThat(declared).as("no rows parsed — §4's table format changed").isNotEmpty();

        assertThat(java.util.Arrays.stream(EventType.values()).map(EventType::wireName).toList())
                .as("""
                    §4 is a closed registry: "no other cross-service message exists". A name in \
                    the code that is not in §4 is a message no subscription filter matches, so \
                    it is published and silently discarded; a name in §4 with no constant is a \
                    message nobody can publish.""")
                .containsExactlyInAnyOrderElementsOf(declared);

        Matcher stated = Pattern.compile("\\*\\*(\\d+) wire names").matcher(section);
        assertThat(stated.find()).isTrue();
        assertThat(declared)
                .as("§4's table and its own stated total disagree")
                .hasSize(Integer.parseInt(stated.group(1)));
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    @DisplayName("round-trips with its §4 payload intact")
    void roundTripsForEveryEventType(EventType type) throws IOException {
        EventEnvelope original = EventEnvelopes.of(
                type, OCCURRED_AT, "correlation-1", "causation-1", samplePayloadFor(type));

        EventEnvelope restored = MAPPER.readValue(MAPPER.writeValueAsString(original), EventEnvelope.class);

        assertThat(restored.eventId()).isEqualTo(original.eventId());
        assertThat(restored.eventType()).isEqualTo(type.wireName());
        assertThat(restored.schemaVersion()).isEqualTo(1);
        assertThat(restored.correlationId()).isEqualTo("correlation-1");
        assertThat(restored.causationId()).isEqualTo("causation-1");
        assertThat(restored.sourceService()).isEqualTo(type.sourceService());

        assertThat(restored.occurredAt())
                .as("an Instant that loses precision in transit makes two events look simultaneous")
                .isEqualTo(OCCURRED_AT);

        assertThat(restored.payload().keySet())
                .as("every identifier §4 lists for %s must survive the trip", type.wireName())
                .containsExactlyInAnyOrderElementsOf(type.requiredKeys());
    }

    @Test
    @DisplayName("money survives as a decimal string, never as a float")
    void moneyDoesNotBecomeAFloat() throws IOException {
        EventEnvelope original = EventEnvelopes.of(
                EventType.BOOKING_PAYMENT_COMPLETED, OCCURRED_AT, "correlation-1",
                Map.of("paymentIntentId", "pi-1", "reservationId", "res-1", "userId", "u-1",
                        "amount", new BigDecimal("150.10"), "currency", "ZMW"));

        String json = MAPPER.writeValueAsString(original);

        // BigDecimal is serialised as a decimal string, never a float. 150.10 has no exact
        // binary representation, so a float round trip returns 150.09999... and the receipt
        // disagrees with the ledger by a hundredth of a kwacha that nobody can trace.
        assertThat(json)
                .as("money must not be written as a JSON number")
                .contains("\"150.10\"");

        Object amount = MAPPER.readValue(json, EventEnvelope.class).payload().get("amount");
        assertThat(new BigDecimal(amount.toString())).isEqualByComparingTo("150.10");
    }

    @Test
    @DisplayName("a payload missing a §4 identifier is refused at the publisher")
    void missingIdentifierIsRefused() {
        Map<String, Object> incomplete = new LinkedHashMap<>(
                samplePayloadFor(EventType.BOOKING_TICKET_PURCHASED));
        incomplete.remove("tierId");

        assertThatThrownBy(() -> EventEnvelopes.of(
                EventType.BOOKING_TICKET_PURCHASED, OCCURRED_AT, "correlation-1", incomplete))
                .as("""
                    Rejected here, this is one stack trace pointing at the publisher. Allowed \
                    onto the topic, it becomes a different failure in each of three subscribers \
                    at three different times.""")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tierId");
    }

    @Test
    @DisplayName("a payload carrying a key §4 does not list is refused")
    void unexpectedKeyIsRefused() {
        Map<String, Object> extra = new LinkedHashMap<>(
                samplePayloadFor(EventType.IDENTITY_ORGANIZATION_APPROVED));
        extra.put("ownerEmail", "someone@example.test");

        // The specific reason payloads are closed: an email in a payload is replicated into
        // every subscriber's dead-letter queue and every log line that dumps a message, and
        // any erasure obligation then reaches all of them.
        assertThatThrownBy(() -> EventEnvelopes.of(
                EventType.IDENTITY_ORGANIZATION_APPROVED, OCCURRED_AT, "correlation-1", extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerEmail");
    }

    @Test
    @DisplayName("a blank identifier is refused — it is worse than a missing one")
    void blankIdentifierIsRefused() {
        Map<String, Object> blank = new LinkedHashMap<>(
                samplePayloadFor(EventType.CATALOG_EVENT_CANCELLED));
        blank.put("reason", "  ");

        assertThatThrownBy(() -> EventEnvelopes.of(
                EventType.CATALOG_EVENT_CANCELLED, OCCURRED_AT, "correlation-1", blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    @DisplayName("a caused event inherits its cause's correlation")
    void causationPreservesTheChain() {
        EventEnvelope cause = EventEnvelopes.of(
                EventType.BOOKING_PAYMENT_COMPLETED, OCCURRED_AT, "correlation-1",
                samplePayloadFor(EventType.BOOKING_PAYMENT_COMPLETED));

        EventEnvelope effect = EventEnvelopes.causedBy(
                cause, EventType.BOOKING_TICKET_PURCHASED, OCCURRED_AT,
                samplePayloadFor(EventType.BOOKING_TICKET_PURCHASED));

        assertThat(effect.correlationId())
                .as("a fresh correlation mid-flow detaches the second half of a purchase from "
                        + "the first, and the gap only shows when someone is reconstructing an incident")
                .isEqualTo("correlation-1");
        assertThat(effect.causationId()).isEqualTo(cause.eventId());
    }

    @Test
    @DisplayName("the payload is copied, so a publisher cannot mutate a staged message")
    void payloadIsDefensivelyCopied() {
        Map<String, Object> mutable = new LinkedHashMap<>(
                samplePayloadFor(EventType.CATALOG_EVENT_CANCELLED));
        EventEnvelope envelope = EventEnvelopes.of(
                EventType.CATALOG_EVENT_CANCELLED, OCCURRED_AT, "correlation-1", mutable);

        mutable.put("reason", "changed after staging");

        assertThat(envelope.payload().get("reason")).isEqualTo("SOLD_OUT");
    }

    // --------------------------------------------------------------------- fixture

    /** A value for each key of the given type, typed as the registry requires. */
    private static Map<String, Object> samplePayloadFor(EventType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String key : type.requiredKeys()) {
            payload.put(key, switch (key) {
                case "capacity", "newCapacity", "previousCapacity", "quantity" -> 25;
                case "price", "amount", "netAmount" -> new BigDecimal("150.10");
                case "currency" -> "ZMW";
                case "startsAt", "salesStartAt", "salesEndAt", "newStartsAt",
                     "previousStartsAt", "completedAt" -> OCCURRED_AT;
                case "reason" -> "SOLD_OUT";
                case "failureCode" -> "INSUFFICIENT_FUNDS";
                case "eventRole" -> "SCANNER";
                case "previousRole" -> "MEMBER";
                case "newRole" -> "MANAGER";
                case "slug" -> "kabwata-arts";
                default -> key + "-value";
            });
        }
        return payload;
    }
}
