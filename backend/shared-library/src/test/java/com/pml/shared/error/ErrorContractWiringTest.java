package com.pml.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform's handler is the one that runs, in every service.
 *
 * <h2>Why this test exists</h2>
 * {@code ErrorContractAutoConfiguration}'s javadoc names this class as the thing that asserts its
 * two invariants "per service, because nothing about the resulting behaviour looks broken from the
 * outside". Without it, a comment would be the only thing standing between the rule and its
 * absence.
 *
 * <h2>The two ways the contract silently stops applying</h2>
 * <ol>
 *   <li><b>A service defines its own {@code DataFetcherExceptionHandler}.</b> DGS injects it by
 *       type and only one may exist, so this does not layer — it replaces, or fails startup.</li>
 *   <li><b>A service defines a {@code DataFetcherExceptionResolver}.</b> Worse, because it does
 *       not conflict with anything. Spring GraphQL consults every resolver <em>before</em> the
 *       handler and stops at the first non-null answer, so a resolver ending in a catch-all — the
 *       usual shape, because it reads as defensive completeness — answers everything and the
 *       platform handler never runs.</li>
 * </ol>
 *
 * <p>In both cases errors are still returned and the service still starts. What is gone is the
 * registry code, {@code retryable}, and the guarantee that internals do not leak —
 * {@code ResolverFailureContractTest} shows what that costs: graphql-java's default puts the
 * exception message, connection string and all, straight into the response.</p>
 *
 * <h2>A source scan, not a context</h2>
 * The claim is about what each service <em>declares</em>. Standing up three application contexts
 * to discover that would need Mongo, Redis and Keycloak for a question answerable by reading four
 * directories, and a test that needs infrastructure to assert a wiring fact gets excluded from CI
 * and stops meaning anything.
 */
@Tag("L1")
@Tag("ET-PLT-005")
@DisplayName("ET-PLT-005-R2 · the platform error handler is the one that runs")
class ErrorContractWiringTest {

    private static final List<String> SERVICES = List.of(
            "catalog-service", "booking-service", "identity-service");

    /** Both are load-bearing; see the class javadoc for how each removes the contract. */
    private static final List<String> RESERVED_TO_THE_PLATFORM = List.of(
            "DataFetcherExceptionHandler",
            "DataFetcherExceptionResolver");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("no service declares an exception handler or resolver of its own")
    void noServiceOwnsTheErrorPath() throws IOException {
        List<String> offenders = new ArrayList<>();
        int examined = 0;

        for (String service : SERVICES) {
            Path root = backendRoot().resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                for (Path file : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                    examined++;
                    String source = withoutComments(Files.readString(file));
                    for (String reserved : RESERVED_TO_THE_PLATFORM) {
                        // `implements X` or a @Bean returning X. Naming it in an import alone is
                        // harmless — the declaration is what changes behaviour.
                        boolean implemented = source.matches("(?s).*\\bimplements\\s+[^{]*\\b"
                                + reserved + "\\b.*");
                        boolean produced = source.matches("(?s).*@Bean[^;{]*\\s" + reserved
                                + "\\s+\\w+\\s*\\(.*");
                        if (implemented || produced) {
                            offenders.add(service + " → " + file.getFileName() + " declares a "
                                    + reserved);
                        }
                    }
                }
            }
        }

        assertThat(examined)
                .as("a scan that reads nothing passes forever")
                .isGreaterThan(100);
        assertThat(offenders)
                .as("a service owning any part of the error path silently replaces the contract: "
                        + "errors still come back, without registry codes, without retryable, and "
                        + "without the guarantee that an exception message stays on the server")
                .isEmpty();
    }

    @Test
    @DisplayName("the auto-configuration still registers ahead of DGS's own")
    void theOrderingAgainstDgsSurvives() throws IOException {
        Path autoConfig = backendRoot().resolve(
                "shared-library/src/main/java/com/pml/shared/error/"
                        + "ErrorContractAutoConfiguration.java");
        String source = Files.readString(autoConfig);

        // Both this bean and DGS's default are @ConditionalOnMissingBean, so whichever
        // autoconfiguration is evaluated first wins. Without the ordering the platform runs on
        // DGS's default handler — no registry codes, no leak guarantee — and nothing else fails.
        assertThat(source)
                .as("registered after DGS, this handler backs off and the contract vanishes")
                .contains("beforeName")
                .contains("DgsSpringGraphQLAutoConfiguration");

        assertThat(source)
                .as("beforeName rather than before: DGS is provided-scope, so a hard class "
                        + "reference would fail to load when it is absent at runtime")
                .doesNotContain("before = ");
    }

    @Test
    @DisplayName("the auto-configuration is actually registered, or none of it runs")
    void theAutoConfigurationIsImported() throws IOException {
        Path imports = backendRoot().resolve("shared-library/src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(Files.readString(imports))
                .as("an auto-configuration missing from the imports file is inert, and every unit "
                        + "test of the handler keeps passing because they construct it directly")
                .contains("com.pml.shared.error.ErrorContractAutoConfiguration");
    }
}
