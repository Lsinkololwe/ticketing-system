package com.pml.shared.event;

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
 * The platform's events run on RabbitMQ and nothing else.
 *
 * <p>A second broker's client left on the classpath, or its connection string left in a profile, is a
 * dependency that does not run and a secret that is never rotated. Azure Service Bus was retired; this
 * keeps it from drifting back in through a pom, a profile or an import.</p>
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("no service depends on, configures or imports Azure Service Bus")
class NoServiceBusLintTest {

    private static final Path BACKEND = locateBackend();
    private static final List<String> MODULES = List.of(
            "shared-library", "identity-service", "catalog-service", "booking-service", "api-gateway");

    private static final Pattern SERVICE_BUS = Pattern.compile(
            "(?i)service ?bus|servicebus|service-bus|azure-messaging|spring-cloud-azure-stream|"
                    + "AZURE_SERVICEBUS|binder-servicebus");

    /** This file names the thing it forbids. */
    private static final String SELF = "NoServiceBusLintTest.java";

    @Test
    @DisplayName("no main source, test source, pom or profile mentions it")
    void nothingReferencesServiceBus() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(BACKEND.resolve("identity-service")), "backend sources not present");
        List<String> found = new ArrayList<>();
        int scanned = 0;
        for (String module : MODULES) {
            Path root = BACKEND.resolve(module);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(NoServiceBusLintTest::scanned).toList()) {
                    scanned++;
                    String body = Files.readString(file);
                    if (!file.getFileName().toString().equals(SELF) && SERVICE_BUS.matcher(body).find()) {
                        found.add(BACKEND.relativize(file).toString());
                    }
                }
            }
        }
        // The module poms and the reactor pom.
        for (Path pom : List.of(BACKEND.resolve("pom.xml"))) {
            scanned++;
            if (SERVICE_BUS.matcher(Files.readString(pom)).find()) {
                found.add(BACKEND.relativize(pom).toString());
            }
        }
        assertThat(scanned).as("the lint is looking in the wrong place").isGreaterThan(100);
        assertThat(found).as("Azure Service Bus is retired; events run on RabbitMQ").isEmpty();
    }

    private static boolean scanned(Path path) {
        String name = path.toString();
        if (name.contains("/target/") || name.contains("/.git/") || name.contains("/node_modules/")) {
            return false;
        }
        return name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith("pom.xml") || name.endsWith(".properties");
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
