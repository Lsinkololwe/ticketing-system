package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The half the clock migration does not cover: <b>elapsed</b> time must not be measured with a
 * wall clock.
 *
 * <h2>Two different problems that look like one</h2>
 * Timestamps come from the injected {@code Clock}, and `InlineNowLintTest` ratchets that
 * down. But a subtraction of two wall-clock readings is not a timestamp at all — it is a duration,
 * and injecting a {@code Clock} into it fixes the lint while leaving the bug.
 *
 * <p>The bug is that the wall clock is <b>not monotonic</b>. NTP corrects it, DST steps it, an
 * operator sets it. Between the two ends of a request that is rare; across every request a busy
 * gateway serves it is not, and the failure is silent: a duration that is wrong by the size of the
 * correction, or negative, logged as a latency measurement somebody will later trust. This was
 * live in `RequestLoggingFilter`, which bracketed every request through the gateway with
 * {@code Instant.now().toEpochMilli()}.</p>
 *
 * <h2>Why a regex can decide this</h2>
 * The shape is narrow and specific: a subtraction where at least one side is a wall-clock reading.
 * That is not a judgement call the way "wiring or leak?" is — there is no legitimate reason to
 * subtract two wall-clock readings to get a duration, because {@code System.nanoTime()} exists and
 * costs the same. So this is a **ban**, not a ratchet: unlike the 283 inline {@code now()} sites,
 * there is no baseline of legitimate instances to preserve.
 *
 * <p>Measuring an interval between two <em>stored</em> instants — "how long ago was this row
 * written" — is a different thing and is not matched here: that reads one clock and one persisted
 * value, and the persisted value is not a second reading of a drifting clock.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001 R3 · elapsed time is measured monotonically, not with the wall clock")
class ElapsedTimeLintTest {

    private static final List<String> MODULES = List.of(
            "shared-library", "catalog-service", "booking-service", "identity-service",
            "api-gateway", "keycloak-extensions");

    /**
     * A subtraction with a wall-clock reading on either side.
     *
     * <p>Deliberately narrow. It matches {@code System.currentTimeMillis() - x} and
     * {@code x - System.currentTimeMillis()}, and the same for {@code Instant.now().toEpochMilli()}
     * and {@code clock.millis()} — the injected clock is no more monotonic than the static one, so
     * "migrated to the Clock" is not a defence.</p>
     */
    private static final Pattern WALL_CLOCK_DELTA = Pattern.compile(
            "(?:System\\.currentTimeMillis\\(\\)|Instant\\.now\\([^)]*\\)\\.toEpochMilli\\(\\)"
                    + "|\\w*[Cc]lock\\.millis\\(\\))\\s*-\\s*\\w"
                    + "|\\w\\s*-\\s*(?:System\\.currentTimeMillis\\(\\)"
                    + "|Instant\\.now\\([^)]*\\)\\.toEpochMilli\\(\\)|\\w*[Cc]lock\\.millis\\(\\))");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    /**
     * Comments and block comments removed; string literals kept.
     *
     * <p>Both halves matter and this corpus has been bitten by each. A lint that counts prose fails
     * on a pure documentation edit and charges a file for explaining itself — this very class names
     * {@code System.currentTimeMillis() - x} in its own javadoc. Reluctant quantifier because the
     * obvious alternation form overflows the stack on a large file.</p>
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("no wall-clock subtraction anywhere in production source")
    void noWallClockDeltas() throws IOException {
        List<String> offenders = new ArrayList<>();
        int examined = 0;

        for (String module : MODULES) {
            Path root = backendRoot().resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                for (Path file : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                    examined++;
                    if (file.getFileName().toString().equals("ElapsedTimeLintTest.java")) {
                        continue;
                    }
                    Matcher hit = WALL_CLOCK_DELTA.matcher(withoutComments(Files.readString(file)));
                    while (hit.find()) {
                        offenders.add(backendRoot().relativize(file) + " → "
                                + hit.group().trim());
                    }
                }
            }
        }

        assertThat(examined)
                .as("a lint that scans nothing passes forever — this must find real source")
                .isGreaterThan(200);
        assertThat(offenders)
                .as("the wall clock is not monotonic: NTP, DST and an operator can all move it "
                        + "between the two readings, producing a duration that is wrong by the "
                        + "size of the correction or outright negative — and logged as a latency "
                        + "somebody will trust. Use System.nanoTime(), which exists for this.")
                .isEmpty();
    }

    @Test
    @DisplayName("the pattern actually matches the shape it claims to")
    void theBanRecognisesItsOwnTarget() {
        // A lint whose regex is subtly wrong reports a clean codebase forever, and the assertion
        // above cannot tell that apart from a genuinely clean one. So: feed it the exact line that
        // was live in RequestLoggingFilter until 2026-09-02, and the fix that replaced it.
        String wasLive = "long duration = Instant.now().toEpochMilli() - startTime;";
        String theFix = "long d = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);";

        assertThat(WALL_CLOCK_DELTA.matcher(wasLive).find())
                .as("the shape this lint exists to ban must be recognised")
                .isTrue();
        assertThat(WALL_CLOCK_DELTA.matcher(theFix).find())
                .as("and the monotonic fix must not be flagged, or the lint is unusable")
                .isFalse();
    }
}
