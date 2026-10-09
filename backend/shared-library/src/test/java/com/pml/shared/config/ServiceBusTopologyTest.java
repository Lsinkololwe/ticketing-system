package com.pml.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.shared.event.EventType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The broker topology matches the event registry.
 *
 * <h2>Three things that fail silently if this drifts</h2>
 * <ul>
 *   <li><b>A missing subscription.</b> The publisher succeeds, the topic accepts the message,
 *       and the consumer that needed it simply never hears. Nothing errors anywhere.</li>
 *   <li><b>A missing or wrong SQL filter.</b> The service is woken by every message its
 *       neighbours publish and discards most of them. It works, and quietly spends the CPU the
 *       filter was designed to save — until a consumer mishandles a row it should never have
 *       been given.</li>
 *   <li><b>A subscription that is not session-enabled where the registry says it should be.</b> The
 *       ordered rows lose their guarantee, so a capacity change can overtake the tier creation
 *       it depends on, and the inventory ends up describing a tier that does not exist yet.</li>
 * </ul>
 *
 * <h2>Skips when the sibling repository is absent</h2>
 * {@code docker-resources} is a separate checkout. Failing here in a CI job that only has this
 * repository would train people to ignore the result.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R4 · Service Bus topology matches §4's registry")
class ServiceBusTopologyTest {

    private static final Path CONFIG =
            Path.of("../../../docker-resources/servicebus/config.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("every consumer of every §4 row has a subscription, filtered to exactly its rows")
    void subscriptionsAndFiltersMatchTheRegistry() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(CONFIG),
                "docker-resources is not checked out beside this repository — skipping");

        Map<String, Map<String, Set<String>>> configured = readTopology();
        Map<String, Map<String, Set<String>>> expected = expectedTopology();

        List<String> problems = new ArrayList<>();

        expected.forEach((topic, subs) -> subs.forEach((sub, wireNames) -> {
            Set<String> actual = configured.getOrDefault(topic, Map.of()).get(sub);
            if (actual == null) {
                problems.add("%s has no %s — its consumer never hears those events, and nothing errors"
                        .formatted(topic, sub));
                return;
            }
            if (!actual.equals(wireNames)) {
                TreeSet<String> missing = new TreeSet<>(wireNames);
                missing.removeAll(actual);
                TreeSet<String> extra = new TreeSet<>(actual);
                extra.removeAll(wireNames);
                problems.add("%s/%s filter: missing %s, extra %s".formatted(topic, sub, missing, extra));
            }
        }));

        configured.forEach((topic, subs) -> subs.keySet().forEach(sub -> {
            if (!expected.getOrDefault(topic, Map.of()).containsKey(sub)) {
                problems.add("%s/%s is configured but §4 gives it no rows".formatted(topic, sub));
            }
        }));

