package com.pml.shared.error;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A debug surface must not be switched on by a file every profile inherits.
 *
 * <h2>The shape this bans</h2>
 * A base {@code application.yml} is inherited by <em>every</em> profile. A literal {@code true}
 * there is therefore a production value, whatever the comment beside it says — and the comment is
 * usually the giveaway: {@code graphiql.enabled: true} in a base file, with
 * {@code application-prod.yml} overriding nothing, beside a {@code permitAll} annotated
 * "GraphiQL UI - development only". The intent is written down; the configuration does the
 * opposite.
 *
 * <p>Credentials take the identical shape — working fallback passwords in the base file,
 * inherited by prod, overridden nowhere — and the remedy is the same in both cases: the base
 * file defaults to the <b>safe</b> value and the
 * development value lives in {@code application-local.yml}, where a value in a file called `local`
 * is visibly a development value.</p>
 *
 * <h2>Why an environment placeholder is not a violation</h2>
 * {@code ${GRAPHIQL_ENABLED:false}} is the correct form and must keep passing: it defaults off and
 * an operator can turn it on deliberately, per environment. Only a bare literal {@code true} is
 * refused. Banning the placeholder too would leave no way to enable the console anywhere, and a
 * rule with no compliant path gets deleted rather than followed.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R4 · no debug surface is enabled by a file prod inherits")
class DebugSurfaceLintTest {

    private static final List<String> SERVICES = List.of(
            "catalog-service", "booking-service", "identity-service", "api-gateway");

    /**
     * Settings that expose internals, each of which has been a real incident somewhere.
     *
     * <p>Kept deliberately short. A long list of plausible-looking keys makes the lint feel
     * thorough while most entries never match anything, and the ones that do get exempted one by
     * one until it is off. Every key here corresponds to a surface that actually exists in this
     * platform's stack.</p>
     */
    private static final List<String> DEBUG_KEYS = List.of(
            "graphiql",            // interactive console + schema browser
            "include-stacktrace",  // Spring's error attributes — stack frames to the client
            "include-exception",   // ditto, the exception class name
            "include-message",     // ditto, the exception message — the internals leak exactly
            "sql-trace",
            "show-sql");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    /**
     * A key set to a bare {@code true}, on the same line or the next non-comment one.
     *
     * <p>YAML nests, so {@code graphiql:} and its {@code enabled:} are on different lines. Rather
     * than parse YAML for one question, this reads the few lines following the key and stops at
     * the first {@code enabled:} — narrow enough to be predictable, and asserted against a known
     * sample below so a silently-wrong regex cannot report a clean tree forever.</p>
     */
    private static boolean enabledLiterally(List<String> lines, int keyIndex) {
        // Comment and blank lines are skipped rather than counted. Counting raw lines is how the
        // first version of this test failed to see the very defect it was written for: the
        // explanatory comment added above `enabled:` pushed it outside a four-line window, and the
        // lint went green on a file that still said `enabled: true`. A lint whose reach depends on
        // how much prose sits above the value is not a lint. Four *meaningful* lines is enough for
        // any of these keys and still stops before wandering into a sibling block.
        int seen = 0;
        for (int i = keyIndex; i < lines.size() && seen < 4; i++) {
            String line = lines.get(i).split("#", 2)[0];
            if (line.isBlank()) {
                continue;
            }
            seen++;
            Matcher enabled = Pattern.compile("\\benabled:\\s*(\\S+)").matcher(line);
            if (enabled.find()) {
                return "true".equals(enabled.group(1));
            }
        }
        return false;
    }

    @Test
    @DisplayName("no base or prod application.yml turns a debug surface on with a literal true")
    void noDebugSurfaceInAnInheritedProfile() throws IOException {
        List<String> offenders = new ArrayList<>();
        int filesRead = 0;

        for (String service : SERVICES) {
            Path resources = backendRoot().resolve(service).resolve("src/main/resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            for (String file : List.of("application.yml", "application-prod.yml")) {
                Path config = resources.resolve(file);
                if (!Files.exists(config)) {
                    continue;
                }
                filesRead++;
                List<String> lines = Files.readAllLines(config);
                for (int i = 0; i < lines.size(); i++) {
                    String bare = lines.get(i).split("#", 2)[0];
                    for (String key : DEBUG_KEYS) {
                        if (bare.contains(key + ":") && enabledLiterally(lines, i)) {
                            offenders.add(service + "/" + file + ":" + (i + 1) + " → " + key);
                        }
                        if (bare.matches(".*\\b" + key + ":\\s*true\\s*$")) {
                            offenders.add(service + "/" + file + ":" + (i + 1) + " → " + key);
                        }
                    }
                }
            }
        }

        assertThat(filesRead)
                .as("a lint that reads no files passes forever")
                .isGreaterThanOrEqualTo(4);
        assertThat(offenders)
                .as("the base application.yml is inherited by every profile, so a literal true "
                        + "there is a production value however the comment beside it reads. Use "
                        + "${VAR:false} and put the development value in application-local.yml.")
                .isEmpty();
    }

    @Test
    @DisplayName("the matcher recognises the exact shape that shipped, and clears the fix")
    void theMatcherRecognisesWhatShipped() {
        // Verbatim from catalog-service/application.yml before 2026-09-02, and the fix that
        // replaced it. Without this the assertion above cannot tell a clean tree from a broken
        // regex — and a broken regex is the failure mode a lint cannot report about itself.
        List<String> shipped = List.of("    graphiql:", "      enabled: true", "      path: /graphiql");
        List<String> shippedWithProse = List.of(
                "    graphiql:", "      # a comment", "", "      # another", "      # and another",
                "      enabled: true");
        List<String> fixed = List.of("    graphiql:", "      enabled: ${GRAPHIQL_ENABLED:false}");

        assertThat(enabledLiterally(shipped, 0))
                .as("the literal-true form must be caught")
                .isTrue();
        assertThat(enabledLiterally(shippedWithProse, 0))
                .as("prose above the value must not put it out of reach — this is the bug the "
                        + "first version of this lint had, found by mutating the config")
                .isTrue();
        assertThat(enabledLiterally(fixed, 0))
                .as("the environment placeholder is the compliant form and must pass")
                .isFalse();
    }
}
