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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog and booking ask identity about authorization through one client. A second HTTP call to
 * {@code /api/internal/authorization/} is a second place a failure could be read as an allow.
 */
@Tag("L1")
@Tag("ET-PLT-013")
@DisplayName("Only the shared client calls identity's authorization endpoints")
class AuthorizationClientSingleLintTest {

    private static final String PATH = "/api/internal/authorization";

    @Test
    @DisplayName("no production source outside identity and the shared client names the authorization path")
    void oneClient() throws IOException {
        Path backend = Path.of("").toAbsolutePath();
        while (backend != null && !Files.isDirectory(backend.resolve("catalog-service/src/main/java"))) {
            backend = backend.getParent();
        }
        Assumptions.assumeTrue(backend != null, "backend sources not present");

        List<String> offenders = new ArrayList<>();
        for (String module : List.of("catalog-service", "booking-service", "api-gateway", "shared-library")) {
            Path root = backend.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    if (file.getFileName().toString().equals("IdentityAuthorizationClient.java")) {
                        continue;
                    }
                    if (Files.readString(file).contains(PATH)) {
                        offenders.add(backend.relativize(file).toString());
                    }
                }
            }
        }
        assertThat(offenders).as("call it through IdentityAuthorizationClient").isEmpty();
    }
}
