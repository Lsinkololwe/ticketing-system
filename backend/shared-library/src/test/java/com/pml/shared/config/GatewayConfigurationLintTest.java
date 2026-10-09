package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is configured in the namespace the gateway actually reads.
 *
 * <h2>The failure mode</h2>
 * {@code spring-cloud-starter-gateway-server-webflux} reads
 * {@code spring.cloud.gateway.server.webflux.*}. Configuration written under the older
 * {@code spring.cloud.gateway.*} is not rejected — it is <em>ignored</em>. The application
 * starts, the actuator is healthy, and the gateway has no routes, no rate limiter and no
 * circuit breaker. CLAUDE.md's troubleshooting section lists exactly this ("Gateway routes not
 * loading"), which is the kind of entry that only gets written after someone has lost an
 * afternoon to it.
 *
 * <p>A misplaced key is invisible to every other test in the suite, because nothing fails —
 * that is precisely why it is worth a lint rather than a review comment.</p>
 *
 * <h2>Scope</h2>
 * Only the gateway module. {@code spring.cloud.azure.*} and {@code spring.cloud.stream.*} are
 * different namespaces belonging to different starters and are not touched here.
 */
@Tag("L1")
@Tag("ET-PLT-001")
@DisplayName("ET-PLT-001-R7 · gateway config is in the namespace the WebFlux gateway reads")
class GatewayConfigurationLintTest {

    private static final Path GATEWAY_CONFIG =
            Path.of("../api-gateway/src/main/resources/application.yml");

    private static final Path GATEWAY_POM = Path.of("../api-gateway/pom.xml");

    @Test
    @DisplayName("gateway settings sit under spring.cloud.gateway.server.webflux")
    void routesAreInTheWebfluxNamespace() throws IOException {
        List<String> lines = Files.readAllLines(GATEWAY_CONFIG);

        int gatewayLine = indentedKeyLine(lines, "gateway:", 4);
        assertThat(gatewayLine)
                .as("no `gateway:` key under spring.cloud — the file moved and this lint checked nothing")
                .isGreaterThan(0);

        // The very next non-blank, non-comment line must be `server:`. Anything else is a
        // setting the WebFlux gateway will never read.
        String next = nextMeaningfulLine(lines, gatewayLine);
        assertThat(next)
                .as("""
                    The first key under spring.cloud.gateway must be `server:` (then `webflux:`). \
                    Keys placed directly under spring.cloud.gateway are silently ignored by \
                    spring-cloud-starter-gateway-server-webflux: the app starts clean with no \
                    routes at all, which reads as a routing bug rather than a config one.""")
                .isEqualTo("server:");

        assertThat(String.join("\n", lines))
                .as("the webflux sub-namespace must be present")
                .contains("webflux:");
    }

    @Test
    @DisplayName("the gateway uses the WebFlux starter, not the deprecated generic one")
    void usesTheWebfluxStarter() throws IOException {
        String pom = Files.readString(GATEWAY_POM);

        assertThat(pom)
                .as("the reactive gateway starter is required — the generic one pulls a servlet "
                        + "stack the parent's enforcer bans anyway")
                .contains("spring-cloud-starter-gateway-server-webflux");

        assertThat(pom)
                .as("spring-cloud-starter-gateway is deprecated; its presence alongside the "
                        + "WebFlux starter is how the wrong namespace starts working by accident")
                .doesNotContain("<artifactId>spring-cloud-starter-gateway</artifactId>");
    }

    // --------------------------------------------------------------------- helpers

    /** Line number (1-based) of a key at the given indent, or -1. */
    private static int indentedKeyLine(List<String> lines, String key, int indent) {
        String target = " ".repeat(indent) + key;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(target)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String nextMeaningfulLine(List<String> lines, int afterLineNumber) {
        List<String> skipped = new ArrayList<>();
        for (int i = afterLineNumber; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                skipped.add(stripped);
                continue;
            }
            return stripped;
        }
        return "";
    }
}
