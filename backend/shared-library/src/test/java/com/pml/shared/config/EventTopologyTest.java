package com.pml.shared.config;

import com.pml.shared.event.EventTopology;
import com.pml.shared.event.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The broker topology matches the event registry.
 *
 * <h2>Three things that fail silently if this drifts</h2>
 * <ul>
 *   <li><b>A missing subscription.</b> The publisher succeeds, the exchange accepts the message,
 *       and the consumer that needed it simply never hears. Nothing errors anywhere.</li>
 *   <li><b>A wrong binding.</b> The service is woken by messages its neighbours publish and
 *       discards most of them, or never receives one it should — until a consumer mishandles a row it
 *       was never meant to be given.</li>
 *   <li><b>An ordered row on a concurrent subscription.</b> The ordering guarantee is lost, so a
 *       capacity change can overtake the tier creation it depends on, and the inventory ends up
 *       describing a tier that does not exist yet.</li>
 * </ul>
 *
 * <p>The registry is parsed from §4 rather than retyped: a hand-kept copy would be a third
 * description of the topology that can disagree with both the registry and the code.</p>
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R4 · the broker topology matches §4's registry")
class EventTopologyTest {

    private static final Path SPEC = Path.of("../../specs/_platform/003-event-contract/spec.md");

    @Test
    @DisplayName("every consumer of every §4 row has a subscription, bound to exactly its rows")
    void subscriptionsAndBindingsMatchTheRegistry() throws IOException {
        Map<String, Map<String, Set<String>>> expected = expectedTopology();

        Map<String, Map<String, Set<String>>> declared = new LinkedHashMap<>();
        for (EventTopology.Subscription subscription : EventTopology.SUBSCRIPTIONS) {
            declared.computeIfAbsent(subscription.exchange(), k -> new LinkedHashMap<>())
                    .put(subscription.group(), new TreeSet<>(subscription.wireNames()));
        }

        List<String> problems = new ArrayList<>();
        expected.forEach((exchange, groups) -> groups.forEach((group, wireNames) -> {
            Set<String> actual = declared.getOrDefault(exchange, Map.of()).get(group);
            if (actual == null) {
                problems.add("%s has no %s — its consumer never hears those events, and nothing errors"
                        .formatted(exchange, group));
            } else if (!actual.equals(wireNames)) {
                TreeSet<String> missing = new TreeSet<>(wireNames);
                missing.removeAll(actual);
                TreeSet<String> extra = new TreeSet<>(actual);
                extra.removeAll(wireNames);
                problems.add("%s/%s bindings: missing %s, extra %s".formatted(exchange, group, missing, extra));
            }
        }));
        declared.forEach((exchange, groups) -> groups.keySet().forEach(group -> {
            if (!expected.getOrDefault(exchange, Map.of()).containsKey(group)) {
                problems.add("%s/%s is declared but §4 gives it no rows".formatted(exchange, group));
            }
        }));

        assertThat(problems).as("the broker and ET-PLT-003 §4 have to describe the same platform").isEmpty();
    }

    @Test
    @DisplayName("a subscription carrying an ordered row has a single active consumer, and only then")
    void orderedRowsGetOrdering() {
        Set<String> orderedWireNames = java.util.Arrays.stream(EventType.values())
                .filter(EventType::isOrdered).map(EventType::wireName).collect(Collectors.toSet());

        List<String> problems = new ArrayList<>();
        for (EventTopology.Subscription subscription : EventTopology.SUBSCRIPTIONS) {
            boolean carriesOrdered = subscription.wireNames().stream().anyMatch(orderedWireNames::contains);
            if (carriesOrdered != subscription.ordered()) {
                problems.add(subscription.queue() + " ordering disagrees with the rows it carries");
            }
        }
        assertThat(problems).isEmpty();
        assertThat(EventTopology.SUBSCRIPTIONS.stream().filter(EventTopology.Subscription::ordered).count())
                .as("ordering is the exception: some subscriptions are ordered and some are not")
                .isBetween(1L, (long) EventTopology.SUBSCRIPTIONS.size() - 1);
    }

    @Test
    @DisplayName("every subscription has a dead-letter exchange, queue and a bound on both")
    void deliveryIsBounded() {
        for (EventTopology.Subscription subscription : EventTopology.SUBSCRIPTIONS) {
            assertThat(subscription.deadLetterQueue()).isEqualTo(subscription.queue() + ".dlq");
            assertThat(subscription.deadLetterExchange()).isEqualTo(subscription.exchange() + ".dlx");
        }
        assertThat(EventTopology.MESSAGE_TTL_MILLIS)
                .as("a message nobody takes is dead-lettered, not kept forever").isPositive();
        assertThat(EventTopology.DEAD_LETTER_LIMIT)
                .as("a dead-letter queue nobody drains cannot grow without limit").isPositive();
    }

    @Test
    @DisplayName("every exchange a subscription binds is an exchange the platform declares")
    void everySubscriptionHasItsExchange() {
        for (EventTopology.Subscription subscription : EventTopology.SUBSCRIPTIONS) {
            assertThat(EventTopology.EXCHANGES).contains(subscription.exchange());
        }
    }

    /** topic → subscription → the wire names §4's Consumers column sends it. */
    private static Map<String, Map<String, Set<String>>> expectedTopology() throws IOException {
        String section = Files.readString(SPEC)
                .split("### Cross-service event registry")[1]
                .split("### The publication shape")[0];

        Matcher row = Pattern.compile(
                        "\\|\\s*`((?:catalog|booking|identity)\\.\\w+)`\\s*\\|\\s*\\d+\\s*\\|([^|]*)\\|([^|]*)\\|")
                .matcher(section);

        Map<String, Map<String, Set<String>>> expected = new LinkedHashMap<>();
        while (row.find()) {
            String wireName = row.group(1);
            String consumers = row.group(3);
            String publisher = wireName.substring(0, wireName.indexOf('.'));
            String exchange = publisher + "-events";

            for (String service : List.of("catalog", "booking", "identity")) {
                if (service.equals(publisher) || !consumers.matches("(?s).*\\b" + service + "\\b.*")) {
                    continue;
                }
                expected.computeIfAbsent(exchange, k -> new LinkedHashMap<>())
                        .computeIfAbsent(service + "-sub", k -> new TreeSet<>())
                        .add(wireName);
            }
        }

        assertThat(expected).as("no rows parsed from §4 — the table format changed").isNotEmpty();
        return expected;
    }
}
