package com.pml.shared.graphql;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The router serves the committed supergraph, not the subgraphs' own schemas, so a subgraph change
 * that was never recomposed is refused at the router while every subgraph test stays green. This
 * composes the on-disk SDL and fails when the result differs from the committed file.
 *
 * <p>Skipped where {@code rover} or the sibling infrastructure repository is not present.</p>
 */
@Tag("L4")
@Tag("ET-PLT-004")
@DisplayName("The committed supergraph is what the subgraph schemas compose to")
class SupergraphFreshnessTest {

    @Test
    @DisplayName("composing the subgraph SDL reproduces the committed supergraph")
    void supergraphIsCurrent() throws Exception {
        Path backend = Path.of("").toAbsolutePath();
        while (backend != null && !Files.isDirectory(backend.resolve("catalog-service/src/main/java"))) {
            backend = backend.getParent();
        }
        Assumptions.assumeTrue(backend != null, "backend sources not present");
        Path script = backend.getParent().getParent().resolve("docker-resources/apollo-router/ticketing/compose-supergraph.sh");
        Path rover = Path.of(System.getProperty("user.home"), ".rover/bin/rover");
        Assumptions.assumeTrue(Files.isExecutable(script), "infrastructure repository not present");
        Assumptions.assumeTrue(Files.isExecutable(rover) || onPath("rover"), "rover not installed");

        ProcessBuilder builder = new ProcessBuilder("bash", script.toString(), "--check")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.environment().put("PATH", System.getenv("PATH") + File.pathSeparator + rover.getParent());
        builder.environment().put("APOLLO_ELV2_LICENSE", "accept");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(180, TimeUnit.SECONDS)).as("composition finished").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
    }

    private static boolean onPath(String command) {
        for (String dir : System.getenv("PATH").split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, command))) {
                return true;
            }
        }
        return false;
    }
}
