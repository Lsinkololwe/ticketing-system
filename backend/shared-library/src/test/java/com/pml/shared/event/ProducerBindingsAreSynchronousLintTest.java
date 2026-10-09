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
 * Every Service Bus producer binding sends synchronously.
 *
 * <p>The Azure binder's producers are asynchronous by default: {@code StreamBridge.send} returns
 * true as soon as the message is handed to the sender and a failure afterwards is only logged. The
 * outbox marks a row sent when {@code send} says so, so with an asynchronous producer a bus that is
 * unreachable (not yet started, restarting, or a broken link) silently drops every event while the
 * outbox reports success. A synchronous producer throws instead, the row stays pending, and the
 * drain retries it.</p>
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("every Service Bus producer binding is synchronous, so a failed send keeps the outbox row pending")
class ProducerBindingsAreSynchronousLintTest {

    private static final Path BACKEND = locateBackend();

    @Test
    @DisplayName("each producer binding declares sync: true")
    @SuppressWarnings("unchecked")
    void everyProducerIsSynchronous() throws IOException {
        List<String> offenders = new ArrayList<>();
        int producers = 0;
        try (Stream<Path> modules = Files.list(BACKEND)) {
            for (Path config : modules.map(m -> m.resolve("src/main/resources/application.yml")).filter(Files::isRegularFile).toList()) {
                for (Object document : new Yaml().loadAll(Files.readString(config))) {
                    Map<String, Object> bindings = path((Map<String, Object>) document,
                            "spring", "cloud", "stream", "servicebus", "bindings");
                    if (bindings == null) {
                        continue;
                    }
                    for (Map.Entry<String, Object> binding : bindings.entrySet()) {
                        Map<String, Object> producer = path((Map<String, Object>) binding.getValue(), "producer");
                        if (producer == null) {
                            continue;
                        }
                        producers++;
                        if (!Boolean.TRUE.equals(producer.get("sync"))) {
                            offenders.add(BACKEND.relativize(config) + " :: " + binding.getKey());
                        }
                    }
                }
            }
        }
        assertThat(producers).as("the services declare producer bindings; none found means this test is looking in the wrong place")
                .isGreaterThanOrEqualTo(3);
        assertThat(offenders).as("an asynchronous producer reports success before the bus has accepted the message").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> path(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(key)) {
                return null;
            }
            current = map.get(key);
        }
        return current instanceof Map<?, ?> ? (Map<String, Object>) current : null;
    }

    private static Path locateBackend() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isRegularFile(here.resolve("pom.xml").resolveSibling("shared-library/pom.xml"))) {
            here = here.getParent();
        }
        return here != null ? here : Path.of("").toAbsolutePath().getParent();
    }
}
