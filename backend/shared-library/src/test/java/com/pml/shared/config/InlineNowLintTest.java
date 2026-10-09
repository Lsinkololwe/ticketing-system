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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every timestamp comes from the injected {@link java.time.Clock}.
 *
 * <h2>Why this matters more than it looks</h2>
 * The platform is defined by its time boundaries: a reservation live at 9:59 and expired at
 * 10:01, an OTP valid at 4:59 and dead at 5:01, a sales window that opens at
 * {@code salesStartAt} and not a second before. Each is specified on both sides, and none is
 * testable against a clock the test cannot move. A single {@code Instant.now()} inside the
 * code under test defeats a frozen-clock test silently — the test passes, and it proved
 * nothing about the boundary.
 *
 * <h2>What counts as a violation, and what does not</h2>
 * <ul>
 *   <li>{@code Instant.now()}, {@code LocalDateTime.now()}, {@code LocalDate.now()},
 *       {@code System.currentTimeMillis()} — wall-clock reads, all violations.</li>
 *   <li>{@code Instant.now(clock)} and friends — <b>not</b> violations. The argument is the
 *       whole point, so the pattern requires empty parentheses.</li>
 *   <li>{@code System.nanoTime()} — <b>not</b> a violation. It is a monotonic counter for
 *       measuring elapsed duration, unrelated to wall-clock time and deliberately not
 *       something a {@code Clock} should replace. Substituting one for the other is a bug,
 *       not a fix.</li>
 *   <li>Comment and Javadoc lines — skipped. Several classes explain in prose why not to call
 *       these, including {@link PlatformClockAutoConfiguration}.</li>
 * </ul>
 *
 * <h2>A ratchet over a large, deliberately staged migration</h2>
 * 477 call sites exist across four services. They are not one job:
 *
 * <ul>
 *   <li><b>Here:</b> {@code Instant.now()}, {@code LocalDate.now()} and
 *       {@code System.currentTimeMillis()} — a straight substitution to the injected clock.</li>
 *   <li><b>The persistence type migration:</b> the 230 {@code LocalDateTime.now()} sites. The correct
 *       fix is a <em>type</em> migration to {@code Instant}, not {@code LocalDateTime.now(clock)}
 *       — that would satisfy this lint while cementing exactly the type the migration removes,
 *       and would have to be undone. Fixing a lint in a way that entrenches the defect is
 *       worse than leaving it visible.</li>
 * </ul>
 *
 * <p>So the counts below are frozen per module and may only fall. New violations fail
 * immediately; the backlog is burned down by the work that owns each kind.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R3 · timestamps come from the injected Clock")
class InlineNowLintTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    /**
     * Empty parentheses only — {@code Instant.now(clock)} is the fix, not the defect. The
     * equivalent B4 pattern missed six real violations by assuming the same of
     * {@code .block()}, which is why this one is written deliberately.
     */
    private static final Pattern INLINE_NOW = Pattern.compile(
            "\\b(?:Instant|LocalDateTime|LocalDate|ZonedDateTime|OffsetDateTime)\\s*\\.\\s*now\\s*\\(\\s*\\)"
                    + "|\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\(\\s*\\)"
                    + "|\\bnew\\s+Date\\s*\\(\\s*\\)"
                    + "|\\bCalendar\\s*\\.\\s*getInstance\\s*\\(\\s*\\)");

    /**
     * Frozen 2026-08-18. Per module, so a service cannot quietly trade one fixed site for a
     * new one elsewhere. These may only fall.
     */
    private static final Map<String, Integer> BUDGET = new LinkedHashMap<>(Map.of(
            "shared-library", 0,
            "catalog-service", 0,
            "booking-service", 0,
            "identity-service", 0,
            "api-gateway", 0,
            "keycloak-extensions", 0));

    @Test
    @DisplayName("no module exceeds its frozen inline-now budget")
    void noModuleExceedsItsBudget() throws IOException {
        Map<String, List<String>> byModule = scan();

        List<String> regressions = new ArrayList<>();
        List<String> improvements = new ArrayList<>();

        BUDGET.forEach((module, budget) -> {
            int actual = byModule.getOrDefault(module, List.of()).size();
            if (actual > budget) {
                regressions.add("%s: %d inline now() calls, budget %d (+%d)"
                        .formatted(module, actual, budget, actual - budget));
            } else if (actual < budget) {
                improvements.add("%s: down to %d from %d — lower the budget to lock the gain in"
                        .formatted(module, actual, budget));
            }
        });

        assertThat(byModule.keySet())
                .as("a module appeared that has no budget — add it deliberately, at its true count")
                .isSubsetOf(BUDGET.keySet());

        assertThat(regressions)
                .as("""
                    A timestamp read from the wall clock cannot be moved by a test, so every \
                    boundary the platform is specified on becomes untestable at that call site. \
                    Inject the platform Clock and use clock.instant(), clock.millis(), or \
                    LocalDate.now(clock).""")
                .isEmpty();

        assertThat(improvements)
                .as("""
                    Sites were migrated but the budget was not lowered. A ratchet that is not \
                    tightened stops ratcheting: the reclaimed headroom silently permits new \
                    violations.""")
                .isEmpty();
    }

    @Test
    @DisplayName("System.nanoTime() is not counted — it measures elapsed time, not wall time")
    void nanoTimeIsNotAWallClockRead() {
        assertThat(INLINE_NOW.matcher("long t = System.nanoTime();").find())
                .as("""
                    nanoTime is a monotonic counter with no relationship to wall-clock time. \
                    Replacing it with a Clock read would break the elapsed-duration measurement \
                    it exists for — a regression dressed as compliance.""")
                .isFalse();
    }

    @Test
    @DisplayName("the clock-aware forms are not counted — they are the fix")
    void clockAwareFormsAreNotViolations() {
        assertThat(INLINE_NOW.matcher("Instant.now(clock)").find()).isFalse();
        assertThat(INLINE_NOW.matcher("LocalDate.now(clock)").find()).isFalse();
        assertThat(INLINE_NOW.matcher("clock.instant()").find()).isFalse();
        assertThat(INLINE_NOW.matcher("clock.millis()").find()).isFalse();

        // ...and the defects still are.
        assertThat(INLINE_NOW.matcher("Instant.now()").find()).isTrue();
        assertThat(INLINE_NOW.matcher("System.currentTimeMillis()").find()).isTrue();
    }

    // --------------------------------------------------------------------- helpers

    private static Map<String, List<String>> scan() throws IOException {
        Map<String, List<String>> byModule = new LinkedHashMap<>();
        int scanned = 0;

        try (Stream<Path> modules = Files.list(BACKEND_ROOT)) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) {
                    continue;
                }
                List<String> hits = new ArrayList<>();
                try (Stream<Path> sources = Files.walk(sourceRoot)) {
                    for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                        scanned++;
                        collect(path, hits);
                    }
                }
                byModule.put(module.getFileName().toString(), hits);
            }
        }

        assertThat(scanned)
                .as("an empty sweep is not a passing lint — the source layout has moved")
                .isGreaterThan(200);
        return byModule;
    }

    private static void collect(Path path, List<String> hits) throws IOException {
        String[] lines = Files.readString(path).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue;                    // prose about the rule is not a breach of it
            }
            var matcher = INLINE_NOW.matcher(trimmed);
            while (matcher.find()) {
                hits.add("%s:%d → %s".formatted(BACKEND_ROOT.relativize(path), i + 1, matcher.group()));
            }
        }
    }

    /** Kept for readability of the budget map above. */
    private static final Set<String> UNUSED = Set.of();
}
