package com.pml.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * No service configuration sets an empty Service Bus namespace.
 *
 * <p>An empty {@code spring.cloud.azure.servicebus.namespace} still counts as set: it takes precedence over the
 * connection string and the sender is pointed at {@code .servicebus.windows.net}. Against the emulator every send then
 * times out, and against Azure it cannot authenticate. Leaving the key out lets the connection string decide.</p>
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("no application config sets spring.cloud.azure.servicebus.namespace to an empty value")
class NoBlankServiceBusNamespaceLintTest {

    @Test
    @DisplayName("the key is absent or has a value in every module's config")
    @SuppressWarnings("unchecked")
    void noEmptyNamespace() throws IOException {
        Path backend = Path.of("").toAbsolutePath();
        while (backend != null && !Files.isRegularFile(backend.resolve("shared-library/pom.xml"))) {
            backend = backend.getParent();
        }
        assertThat(backend).as("backend root").isNotNull();
        List<String> offenders = new ArrayList<>();
        int inspected = 0;
        try (Stream<Path> modules = Files.list(backend)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(resources)) {
                    for (Path file : files.filter(f -> f.getFileName().toString().matches("application(-.+)?\\.ya?ml")).toList()) {
                        inspected++;
                        for (Object document : new Yaml().loadAll(Files.readString(file))) {
                            Object current = document;
                            for (String key : new String[] {"spring", "cloud", "azure", "servicebus"}) {
                                current = current instanceof Map<?, ?> m ? m.get(key) : null;
                            }
                            if (current instanceof Map<?, ?> servicebus && servicebus.containsKey("namespace")) {
                                Object value = servicebus.get("namespace");
                                if (value == null || value.toString().isBlank()) {
                                    offenders.add(backend.relativize(file).toString());
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(inspected).as("config files inspected").isGreaterThan(3);
        assertThat(offenders).as("an empty namespace overrides the connection string").isEmpty();
    }
}
