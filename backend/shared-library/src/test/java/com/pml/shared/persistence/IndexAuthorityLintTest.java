package com.pml.shared.persistence;

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
 * Each service's {@code *IndexInitializer} is the only place an index is declared.
 *
 * <p>An index annotation on a model class creates an index at start-up that the registry, its
 * declared rows and the registry tests know nothing about — and it is where {@code unique + sparse} gets
 * written, which rejects the second document holding {@code null}. So annotations are banned in
 * every service source, and {@code auto-index-creation} must be off so a stray one would create
 * nothing even if it slipped in.
 */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002 · the index registry is the only index authority")
class IndexAuthorityLintTest {

    private static final List<String> SERVICES = List.of("booking-service", "identity-service", "catalog-service");

    /** Comments and string literals, blanked so prose that names an annotation is not counted. */
    private static final Pattern NOT_CODE = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\\\n])*\"", Pattern.DOTALL);

    private static final Pattern ANNOTATION = Pattern.compile(
            "@(Indexed|CompoundIndex|CompoundIndexes|TextIndexed|GeoSpatialIndexed|HashIndexed|WildcardIndexed)\\b");

    private static final Pattern AUTO_INDEX = Pattern.compile("auto-index-creation:\\s*(\\S+)");

    @Test
    @DisplayName("no service source declares an index by annotation")
    void noIndexAnnotations() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String service : SERVICES) {
            for (Path source : sources(service)) {
                scanned++;
                Matcher matcher = ANNOTATION.matcher(NOT_CODE.matcher(Files.readString(source)).replaceAll(""));
                while (matcher.find()) {
                    offenders.add(backendRoot().relativize(source) + " → " + matcher.group());
                }
            }
        }
        assertThat(scanned).as("no sources found — the lint is looking in the wrong place").isGreaterThan(100);
        assertThat(offenders)
                .as("""
                    an index annotation creates an index outside the registry, where no test \
                    checks it. Declare it in the service's IndexInitializer, add the ET-PLT-002 \
                    §4 row, and delete the annotation. For a unique index on an optional field \
                    use partialWhereTypeIs(field, "string"), never sparse.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every service runs with auto-index-creation off")
    void autoIndexCreationIsOff() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String service : SERVICES) {
            Path yaml = backendRoot().resolve(service).resolve("src/main/resources/application.yml");
            Matcher setting = AUTO_INDEX.matcher(Files.readString(yaml));
            if (!setting.find()) {
                problems.add(service + " does not set auto-index-creation");
            } else if (!"false".equals(setting.group(1))) {
                problems.add(service + " sets auto-index-creation: " + setting.group(1));
            }
            while (setting.find()) {
                problems.add(service + " sets auto-index-creation more than once");
            }
        }
        assertThat(problems).isEmpty();
    }

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static List<Path> sources(String service) throws IOException {
        Path root = backendRoot().resolve(service).resolve("src/main/java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
