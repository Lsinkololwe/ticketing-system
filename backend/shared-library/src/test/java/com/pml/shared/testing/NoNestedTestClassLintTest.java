package com.pml.shared.testing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test class grouped with JUnit's nested-class annotation fails the build, and every module keeps
 * declaring the JUnit launcher those groups depend on.
 *
 * <p>Every test in this backend is a flat {@code @Test} method of its top-level class. A grouped
 * class is reported by Surefire as a separate suite per group, so the top-level class's own report
 * shows only its ungrouped methods, and a suite count read off the class name undercounts. Worse,
 * when a module does not declare {@code junit-platform-launcher} itself, Surefire falls back to
 * its bundled launcher and the grouped methods are not executed at all, with no error and no
 * skipped count; a security proof that never runs reads as a green suite. Grouping is therefore
 * refused outright, and the launcher declaration is pinned so that removing it cannot silently
 * change what runs.
 *
 * <p>Write flat {@code @Test} methods and carry the grouping in the method name and the display
 * name. The scan reads code only: this explanation, and any comment that names the annotation,
 * are removed before matching.
 */
@Tag("L1")
@Tag("ET-PLT-006")
@DisplayName("ET-PLT-006 · no test class uses nested test groups, and every module declares the JUnit launcher")
class NoNestedTestClassLintTest {

    private static final Path BACKEND = locateBackend();

    private static final List<String> MODULES = List.of(
            "shared-library", "catalog-service", "identity-service", "booking-service", "api-gateway");

    /** Built from two pieces so this file does not contain the thing it forbids. */
    private static final Pattern NESTED_ANNOTATION = Pattern.compile("@" + "Nested\\b");

    private static final Pattern NESTED_IMPORT = Pattern.compile("import\\s+org\\.junit\\.jupiter\\.api\\." + "Nested\\s*;");

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
    @DisplayName("ET-PLT-006 · no backend test source declares a nested test group")
    void noTestSourceDeclaresANestedGroup() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> offenders = new ArrayList<>();
        int examined = 0;
        for (Path file : testSources()) {
            examined++;
            String code = withoutComments(Files.readString(file));
            if (NESTED_ANNOTATION.matcher(code).find() || NESTED_IMPORT.matcher(code).find()) {
                offenders.add(BACKEND.relativize(file).toString());
            }
        }

        assertThat(examined)
                .as("no test sources found — the walk is broken, not the suite")
                .isGreaterThan(50);
        assertThat(offenders)
                .as("these classes group tests in nested classes, which are reported as separate suites "
                        + "and are skipped entirely without the launcher; flatten them into @Test "
                        + "methods of the outer class")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-006 · every module declares junit-platform-launcher itself, so Surefire does not fall back to its bundled one")
    void everyModuleDeclaresTheLauncher() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("catalog-service")),
                "backend sources not present");

        List<String> missing = new ArrayList<>();
        for (String module : MODULES) {
            Path pom = BACKEND.resolve(module).resolve("pom.xml");
            String declared = Files.readString(pom).replaceAll("(?s)<!--.*?-->", "");
            if (!declared.contains("<artifactId>junit-platform-launcher</artifactId>")) {
                missing.add(module);
            }
        }

        assertThat(missing)
                .as("these modules leave Surefire on its bundled launcher")
                .isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-006 · the scan reads code, not comments, so prose naming the annotation is allowed")
    void commentsAreNotCode() {
        String annotation = "@" + "Nested";
        String source = """
                /** Flat methods, not %s groups. */
                // %s is mentioned here too
                class Sample {
                    @org.junit.jupiter.api.Test void a() { }
                }
                """.formatted(annotation, annotation);

        assertThat(NESTED_ANNOTATION.matcher(withoutComments(source)).find()).isFalse();
        assertThat(NESTED_ANNOTATION.matcher(withoutComments("    " + annotation + "\n    class Group { }")).find())
                .isTrue();
    }

    /** Block and line comments removed; reluctant quantifier so a large file cannot overflow the stack. */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static List<Path> testSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : MODULES) {
            Path root = BACKEND.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(sources::add);
            }
        }
        return sources;
    }
}
