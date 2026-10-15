package com.pml.shared.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reactive guard is one expression.
 *
 * <p>A {@code @PreAuthorize} expression that joins a bean call to anything with {@code or} or
 * {@code and} cannot use a bean that returns {@code Mono<Boolean>}: SpEL evaluates the operator and
 * cannot turn the Mono into a Boolean, so the check throws for everyone the other operand did not
 * already settle. The owner the expression exists for is refused with an internal error, which no unit
 * test of the bean can see because the bean is correct. The rule is to put the whole predicate in the
 * bean ({@code rolesOrEventOrganizer(authentication, 'ADMIN,FINANCE', #eventId)}) and call it alone.</p>
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("A @PreAuthorize that calls a reactive bean calls it alone")
class ReactiveGuardExpressionLintTest {

    private static final Path BACKEND = locateBackend();
    private static final List<String> SERVICES = List.of("identity-service", "catalog-service", "booking-service");
    private static final Pattern REACTIVE_METHOD = Pattern.compile("public\\s+Mono<Boolean>\\s+(\\w+)\\s*\\(");
    private static final Pattern PRE_AUTHORIZE = Pattern.compile("@PreAuthorize\\(\"((?:\\\\.|[^\"\\\\])*)\"\\)");
    private static final Pattern OPERATOR = Pattern.compile("\\b(or|and)\\b|\\|\\||&&");

    @Test
    @DisplayName("no expression combines a Mono<Boolean> bean call with or / and")
    void reactiveBeansAreCalledAlone() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("booking-service")), "backend sources not present");
        List<Path> sources = sources();
        Set<String> reactive = new LinkedHashSet<>();
        for (Path file : sources) {
            Matcher method = REACTIVE_METHOD.matcher(Files.readString(file));
            while (method.find()) {
                reactive.add(method.group(1));
            }
        }
        assertThat(reactive).as("no Mono<Boolean> guard beans found — the lint is looking in the wrong place")
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        for (Path file : sources) {
            Matcher expression = PRE_AUTHORIZE.matcher(Files.readString(file));
            while (expression.find()) {
                String text = expression.group(1);
                if (!OPERATOR.matcher(text).find()) {
                    continue;
                }
                for (String name : reactive) {
                    if (Pattern.compile("@\\w+\\." + Pattern.quote(name) + "\\(").matcher(text).find()) {
                        problems.add(BACKEND.relativize(file) + " : " + text);
                    }
                }
            }
        }
        assertThat(problems)
                .as("a reactive bean in an or/and expression fails for the owner it was written to admit")
                .isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>();
        for (String service : SERVICES) {
            Path root = BACKEND.resolve(service).resolve("src/main/java");
            if (Files.isDirectory(root)) {
                try (Stream<Path> files = Files.walk(root)) {
                    files.filter(path -> path.toString().endsWith(".java")).forEach(all::add);
                }
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
