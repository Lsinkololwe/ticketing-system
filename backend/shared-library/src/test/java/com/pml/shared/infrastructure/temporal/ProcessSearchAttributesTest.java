package com.pml.shared.infrastructure.temporal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("L1")
@Tag("ET-PLT-015")
@DisplayName("ET-PLT-015 · process search attributes match docker-resources/temporal/self-hosted/search-attributes.conf")
class ProcessSearchAttributesTest {

    @Test
    @DisplayName("the Java names and Keyword type equal the registered attributes")
    void namesMatchTheRegistry() throws IOException {
        Path conf = locate();
        assumeTrue(conf != null, "docker-resources/temporal/self-hosted/search-attributes.conf not found from " + Path.of("").toAbsolutePath());
        Map<String, String> registered = new TreeMap<>();
        for (String line : Files.readAllLines(conf)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            registered.put(parts[0], parts[1]);
        }
        Map<String, String> java = new TreeMap<>();
        for (var key : new io.temporal.common.SearchAttributeKey<?>[]{ProcessSearchAttributes.BUSINESS_ID,
                ProcessSearchAttributes.TENANT_ID, ProcessSearchAttributes.ORGANIZATION_ID, ProcessSearchAttributes.EVENT_ID,
                ProcessSearchAttributes.BUSINESS_STATUS, ProcessSearchAttributes.PROCESS_KIND}) {
            java.put(key.getName(), key.getValueType().name());
        }
        assertThat(java.keySet()).as("the registered set and the Java constants drifted").isEqualTo(registered.keySet());
        assertThat(java.values()).allMatch(type -> type.endsWith("KEYWORD"));
        assertThat(registered.values()).allMatch(type -> type.equals("Keyword"));
    }

    @Test
    @DisplayName("the builder skips null and blank values")
    void builderSkipsNulls() {
        var attributes = ProcessSearchAttributes.of("Purchase", "r-1").eventId(null).tenantId(" ").organizationId("o-1")
                .build().toSearchAttributes();

        assertThat(attributes.get(ProcessSearchAttributes.BUSINESS_ID)).isEqualTo("r-1");
        assertThat(attributes.get(ProcessSearchAttributes.PROCESS_KIND)).isEqualTo("Purchase");
        assertThat(attributes.get(ProcessSearchAttributes.ORGANIZATION_ID)).isEqualTo("o-1");
        assertThat(attributes.containsKey(ProcessSearchAttributes.EVENT_ID)).isFalse();
        assertThat(attributes.containsKey(ProcessSearchAttributes.TENANT_ID)).isFalse();
    }

    private static Path locate() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolveSibling("docker-resources").resolve("temporal/self-hosted/search-attributes.conf");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
