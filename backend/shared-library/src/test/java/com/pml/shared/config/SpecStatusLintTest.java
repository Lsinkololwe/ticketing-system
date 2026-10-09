package com.pml.shared.config;

import org.junit.jupiter.api.Assumptions;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The corpus status lifecycle, enforced.
 *
 * <h2>Why this test exists</h2>
 * Without this test nothing enforces the lifecycle mechanically: the boxes are checked by the
 * person who read the spec and ran its tests, and {@code verified} is their statement that both
 * happened, so honesty about an unchecked box is the whole safeguard.
 *
 * <p>Honesty is a fine safeguard against dishonesty and no safeguard at all against forgetting,
 * which is the failure that actually happens. Both directions cost something real:</p>
 *
 * <ul>
 *   <li><b>Claiming done too early</b> unblocks every spec that lists it under {@code blocked_by}.
 *       Work starts on foundations that are not there, and the corpus's build order — the one
 *       thing keeping fourteen platform specs tractable — stops meaning anything.</li>
 *   <li><b>Finishing and not saying so</b> is quieter and just as expensive: the next agent
 *       re-reads a spec that is already built, re-derives its decisions, and sometimes rebuilds
 *       part of it. A gate whose boxes are all ticked while the spec still reads
 *       {@code approved} is finished work that nobody can see is finished.</li>
 * </ul>
 *
 * <p>So the rule is symmetric, and that is the point: the status and the gate must agree in
 * <em>both</em> directions.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("specs · status and gate agree in both directions")
class SpecStatusLintTest {

    private static final Path SPECS = Path.of("../../specs");
    private static final Path TASKS = SPECS.resolve("tasks");

    private static final Set<String> LIFECYCLE = Set.of(
            "draft", "approved", "in-progress", "implemented", "verified", "deferred", "withdrawn");

    /** A status that asserts the work is built, so the gate must back it up. */
    private static final Set<String> CLAIMS_BUILT = Set.of("implemented", "verified");

    /** Statuses where an unfinished gate is expected rather than a contradiction. */
    private static final Set<String> NOT_YET_CLAIMING = Set.of(
            "draft", "approved", "in-progress", "deferred", "withdrawn");

