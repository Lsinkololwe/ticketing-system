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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-PLT-001 R1 — no fire-and-forget subscription.
 *
 * <h2>Why bare {@code .subscribe()} specifically</h2>
 * CONVENTIONS §1 names it: <em>"Fire-and-forget: the response is sent before the write happens
 * and the error goes nowhere."</em> Both halves are real, and the second is the one that hurts.
 *
 * <p>Of the shapes Reactor offers, {@code .subscribe()} with no arguments is the only one where
 * a failure provably has nowhere to go — {@code .subscribe(onNext, onError)} at least hands the
 * error somewhere. That makes it a line a lint can actually draw, rather than a judgement about
 * whether a given lambda is "enough".
 *
 * <h2>The one this caught</h2>
 * {@code PawaPayWebhookController} cleared a webhook deduplication marker fire-and-forget inside
 * {@code onErrorResume}, then returned HTTP 200 immediately. Three failures compounded: the
 * response returned before the clear ran, a failed clear went nowhere, and PawaPay was told
 * "success" and stopped retrying. A marker left set then suppresses the genuine retry as a
 * duplicate — so the callback is never processed, and money is in flight with nobody looking
 * for it. Composed now, with the clear awaited and its own failure logged.
 *
 * <h2>Not every subscription is a defect</h2>
 * Establishing a long-lived consumer <em>is</em> subscribing. {@code AzureServiceBusConfig} wires
 * bus subscriptions, {@code MongoSchemaValidationConfig} applies validators at boot, and
 * {@code RevocationCacheWarmer} warms a cache on startup — none of those is a request path, and
 * rewriting them to avoid the word would make them worse. They sit inside the frozen counts
 * below rather than being pattern-exempted, because "is this wiring or is this a leak?" is a
 * judgement a regex should not be trusted to make.
 *
 * <p>So this is a ratchet over a triaged backlog, not a rule that pretends the backlog is
 * uniform. The triage lives in {@code specs/tasks/ET-PLT-001.md}.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R1 · no fire-and-forget subscription")
class FireAndForgetLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    /** Bare only: {@code .subscribe(handler)} hands the error somewhere, and is not this defect. */
    private static final Pattern FIRE_AND_FORGET = Pattern.compile("\\.subscribe\\s*\\(\\s*\\)");

    /**
     * Frozen 2026-08-18, after the webhook fix took booking from 14 to 13. May only fall.
     *
     * <p>Remaining, by kind: bus and boot wiring in {@code AzureServiceBusConfig} (5),
     * {@code MongoSchemaValidationConfig} (1) and {@code RevocationCacheWarmer} (1) — arguably
     * legitimate; {@code ReconciliationScheduler} (4), {@code FinancialJobListener} (3) and
     * {@code ChargebackEventListener} (1) — scheduled and after-commit work whose errors vanish,
     * owned by ET-FIN-005, ET-PLT-003 and ET-ADM-003.
     */
    private static final Map<String, Integer> BUDGET = new LinkedHashMap<>(Map.of(
            "shared-library", 1,
            "catalog-service", 0,
            "booking-service", 13,
            "identity-service", 1,
            "api-gateway", 0,
            "keycloak-extensions", 0));

    @Test
    @DisplayName("no module exceeds its frozen fire-and-forget budget")
    void noModuleExceedsItsBudget() throws IOException {
        Map<String, Integer> actual = scan();

        List<String> regressions = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        BUDGET.forEach((module, budget) -> {
            int found = actual.getOrDefault(module, 0);
            if (found > budget) {
                regressions.add("%s: %d bare .subscribe(), budget %d (+%d)"
                        .formatted(module, found, budget, found - budget));
            } else if (found < budget) {
                improvements.add("%s: down to %d from %d — lower the budget to lock the gain in"
                        .formatted(module, found, budget));
            }
        });

        assertThat(regressions)
                .as("""
                    A bare .subscribe() sends the response before the work happens and drops any \
                    error on the floor. Compose it into the chain the caller returns — then / \
                    flatMap — so the failure reaches somebody. If the work genuinely is \
                    fire-and-forget, it still needs an error handler: .subscribe(onNext, onError).""")
                .isEmpty();

        assertThat(improvements)
                .as("a ratchet that is not tightened stops ratcheting")
                .isEmpty();
    }

    @Test
    @DisplayName("only the bare form counts — a subscription with an error handler is not this defect")
    void onlyTheBareFormCounts() {
        assertThat(FIRE_AND_FORGET.matcher("flux.subscribe();").find()).isTrue();
        assertThat(FIRE_AND_FORGET.matcher("flux.subscribe( );").find()).isTrue();

        assertThat(FIRE_AND_FORGET.matcher("flux.subscribe(this::handle, this::onError);").find())
                .as("an error handler means the failure reaches somebody — a different shape entirely")
                .isFalse();
        assertThat(FIRE_AND_FORGET.matcher("flux.subscribe(subscriber);").find()).isFalse();
    }

    private static Map<String, Integer> scan() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int scanned = 0;

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                int hits = 0;
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        scanned++;
                        for (String line : Files.readString(path).split("\n", -1)) {
                            String trimmed = line.strip();
                            if (trimmed.startsWith("*") || trimmed.startsWith("//")
                                    || trimmed.startsWith("/*")) {
                                continue;
                            }
                            if (FIRE_AND_FORGET.matcher(trimmed).find()) {
                                hits++;
                            }
                        }
                    }
                }
                counts.put(module.getFileName().toString(), hits);
            }
        }

        assertThat(scanned)
                .as("an empty sweep is not a passing lint")
                .isGreaterThan(200);
        return counts;
    }
}
