package com.pml.shared.security;

import org.junit.jupiter.api.Assumptions;
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
 * Authority strings are written once, in {@link Authorities}. A role or scope spelled by hand in a
 * second place is a second definition that can fall out of step with the first, and the failure is
 * a gate that admits the wrong people without a compile error.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Role and scope authorities are spelled only in Authorities")
class AuthorityLiteralLintTest {

    private static final Path BACKEND = locateBackend();
    private static final List<String> SERVICES = List.of(
            "shared-library", "identity-service", "catalog-service", "booking-service", "api-gateway");
    private static final String HOME = "shared/security/Authorities.java";

    private static final Pattern LITERAL = Pattern.compile(
            "\"ROLE_(SUPER_ADMIN|ADMIN|FINANCE_LEAD|FINANCE|ORGANIZER|CUSTOMER|INTERNAL_SERVICE|INTERNAL|SYSTEM)\""
                    + "|\"SCOPE_internal-(read|write)\"|\"ROLE_\"");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    @DisplayName("No main source outside Authorities spells a platform role or internal scope authority")
    void authoritiesAreNotSpelledByHand() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        for (Path file : sources()) {
            scanned++;
            if (file.toString().endsWith(HOME)) {
                continue;
            }
            Matcher literal = LITERAL.matcher(COMMENT.matcher(Files.readString(file)).replaceAll(""));
            while (literal.find()) {
                problems.add(BACKEND.relativize(file) + " spells " + literal.group() + "; take it from Authorities");
            }
        }
        assertThat(scanned).as("no sources found — the lint is looking in the wrong place").isGreaterThan(100);
        assertThat(problems).isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = BACKEND.resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(all::add);
            }
        }
        return all;
    }

    private static Path locateBackend() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int up = 0; up < 4 && candidate != null; up++, candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("catalog-service/src/main/java"))) {
                return candidate;
            }
        }
        return Path.of("..");
    }
}
