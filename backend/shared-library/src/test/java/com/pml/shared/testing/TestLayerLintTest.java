package com.pml.shared.testing;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every test declares its layer, and the declaration matches what it uses.
 *
 * <h2>What the layers buy</h2>
 * The layers are a contract about cost: layer 1 is plain JUnit at about a millisecond, layer 2
 * adds a Testcontainers MongoDB at about fifty, and layers 4 and 5 add composition and WireMock at
 * seconds apiece. The fast stage — layers 1 to 3 — has a sixty-second budget, and that budget is
 * only meaningful if a class cannot quietly sit in a layer it does not belong to.
 *
 * <p>The failure mode is specific and one-directional. Nobody labels a fast test slow; what
 * happens is that a layer-1 test grows a fixture, someone reaches for a real repository to make an
 * assertion work, and a class that was a millisecond becomes fifty without its tag changing. Do
 * that a few dozen times and the fast stage is the full suite wearing a different name. If you
 * are asserting on commission arithmetic inside a Testcontainers test, move it down.
 *
 * <h2>Derived, not asked for</h2>
 * The layer is inferred from what a class references — a container fixture, a Spring context, an
 * SDL — and compared against the tag it carries. That way the tag cannot drift from the truth by
 * being forgotten, only by being deliberately wrong, and this test fails either way.
 */
@Tag("L1")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006-R1 · the declared test layer matches what the test actually uses")
class TestLayerLintTest {

    private static final Path BACKEND = locateBackend();

    private static final Pattern LAYER_TAG = Pattern.compile("@Tag\\(\"(L[1-5])\"\\)");
    private static final Pattern SPEC_TAG = Pattern.compile("@Tag\\(\"(ET-[A-Z]+-\\d+)\"\\)");

    /**
     * What each layer is allowed to reach for, in layer order.
     *
     * <p>Checked most expensive first: a class using both WireMock and a container is layer 5, and
     * asking about the container first would file it as layer 2 and put it in the fast stage.
     */
    private static final Map<String, List<String>> MARKERS = markers();

    private static Map<String, List<String>> markers() {
        // Built by insertion rather than from Map.of: `new LinkedHashMap<>(Map.of(…))` copies
        // from an unordered map, so the iteration order would be arbitrary and the
        // most-expensive-first rule below would hold only by luck.
        Map<String, List<String>> ordered = new LinkedHashMap<>();
        ordered.put("L5", List.of("Providers", "WireMock", "Concurrency"));
        ordered.put("L2", List.of("MongoReplicaSet", "MongoStandalone", "Testcontainers", "RedisNode",
                "TemporalDevServer"));
        // Layer 3 is the saga layer, and a durable workflow is the platform's saga: the
        // time-skipping test environment runs one end to end against stubbed activities.
        ordered.put("L3", List.of("@SpringBootTest", "ApplicationContextRunner",
                "TestWorkflowEnvironment", "TestWorkflowExtension"));
        // `ProcessBuilder` is an L4 marker in its own right. Layer is inferred from what a class
        // references, and a test that forks a real build references nothing heavy — it imports
        // java.lang and costs a second. EnforcerRefusalTest read as L1 for exactly that reason,
        // and it is not L1: L1 is "no Spring, no database, a millisecond". Whatever a subprocess
        // costs, it is not that, and the inference cannot see across the fork to find out.
        ordered.put("L4", List.of("schema.graphqls", "supergraph", "ProcessBuilder"));
        return ordered;
    }

    private static Path locateBackend() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("catalog-service/src/test"))) {
                return candidate;
            }
        }
        return Path.of("..");
    }

    @Test
    @DisplayName("ET-PLT-006-R1 · every test class declares exactly one layer and one spec")
    void everyTestIsClassified() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();
        int examined = 0;

        for (Path file : testClasses()) {
            String source = withoutComments(Files.readString(file));
            examined++;
            long layers = LAYER_TAG.matcher(source).results().count();
            long specs = SPEC_TAG.matcher(source).results().count();

            if (layers != 1) {
                problems.add("%s carries %d layer tags — exactly one of L1…L5 is required"
                        .formatted(file.getFileName(), layers));
            }
            if (specs < 1) {
                problems.add("%s carries no ET-… tag, so `-Dgroups=<spec>` cannot select it"
                        .formatted(file.getFileName()));
            }
        }

        // A classifier that finds no test classes reports a perfectly classified suite.
        assertThat(examined)
                .as("no test classes found — the walk is broken, not the suite")
                .isGreaterThan(50);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-006-R1 · a layer-1 test touches no container and no Spring context")
    void declaredLayersAreHonest() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> problems = new ArrayList<>();
        for (Path file : testClasses()) {
            // This class names every marker string in order to look for them, so deriving a
            // layer from its own source would always find the most expensive one.
            if (file.getFileName().toString().equals("TestLayerLintTest.java")) {
                continue;
            }
            String source = withoutComments(Files.readString(file));
            Matcher declared = LAYER_TAG.matcher(source);
            if (!declared.find()) {
                continue;
            }
            String derived = derive(source);
            if (!derived.equals(declared.group(1))) {
                problems.add("""
                        %s declares %s and uses %s. R1 puts a sixty-second budget on layers 1-3; \
                        a class that grew a fixture without changing its tag spends that budget \
                        without anyone choosing to."""
                        .formatted(file.getFileName(), declared.group(1), derived));
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-006-R1 · layer 1 is the majority of the suite by count")
    void mostTestsAreLayerOne() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        // Nearly all tests are layer 1, because nearly all the risk is. A suite that
        // inverts this is one nobody runs, so the majority is measured here rather than
        // hoped for.
        long total = 0;
        long layerOne = 0;
        for (Path file : testClasses()) {
            String source = withoutComments(Files.readString(file));
            Matcher declared = LAYER_TAG.matcher(source);
            if (declared.find()) {
                total++;
                if ("L1".equals(declared.group(1))) {
                    layerOne++;
                }
            }
        }
        assertThat(layerOne)
                .as("%d of %d test classes are layer 1; the arithmetic and the state machines "
                        + "belong there, not behind a container", layerOne, total)
                .isGreaterThan(total / 2);
    }

    // ── helpers ─────────────────────────────────────────────────────────────


    /**
     * Comments and block comments removed before the layer is derived.
     *
     * <p>Without this the lint reads prose. A class explaining <em>why</em> it is not layer 5 names
     * the very markers that define layer 5, and is then reported as layer 5: a comment saying
     * "the corpus reserves L5 for Testcontainers plus WireMock" makes a class look like it uses
     * WireMock.
     *
     * <p>{@code TenantBoundaryLintTest}, {@code EventWriteGuardLintTest} and
     * {@code IndexAuthorityLintTest} strip comments for the same reason: any source-scanning lint
     * that reads comments reports prose as code. Reluctant quantifier: the obvious alternation
     * form overflows the stack on a large file.</p>
     */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String derive(String source) {
        for (Map.Entry<String, List<String>> layer : MARKERS.entrySet()) {
            for (String marker : layer.getValue()) {
                if (source.contains(marker)) {
                    return layer.getKey();
                }
            }
        }
        return "L1";
    }

    private static List<Path> testClasses() throws IOException {
        List<Path> classes = new ArrayList<>();
        for (String service : List.of("shared-library", "catalog-service", "identity-service",
                "booking-service", "api-gateway")) {
            Path root = BACKEND.resolve(service).resolve("src/test/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith("Test.java")).sorted().forEach(classes::add);
            }
        }
        return classes;
    }
}
