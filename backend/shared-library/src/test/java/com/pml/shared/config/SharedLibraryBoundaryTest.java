package com.pml.shared.config;

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
 * ET-PLT-001 R5 — {@code shared-library} carries contracts, not services.
 *
 * <h2>Why a source scan rather than a context assertion</h2>
 * A stereotype in a library is invisible from inside the library: it only becomes a bean when
 * some application component-scans the package. The three services do exactly that
 * ({@code @ComponentScan(basePackages = {"com.pml.identity", "com.pml.shared"})}), so a
 * {@code @Service} added here silently appears in all three — and a context test on this
 * module would not see it at all.
 *
 * <p>Hence lint-as-test: scan the source. ROADMAP lists R5 as asserted by this spec
 * <em>and by lint</em>, and this is the lint half.
 *
 * <h2>What replaces the stereotype</h2>
 * A library contributes beans through an {@code @AutoConfiguration} that the application can
 * override or exclude — an explicit offer rather than an implicit consequence of someone
 * else's scan. See {@link SharedPersistenceSupportAutoConfiguration}.
 */
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R5 · shared-library declares no service stereotypes")
class SharedLibraryBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /**
     * Word-boundary matched so {@code @Documented} — the JDK meta-annotation, which appears
     * legitimately on the revocation annotations — is not mistaken for Spring Data's
     * {@code @Document}. That false positive is why the first inventory of this module
     * over-reported.
     */
    private static final Pattern FORBIDDEN =
            Pattern.compile("^\\s*@(Service|Repository|Document|DgsComponent)\\b", Pattern.MULTILINE);

    /**
     * The one justified exemption, named rather than pattern-matched.
     *
     * <p>{@code AuthDirectiveAutoConfiguration} declares a nested {@code @DgsComponent} whose
     * only job is to register the {@code @auth} directive in DGS runtime wiring.
     * {@code graphql/auth.graphqls} is explicitly on R5's allowlist, and this class is the
     * mechanism that makes that schema file work — a cross-cutting contract, not business
     * logic. It is contributed through an {@code @AutoConfiguration}, so it is an explicit
     * offer rather than a consequence of someone else's component scan.
     *
     * <p>Exempted by exact path so that any <em>other</em> {@code @DgsComponent} added to this
     * module still fails. An exemption is a hole in the rule; this one is the whole list.
     */
    private static final String EXEMPT =
            "com/pml/shared/graphql/auth/AuthDirectiveAutoConfiguration.java";

    @Test
    @DisplayName("no @Service, @Repository, @Document or @DgsComponent anywhere in main source")
    void declaresNoStereotypes() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            sources.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String relative = SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
                    if (relative.equals(EXEMPT)) {
                        return;
                    }
                    String body = Files.readString(path);
                    FORBIDDEN.matcher(body).results().forEach(match ->
                            offenders.add("%s → %s".formatted(relative, match.group().trim())));
                } catch (IOException e) {
                    throw new IllegalStateException("cannot read " + path, e);
                }
            });
        }

        assertThat(offenders)
                .as("""
                    A stereotype here becomes a bean in all three services by way of their \
                    component scan, which makes the library's contents an implicit part of \
                    every application's context. Contribute it from an @AutoConfiguration \
                    instead — an offer the application can override or exclude — or move it \
                    to the service that owns it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan is actually looking at something — guards against a silently empty sweep")
    void theScanSeesTheSource() throws IOException {
        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            long javaFiles = sources.filter(p -> p.toString().endsWith(".java")).count();
            assertThat(javaFiles)
                    .as("""
                        If the working directory or the source layout ever moves, the sweep \
                        above would walk nothing and pass. An empty-result lint is not a \
                        passing lint.""")
                    .isGreaterThan(50);
        }
    }
}