        assertThat(problems)
                .as("the broker and ET-PLT-003 §4 have to describe the same platform")
                .isEmpty();
    }

    @Test
    @DisplayName("a subscription carrying an ordered row is session-enabled")
    void orderedRowsGetSessions() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(CONFIG),
                "docker-resources is not checked out beside this repository — skipping");

        JsonNode namespace = MAPPER.readTree(Files.readString(CONFIG))
                .path("UserConfig").path("Namespaces").get(0);

        Map<String, Map<String, Set<String>>> expected = expectedTopology();
        Set<String> orderedWireNames = Arrays.stream(EventType.values())
                .filter(EventType::isOrdered)
                .map(EventType::wireName)
                .collect(Collectors.toSet());

        List<String> problems = new ArrayList<>();

        for (JsonNode topic : namespace.path("Topics")) {
            String topicName = topic.path("Name").asText();
            for (JsonNode subscription : topic.path("Subscriptions")) {
                String subName = subscription.path("Name").asText();
                Set<String> rows = expected.getOrDefault(topicName, Map.of())
                        .getOrDefault(subName, Set.of());
                boolean shouldHaveSessions = rows.stream().anyMatch(orderedWireNames::contains);
                boolean hasSessions = subscription.path("Properties").path("RequiresSession").asBoolean();

                if (shouldHaveSessions && !hasSessions) {
                    problems.add("%s/%s carries an ordered row but is not session-enabled"
                            .formatted(topicName, subName));
                }
                if (!shouldHaveSessions && hasSessions) {
                    problems.add(("%s/%s is session-enabled but carries no ordered row — every "
                            + "message then needs a session id for no benefit")
                            .formatted(topicName, subName));
                }
            }
        }

        assertThat(problems)
                .as("""
                    §4 buys ordering explicitly, per aggregate. Without the session the \
                    ordered rows have none, so a capacity change can overtake the tier creation \
                    it depends on.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every subscription dead-letters rather than redelivering forever")
    void deliveryIsBounded() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(CONFIG),
                "docker-resources is not checked out beside this repository — skipping");

        JsonNode namespace = MAPPER.readTree(Files.readString(CONFIG))
                .path("UserConfig").path("Namespaces").get(0);

        List<String> problems = new ArrayList<>();
        for (JsonNode topic : namespace.path("Topics")) {
            for (JsonNode subscription : topic.path("Subscriptions")) {
                JsonNode properties = subscription.path("Properties");
                String where = topic.path("Name").asText() + "/" + subscription.path("Name").asText();
                if (properties.path("MaxDeliveryCount").asInt(0) <= 0) {
                    problems.add(where + " has no delivery limit");
                }
                if (!properties.path("DeadLetteringOnMessageExpiration").asBoolean()) {
                    problems.add(where + " discards expired messages instead of dead-lettering them");
                }
            }
        }

        assertThat(problems)
                .as("a message that is redelivered forever is a consumer that never recovers and "
                        + "a queue that never drains")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    /** topic → subscription → the wire names its SQL filter admits. */
    private static Map<String, Map<String, Set<String>>> readTopology() throws IOException {
        Map<String, Map<String, Set<String>>> topology = new LinkedHashMap<>();
        JsonNode namespace = MAPPER.readTree(Files.readString(CONFIG))
                .path("UserConfig").path("Namespaces").get(0);

        for (JsonNode topic : namespace.path("Topics")) {
            Map<String, Set<String>> subs = new LinkedHashMap<>();
            for (JsonNode subscription : topic.path("Subscriptions")) {
                Set<String> admitted = new TreeSet<>();
                for (JsonNode rule : subscription.path("Rules")) {
                    String expression = rule.path("Properties").path("SqlFilter")
                            .path("SqlExpression").asText("");
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("eventType = '([^']+)'").matcher(expression);
                    while (m.find()) {
                        admitted.add(m.group(1));
                    }
                }
                subs.put(subscription.path("Name").asText(), admitted);
            }
            topology.put(topic.path("Name").asText(), subs);
        }
        return topology;
    }

    /**
     * Derived from the event registry's Consumers column, parsed from its markdown table.
     *
     * <p>Read rather than retyped: a hand-maintained copy is a third description of the topology
     * that can disagree with both the registry and the broker.</p>
     */
    private static Map<String, Map<String, Set<String>>> expectedTopology() throws IOException {
        Path spec = Path.of("../../specs/_platform/003-event-contract/spec.md");
        String section = Files.readString(spec)
                .split("### Cross-service event registry")[1]
                .split("### The publication shape")[0];

        java.util.regex.Matcher row = java.util.regex.Pattern.compile(
                        "\\|\\s*`((?:catalog|booking|identity)\\.\\w+)`\\s*\\|\\s*\\d+\\s*\\|([^|]*)\\|([^|]*)\\|")
                .matcher(section);

        Map<String, Map<String, Set<String>>> expected = new LinkedHashMap<>();
        while (row.find()) {
            String wireName = row.group(1);
            String consumers = row.group(3);
            String publisher = wireName.substring(0, wireName.indexOf('.'));
            String topic = publisher + "-events";

            for (String service : List.of("catalog", "booking", "identity")) {
                if (service.equals(publisher) || !consumers.matches("(?s).*\\b" + service + "\\b.*")) {
                    continue;
                }
                expected.computeIfAbsent(topic, k -> new LinkedHashMap<>())
                        .computeIfAbsent(service + "-sub", k -> new TreeSet<>())
                        .add(wireName);
            }
        }

        assertThat(expected).as("no rows parsed from §4 — the table format changed").isNotEmpty();
        return expected;
    }
}