    @Test
    @DisplayName("every spec carries a status from the lifecycle")
    void everySpecHasALegalStatus() throws IOException {
        List<String> problems = new ArrayList<>();

        forEachSpec((id, status, yaml) -> {
            if (status == null) {
                problems.add(id + " has no status: field");
            } else if (!LIFECYCLE.contains(status)) {
                problems.add("%s has status '%s', which is not in the lifecycle %s"
                        .formatted(id, status, LIFECYCLE));
            }
        });

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("a spec claiming implemented has every gate box ticked")
    void nothingClaimsToBeBuiltBeforeItsGatePasses() throws IOException {
        List<String> problems = new ArrayList<>();

        forEachSpec((id, status, yaml) -> {
            if (!CLAIMS_BUILT.contains(status)) {
                return;
            }
            Gate gate = gateOf(id);
            if (gate == null) {
                problems.add(id + " claims " + status + " but has no task file to gate it");
                return;
            }
            if (gate.unchecked() > 0) {
                problems.add("%s claims %s with %d of %d gate boxes unticked: %s".formatted(
                        id, status, gate.unchecked(), gate.total(), gate.firstUnchecked()));
            }
        });

        assertThat(problems)
                .as("""
                    A spec that claims to be built unblocks everything listing it under \
                    blocked_by. Claiming it early starts work on foundations that are not there.""")
                .isEmpty();
    }

    @Test
    @DisplayName("a spec whose gate fully passes is marked implemented, unless it is blocked")
    void finishedWorkIsMarkedFinished() throws IOException {
        List<String> problems = new ArrayList<>();

        // Blocked specs are exempt, because for them this rule is unsatisfiable. A spec can
        // finish its own work before its blockers finish theirs, with every gate row ticked and
        // its `blocked_by` entries still in progress. Without the exemption this test would demand
        // `implemented` while blockersAreBuiltFirst refuses it, and no edit would satisfy both.
        // A rule with no compliant state is one somebody deletes rather than follows.
        Map<String, String> statusById = new LinkedHashMap<>();
        Map<String, List<String>> blockersById = new LinkedHashMap<>();
        forEachSpec((id, status, yaml) -> {
            statusById.put(id, status);
            blockersById.put(id, blockedBy(yaml));
        });

        forEachSpec((id, status, yaml) -> {
            if (!NOT_YET_CLAIMING.contains(status)) {
                return;
            }
            boolean waitingOnABlocker = blockersById.getOrDefault(id, List.of()).stream()
                    .anyMatch(blocker -> NOT_YET_CLAIMING.contains(
                            statusById.getOrDefault(blocker, "approved")));
            if (waitingOnABlocker) {
                return;
            }
            Gate gate = gateOf(id);
            // The last gate row is "Spec status: → implemented", which cannot be ticked before
            // this very change. It is excluded so the rule is satisfiable rather than circular.
            if (gate == null || gate.total() == 0 || gate.uncheckedIgnoringTheStatusRow() > 0) {
                return;
            }
            problems.add("%s has passed every gate box but still reads '%s' — advance it to "
                    .formatted(id, status) + "implemented");
        });

        assertThat(problems)
                .as("""
                    Finished work that is not marked finished is re-read, re-derived and \
                    sometimes rebuilt by whoever picks the corpus up next.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no spec is implemented ahead of something it is blocked by")
    void blockersAreBuiltFirst() throws IOException {
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, List<String>> blockers = new LinkedHashMap<>();

        forEachSpec((id, status, yaml) -> {
            statuses.put(id, status);
            blockers.put(id, blockedBy(yaml));
        });

        List<String> problems = new ArrayList<>();
        statuses.forEach((id, status) -> {
            if (!CLAIMS_BUILT.contains(status)) {
                return;
            }
            for (String blocker : blockers.getOrDefault(id, List.of())) {
                String blockerStatus = statuses.get(blocker);
                if (blockerStatus != null && !CLAIMS_BUILT.contains(blockerStatus)) {
                    problems.add("%s is %s but its blocker %s is only %s"
                            .formatted(id, status, blocker, blockerStatus));
                }
            }
        });

        assertThat(problems)
                .as("README: blocked_by is a hard edge — a workflow must not start a spec whose "
                        + "blockers are not implemented")
                .isEmpty();
    }

    // --------------------------------------------------------------------- helpers

    private interface SpecVisitor {
        void visit(String id, String status, String yaml);
    }

    private static void forEachSpec(SpecVisitor visitor) throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SPECS), "specs/ is not present");

        try (Stream<Path> files = Files.walk(SPECS)) {
            List<Path> specs = files
                    .filter(p -> p.getFileName().toString().equals("spec.yaml"))
                    // _templates ships a placeholder spec.yaml that describes nothing.
                    .filter(p -> !p.toString().contains("_templates"))
                    .sorted()
                    .toList();

            assertThat(specs).as("no spec.yaml found — the corpus layout changed").isNotEmpty();

            for (Path spec : specs) {
                String yaml = Files.readString(spec);
                visitor.visit(
                        firstMatch(yaml, "(?m)^id:\\s*(\\S+)"),
                        firstMatch(yaml, "(?m)^status:\\s*(\\S+)"),
                        yaml);
            }
        }
    }

    /** The checkbox rows under {@code ## E · Gate} in a spec's task file. */
    private record Gate(int total, int unchecked, int uncheckedIgnoringTheStatusRow,
                        String firstUnchecked) {
    }

    private static Gate gateOf(String specId) {
        Path taskFile = TASKS.resolve(specId + ".md");
        if (!Files.isRegularFile(taskFile)) {
            return null;
        }
        String markdown;
        try {
            markdown = Files.readString(taskFile);
        } catch (IOException unreadable) {
            return null;
        }

        int start = markdown.indexOf("## E · Gate");
        if (start < 0) {
            return null;
        }
        String section = markdown.substring(start);
        int next = section.indexOf("\n## ", 1);
        if (next > 0) {
            section = section.substring(0, next);
        }

        int total = 0;
        int unchecked = 0;
        int uncheckedIgnoringStatus = 0;
        String first = null;

        for (String line : section.lines().toList()) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("- [")) {
                continue;
            }
            total++;
            if (trimmed.startsWith("- [x]")) {
                continue;
            }
            unchecked++;
            if (first == null) {
                first = trimmed.length() > 90 ? trimmed.substring(0, 90) + "…" : trimmed;
            }
            if (!trimmed.contains("status:")) {
                uncheckedIgnoringStatus++;
            }
        }
        return new Gate(total, unchecked, uncheckedIgnoringStatus, first);
    }

    private static List<String> blockedBy(String yaml) {
        String inline = firstMatch(yaml, "(?m)^blocked_by:\\s*\\[([^\\]]*)\\]");
        if (inline == null || inline.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        Matcher matcher = Pattern.compile("ET-[A-Z]{3}-\\d{3}").matcher(inline);
        while (matcher.find()) {
            ids.add(matcher.group());
        }
        return ids;
    }

    private static String firstMatch(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
