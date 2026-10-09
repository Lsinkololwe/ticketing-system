package com.pml.shared.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A spec claiming to be implemented has at least one test that says so.
 *
 * <h2>Why this is a test and not a build flag</h2>
 * Every gate in the corpus names the same verification command:
 * {@code mvn -f backend verify -Dgroups=<SPEC> -DfailIfNoTests=true}. The flag is there for a good
 * reason — without it a spec "verifies green" having executed zero tests, which is how a corpus
 * awards itself a status by accident.
 *
 * <p>It cannot work. In a five-module reactor {@code failIfNoTests} means <b>zero in each
 * module</b>, not zero overall, so the build fails on the first module that has no test for that
 * tag — and no spec has a test in all five. A spec with a hundred tagged tests across four
 * modules still fails on {@code api-gateway}, which has three tests and none of them tagged, so
 * the command could not succeed for any spec.
 *
 * <p>So the guarantee lives here, where it is a property of the corpus rather than of an
 * invocation: the gates run with {@code -DfailIfNoTests=false}, and <b>this</b> test is what
 * makes "implemented" mean something. A flag can be omitted by whoever types the command next; a
 * test in the reactor cannot.</p>
 *
 * <h2>Deliberately weak, and deliberately not weaker</h2>
 * One tagged test is a low bar, and it is the right bar for a mechanical check: how much coverage a
 * requirement deserves is a human judgement, which is what the gate itself is for. What this
 * refuses is the specific, checkable failure of a spec advancing to {@code implemented} with
 * <b>nothing at all</b> pointing at it.
 */
@Tag("L1")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006 · a spec claiming implemented has a test tagged to it")
class SpecTagCoverageTest {

    /** Statuses that assert work is done, as opposed to planned. */
    private static final Set<String> CLAIMS_WORK_DONE = Set.of("implemented", "verified");

    private static final Pattern SPEC_ID = Pattern.compile("(?m)^id:\\s*(ET-[A-Z]+-\\d+)");
    private static final Pattern STATUS = Pattern.compile("(?m)^status:\\s*(\\w+)");
    private static final Pattern TAGGED = Pattern.compile("@Tag\\(\"(ET-[A-Z]+-\\d+)\"\\)");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static Path specsRoot() {
        return backendRoot().getParent().resolve("specs");
    }

    /** spec id → declared status, for every spec.yaml in the corpus. */
    private static Map<String, String> corpusStatuses() throws IOException {
        Map<String, String> statuses = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(specsRoot())) {
            for (Path yaml : tree.filter(p -> p.getFileName().toString().equals("spec.yaml")).toList()) {
                String source = Files.readString(yaml);
                Matcher id = SPEC_ID.matcher(source);
                Matcher status = STATUS.matcher(source);
                if (id.find() && status.find()) {
                    statuses.put(id.group(1), status.group(1));
                }
            }
        }
        return statuses;
    }

    /** Every spec id that at least one test class carries as a tag. */
    private static Set<String> taggedByTests() throws IOException {
        Set<String> tagged = new LinkedHashSet<>();
        try (Stream<Path> tree = Files.walk(backendRoot())) {
            for (Path source : tree
                    .filter(p -> p.toString().contains("/src/test/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                Matcher tag = TAGGED.matcher(Files.readString(source));
                while (tag.find()) {
                    tagged.add(tag.group(1));
                }
            }
        }
        return tagged;
    }

    @Test
    @DisplayName("every spec at implemented or verified has a test carrying its tag")
    void nothingClaimsDoneWithoutATest() throws IOException {
        assumeTrue(Files.isDirectory(specsRoot()), "specs/ not checked out beside backend/");

        Map<String, String> statuses = corpusStatuses();
        Set<String> tagged = taggedByTests();

        List<String> unproven = new ArrayList<>();
        for (Map.Entry<String, String> spec : statuses.entrySet()) {
            if (CLAIMS_WORK_DONE.contains(spec.getValue()) && !tagged.contains(spec.getKey())) {
                unproven.add(spec.getKey() + " is '" + spec.getValue() + "' with no tagged test");
            }
        }

        assertThat(statuses)
                .as("no spec.yaml parsed means the regex or the path is wrong, not that the "
                        + "corpus is clean — and this test would then pass forever")
                .hasSizeGreaterThan(30);
        assertThat(unproven)
                .as("a spec advances to implemented when its gate is met, and a gate met by zero "
                        + "tests is a sentence somebody wrote. Tag the tests that prove it, or put "
                        + "the status back.")
                .isEmpty();
    }

    @Test
    @DisplayName("no test is tagged to a spec that does not exist")
    void everyTagNamesARealSpec() throws IOException {
        assumeTrue(Files.isDirectory(specsRoot()), "specs/ not checked out beside backend/");

        Set<String> known = corpusStatuses().keySet();
        List<String> orphans = taggedByTests().stream()
                .filter(tag -> !known.contains(tag))
                .sorted()
                .toList();

        // The other direction, and the one that rots quietly: a tag surviving a spec's rename or
        // withdrawal points at nothing, and `verify -Dgroups=` on it runs zero tests while looking
        // like coverage.
        assertThat(orphans)
                .as("a tag naming no spec is coverage pointing nowhere")
                .isEmpty();
    }
}
