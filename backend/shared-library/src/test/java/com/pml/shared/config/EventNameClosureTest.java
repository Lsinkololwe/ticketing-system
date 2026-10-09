package com.pml.shared.config;

import com.pml.shared.event.EventType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every event somebody waits for is an event somebody sends.
 *
 * <h2>The property no single service can check</h2>
 * A subscriber declares what it consumes; a publisher declares what it publishes. Each spec is
 * internally consistent and reviewable on its own, and the interesting failure lives only in the
 * pair: a consumer subscribed to a name that no publisher ever emits. Nothing errors. The
 * subscription exists, the binder is happy, the handler is registered, and it is simply never
 * called — so the feature is quietly missing rather than broken, and it stays that way until
 * somebody notices a counter that never moves.
 *
 * <p>The inverse costs less but is worth knowing: a name published to nobody is either dead
 * weight on the bus or a consumer that was planned and never written.</p>
 *
 * <h2>Why this reads the corpus and not the code</h2>
 * The services do not yet implement every consumer, so a code-level check would pass by being
 * empty. {@code spec.yaml}'s {@code events.bus} and {@code events.consumes} are the declarations
 * that will drive that implementation, which makes the corpus the place the closure can be
 * broken and the place it has to hold.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-TS5 · the event name closure holds across the corpus")
class EventNameClosureTest {

    private static final Path SPECS = Path.of("../../specs");

    @Test
    @DisplayName("every consumed wire name is published by some spec")
    void everyConsumedNameHasAPublisher() throws IOException {
        Corpus corpus = readCorpus();

        Set<String> orphans = new TreeSet<>(corpus.consumed.keySet());
        orphans.removeAll(corpus.published.keySet());

        assertThat(orphans)
                .as(() -> """
                    These names are subscribed to and never sent. The subscription is created, \
                    the handler is registered, and it is never called — nothing errors, and the \
                    feature is missing rather than broken. Waiting specs: %s"""
                        .formatted(orphans.stream()
                                .map(name -> name + " ← " + corpus.consumed.get(name))
                                .collect(Collectors.joining("; "))))
                .isEmpty();
    }

    /**
     * Three published names the closed event registry does not carry.
     *
     * <p>The registry is meant to be the complete list of cross-service messages. These three are
     * each a real cross-service message all the same:</p>
     *
     * <ul>
     *   <li>{@code identity.TokenRevoked} — invalidates each service's local token cache. Without
     *       it a revoked token stays usable until it expires.</li>
     *   <li>{@code identity.RolePermissionsChanged} — invalidates the permission cache. The
     *       registry carries {@code MemberRoleChanged} for the same purpose at member granularity,
     *       so this is the role-level sibling.</li>
     *   <li>{@code catalog.ReferenceDataChanged} — invalidates cached reference data.</li>
     * </ul>
     *
     * <p>This set is frozen and may only shrink. Either the registry grows to hold these rows or
     * their publishers stop publishing them, and whichever it is, it is a decision about the closed
     * registry rather than something a test should quietly absorb. A <b>fourth</b> invented name
     * fails here immediately, which is the property worth keeping while the three are settled.</p>
     */
    private static final Set<String> KNOWN_OUTSIDE_THE_REGISTRY = Set.of(
            "identity.TokenRevoked",
            "identity.RolePermissionsChanged",
            "catalog.ReferenceDataChanged");

    @Test
    @DisplayName("no spec invents a wire name beyond the three §4 already contradicts")
    void noSpecInventsAWireName() throws IOException {
        Corpus corpus = readCorpus();

        Set<String> registry = Arrays.stream(EventType.values())
                .map(EventType::wireName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> unknown = new TreeSet<>();
        Stream.concat(corpus.published.keySet().stream(), corpus.consumed.keySet().stream())
                .filter(name -> !registry.contains(name))
                .filter(name -> !KNOWN_OUTSIDE_THE_REGISTRY.contains(name))
                .forEach(unknown::add);

        assertThat(unknown)
                .as("§4 calls the registry closed. A spec naming a row outside it has either "
                        + "invented an event or is using a name that was renamed under it")
                .isEmpty();
    }

    @Test
    @DisplayName("the ratchet only shrinks — a settled name is removed from the allowlist")
    void theAllowlistDoesNotOutliveTheContradiction() throws IOException {
        Corpus corpus = readCorpus();

        Set<String> registry = Arrays.stream(EventType.values())
                .map(EventType::wireName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> stale = KNOWN_OUTSIDE_THE_REGISTRY.stream()
                .filter(registry::contains)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(stale)
                .as("""
                    These are now in §4, so the exemption is spent. An allowlist entry that \
                    outlives what it excused is how a ratchet stops being one — it silently \
                    re-permits the next occurrence of a problem that was actually solved.""")
                .isEmpty();

        Set<String> unused = KNOWN_OUTSIDE_THE_REGISTRY.stream()
                .filter(name -> !corpus.published.containsKey(name)
                        && !corpus.consumed.containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unused)
                .as("no spec publishes or consumes these any more — drop them from the allowlist")
                .isEmpty();
    }

    @Test
    @DisplayName("every §4 row has a spec that publishes it")
    void theRegistryHasNoUnsentRows() throws IOException {
        Corpus corpus = readCorpus();

        Set<String> unsent = Arrays.stream(EventType.values())
                .map(EventType::wireName)
                .filter(name -> !corpus.published.containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unsent)
                .as("a registry row no spec publishes is a message the platform promises and "
                        + "never sends — the same silence as an orphaned consumer, one step earlier")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    /** wire name → the spec ids that publish it / consume it. */
    private record Corpus(Map<String, Set<String>> published, Map<String, Set<String>> consumed) {
    }

    private static Corpus readCorpus() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ is not present");

        Map<String, Set<String>> published = new LinkedHashMap<>();
        Map<String, Set<String>> consumed = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(SPECS)) {
            List<Path> specs = files.filter(p -> p.getFileName().toString().equals("spec.yaml"))
                    .sorted()
                    .toList();

            for (Path spec : specs) {
                String yaml = Files.readString(spec);
                String id = firstMatch(yaml, "(?m)^id:\\s*(\\S+)");
                // The template ships as a spec.yaml with placeholder ids; it declares nothing.
                if (id == null || id.startsWith("ET-XXX")) {
                    continue;
                }
                collect(yaml, "bus", id, published);
                collect(yaml, "consumes", id, consumed);
            }
        }

        assertThat(published)
                .as("no bus names parsed from any spec.yaml — the corpus layout changed")
                .isNotEmpty();
        return new Corpus(published, consumed);
    }

    /**
     * Reads one list under {@code events:}.
     *
     * <p>Entries carry trailing comments ({@code - catalog.EventPublished  # v1 → booking}), so
     * the name is taken up to the first whitespace or {@code #}.</p>
     */
    private static void collect(String yaml, String key, String specId, Map<String, Set<String>> into) {
        Matcher block = Pattern.compile(
                        "(?m)^  " + key + ":\\s*$\\n((?:^    -.*$\\n?)*)")
                .matcher(yaml);
        if (!block.find()) {
            return;
        }
        Matcher entry = Pattern.compile("(?m)^    -\\s*([A-Za-z]+\\.[A-Za-z]+)").matcher(block.group(1));
        while (entry.find()) {
            into.computeIfAbsent(entry.group(1), k -> new LinkedHashSet<>()).add(specId);
        }
    }

    private static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

}
