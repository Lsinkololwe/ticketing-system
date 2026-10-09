package com.pml.shared.config;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retry budget is stated, in every service, rather than inherited.
 *
 * <h2>What "explicit rather than defaulted" is protecting against</h2>
 * Spring Cloud Stream supplies a default for each of these four numbers, so a service with none
 * of them configured starts cleanly, consumes normally, and behaves in a way nobody chose. The
 * two ends are both bad and neither announces itself: too few attempts turns a two-second
 * provider blip into a dead letter, and too many turns a sustained outage into a retry storm
 * against a service that is trying to come back up.
 *
 * <h2>Why a lint and not a constant</h2>
 * {@link com.pml.shared.event.RetryBudget#DEFAULT} exists, and a service could simply use it.
 * But the point is what an operator can read off the configuration of a running
 * service — the number that would have to change during an incident. A constant compiled into
 * a library is not that.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R6 · every service states its retry budget")
class ConsumerRetryLintTest {

    private static final List<String> SERVICES =
            List.of("catalog-service", "booking-service", "identity-service");

    /** All four, because a budget missing one of them is not a budget. */
    private static final List<String> REQUIRED_KEYS =
            List.of("max-attempts:", "initial-backoff:", "max-backoff:", "backoff-multiplier:");

    @Test
    @DisplayName("each service declares all four retry properties in its base configuration")
    void everyServiceStatesTheBudget() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : SERVICES) {
            Path config = Path.of("../%s/src/main/resources/application.yml".formatted(service));
            if (!Files.isRegularFile(config)) {
                problems.add(service + " has no application.yml");
                continue;
            }

            String yaml = Files.readString(config);
            if (!yaml.contains("platform:") || !yaml.contains("consumer:")) {
                problems.add(service + " declares no platform.events.consumer block at all");
                continue;
            }

            for (String key : REQUIRED_KEYS) {
                if (!yaml.contains(key)) {
                    problems.add("%s does not state %s — it would inherit a value nobody chose"
                            .formatted(service, key.replace(":", "")));
                }
            }
        }

        assertThat(problems)
                .as("R6: maxDeliveryCount, exponential backoff and a maximum backoff, all "
                        + "explicit rather than defaulted")
                .isEmpty();
    }

    /**
     * A duplicate top-level key is a deletion, not a merge.
     *
     * <p>YAML keeps the last occurrence and discards the first, silently. Appending a
     * {@code platform:} block to a file that already has one does not add to it — it removes
     * everything the earlier block held. In {@code booking-service} that is the commission rate,
     * the QR-code storage settings and the refund cutoff: the service starts, and reads defaults
     * for money.</p>
     *
     * <p>Nothing in the build catches this otherwise. The file parses, Spring binds what it is
     * given, and the only symptom is configuration that was set and is not.</p>
     */
    @Test
    @DisplayName("no service configuration declares the same top-level key twice")
    void noDuplicateTopLevelKeys() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : SERVICES) {
            try (var configs = Files.list(Path.of("../%s/src/main/resources".formatted(service)))) {
                for (Path config : configs.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    for (String line : Files.readAllLines(config)) {
                        // A top-level key starts at column zero and is not a comment or a
                        // document separator.
                        if (line.isBlank() || line.startsWith("#") || line.startsWith("-")
                                || Character.isWhitespace(line.charAt(0))) {
                            continue;
                        }
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            counts.merge(line.substring(0, colon).strip(), 1, Integer::sum);
                        }
                    }
                    counts.forEach((key, count) -> {
                        if (count > 1) {
                            problems.add("%s/%s declares '%s' %d times — the earlier block is discarded"
                                    .formatted(service, config.getFileName(), key, count));
                        }
                    });
                }
            }
        }

        assertThat(problems)
                .as("YAML keeps the last duplicate and drops the first, without complaining")
                .isEmpty();
    }

    /**
     * The base file, not {@code application-local.yml}.
     *
     * <p>A budget that exists only in the local profile is absent in production, which is the
     * one environment where the number matters and the one nobody can check by starting the
     * app on their laptop.</p>
     */
    @Test
    @DisplayName("the budget is in the base profile, so production inherits it too")
    void theBudgetIsNotLocalOnly() throws IOException {
        List<String> problems = new ArrayList<>();

        for (String service : SERVICES) {
            Path base = Path.of("../%s/src/main/resources/application.yml".formatted(service));
            Path prod = Path.of("../%s/src/main/resources/application-prod.yml".formatted(service));

            boolean inBase = Files.isRegularFile(base)
                    && Files.readString(base).contains("max-attempts:");
            boolean inProd = Files.isRegularFile(prod)
                    && Files.readString(prod).contains("max-attempts:");

            if (!inBase && inProd) {
                problems.add(service + " states the budget only under prod");
            }
            if (!inBase && !inProd) {
                problems.add(service + " states the budget in neither base nor prod");
            }
        }

        assertThat(problems).isEmpty();
    }
}
