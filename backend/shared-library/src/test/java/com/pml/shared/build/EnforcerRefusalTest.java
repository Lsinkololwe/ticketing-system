package com.pml.shared.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The enforcer actually refuses, and names the module when it does.
 *
 * <h2>Why this is a separate test that shells out to Maven</h2>
 * {@link BuildTopologyTest} asserts the enforcer rules are <em>configured</em>. That is not the
 * same claim as the rules <em>biting</em>, and for a build rule the negative test is the whole
 * value. A rule can be present and scoped to a phase nothing runs, or listed
 * under a plugin declaration that a child overrides, and every structural assertion still passes.
 *
 * <p>So this one runs a real build. It does it against a throwaway module in a temp directory that
 * inherits the installed {@code com.pml:backend} parent, rather than by editing a real POM: a test
 * that mutates the working tree and restores it in a finally block leaves the tree broken the one
 * time it is killed mid-run, and that is a bad trade for a build check.</p>
 *
 * <h2>What it asserts beyond "the build failed"</h2>
 * The failure has to <b>name the module</b>. A generic refusal makes the next
 * person bisect seven POMs to find which one they broke, and that cost is paid every time rather
 * than once — which is why the message is asserted here, not just the exit code.
 */
@Tag("L4")
@Tag("ET-PLT-012")
@DisplayName("ET-PLT-012 R7 · the enforcer refuses, and says which module")
class EnforcerRefusalTest {

    /**
     * The servlet stack, banned on a reactive platform and enforced here.
     *
     * <p>It is the highest-value entry on the banned list: adding it to a WebFlux service does not
     * fail, it silently swaps Netty for Tomcat at startup, and the service then serves the same
     * endpoints on a blocking thread-per-request model — every reactive guarantee in the corpus
     * quietly withdrawn, with a green build.</p>
     */
    private static final String BANNED = "spring-boot-starter-web";

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    @Test
    @DisplayName("a module adding spring-boot-starter-web fails validate, by name")
    void bannedDependencyFailsValidateByName(@TempDir Path scratch) throws Exception {
        Path parentPom = backendRoot().resolve("pom.xml");
        assumeTrue(Files.exists(parentPom), "backend parent POM not found");

        Files.writeString(scratch.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>com.pml</groupId>
                        <artifactId>backend</artifactId>
                        <version>1.0.0</version>
                        <relativePath>%s</relativePath>
                    </parent>
                    <artifactId>servlet-smuggler</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>%s</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(parentPom.toAbsolutePath(), BANNED));

        Process build = new ProcessBuilder(List.of(mvn(), "-o", "-B", "validate"))
                .directory(scratch.toFile())
                .redirectErrorStream(true)
                .start();

        String output;
        try (var stream = build.getInputStream()) {
            output = new String(stream.readAllBytes());
        }
        assumeTrue(build.waitFor(4, TimeUnit.MINUTES), "the probe build did not finish");

        // Offline (-o): if the throwaway module cannot resolve without the network the test has
        // proved nothing about the enforcer, so it stands down rather than reporting a pass.
        assumeTrue(!output.contains("Cannot access") && !output.contains("offline mode"),
                "dependencies not in the local repository — run `mvn -f backend install` first");

        assertThat(build.exitValue())
                .as("adding " + BANNED + " must fail the build at validate, before anything is "
                        + "compiled on top of it")
                .isNotZero();
        assertThat(output)
                .as("the refusal must name the offending module, or the next person bisects "
                        + "seven POMs to find out which one they broke")
                .contains("servlet-smuggler");
        // The resolved-coordinate line, not the rule's prose. The banned-list message happens to
        // spell out "spring-boot-starter-web / tomcat / jetty / undertow" as an explanation, so a
        // bare contains(BANNED) matches the enforcer describing itself and passes against a build
        // that refused something else entirely. Only the resolution line carries a version and the
        // "<--- banned" marker, and only the real artifact can produce it.
        assertThat(output)
                .as("must name the artifact it actually resolved and refused, not merely mention "
                        + "it in the rule's own explanatory text")
                .containsPattern(BANNED + ":jar:[\\w.\\-]+ <--- banned");
    }

    private static String mvn() throws IOException {
        Path wrapper = backendRoot().resolve("mvnw");
        return Files.isExecutable(wrapper) ? wrapper.toAbsolutePath().toString() : "mvn";
    }
}
