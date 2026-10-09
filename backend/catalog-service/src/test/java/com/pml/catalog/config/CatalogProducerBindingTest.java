package com.pml.catalog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Azure Service Bus binder cannot create a producer without being told whether the destination
 * is a queue or a topic. Without it every outbox send fails with "Entity type cannot be null", the
 * row goes back to PENDING and nothing is ever published.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001 · the catalog outbox binding declares its Service Bus entity type")
class CatalogProducerBindingTest {

    @Test
    @SuppressWarnings("unchecked")
    void outboxBindingIsATopicProducer() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> stream = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("spring")).get("cloud")).get("stream");
            Map<String, Object> bindings = (Map<String, Object>) ((Map<String, Object>) stream.get("servicebus")).get("bindings");
            Map<String, Object> binding = (Map<String, Object>) bindings.get("catalogEvents-out-0");
            Map<String, Object> producer = (Map<String, Object>) binding.get("producer");
            assertThat(producer.get("entity-type")).isEqualTo("topic");
        }
    }
}
