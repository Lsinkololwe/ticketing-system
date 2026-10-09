package com.pml.gateway.config;

import com.pml.shared.error.DuplicateKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The browser-facing REST endpoints (verification documents, media upload) are only
 * reachable through the gateway if a route exists for them.
 *
 * <p>Without a route the request falls through to the static-resource handler: a 404 that the
 * error advice reported as a defect, and with the Mongo driver absent from this module the advice
 * itself crashed, so the call hung until the client timed out. Both halves are pinned here.</p>
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001 R4 · the gateway routes the REST API and survives error translation without a database driver")
class GatewayRestRoutesTest {

    @SuppressWarnings("unchecked")
    private static List<String> predicates() throws Exception {
        try (InputStream in = GatewayRestRoutesTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
            Map<String, Object> server = (Map<String, Object>) gateway.getOrDefault("server", Map.of());
            Map<String, Object> webflux = (Map<String, Object>) server.getOrDefault("webflux", gateway);
            List<Map<String, Object>> routes = (List<Map<String, Object>>) webflux.get("routes");
            return routes.stream()
                    .flatMap(route -> ((List<String>) route.get("predicates")).stream())
                    .toList();
        }
    }

    @Test
    @DisplayName("verification-document and media REST paths have routes")
    void restPathsAreRouted() throws Exception {
        String all = String.join(" | ", predicates());
        assertThat(all).contains("/api/v1/organizations/**", "/api/v1/documents/**", "/api/v1/media/**");
    }

    @Test
    @DisplayName("duplicate-key detection does not need the MongoDB driver")
    void duplicateKeysWithoutDriver() {
        assertThat(DuplicateKeys.isDuplicateKey(new IllegalStateException("boom"))).isFalse();
        assertThat(DuplicateKeys.describe(new IllegalStateException("boom"))).isEqualTo("boom");
    }
}
