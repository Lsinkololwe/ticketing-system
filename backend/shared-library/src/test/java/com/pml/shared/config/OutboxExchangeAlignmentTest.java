package com.pml.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every service's outbox drains to the exchange the event registry declares for it, and that
 * exchange is one the platform topology declares.
 *
 * <h2>The defect class</h2>
 * {@code platform.outbox.exchange} is a string, and so is every exchange name in
 * {@code EventTopology}. When they differ, every piece still starts: the outbox stages rows, the
 * drain claims them, and the broker is asked to publish to an exchange nobody declared. Each part is
 * individually correct and the message never reaches a subscription.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · each service's outbox publishes on its declared §4 exchange")
class OutboxExchangeAlignmentTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Map<String, String> SECTION_4_EXCHANGES = Map.of(
            "catalog-service", "catalog-events",
            "booking-service", "booking-events",
            "identity-service", "identity-events");

    private static final Pattern OUTBOX_EXCHANGE = Pattern.compile(
            "(?m)^\\s{2}outbox:\\s*\\n(?:\\s{4,}.*\\n)*?\\s{4}exchange:\\s*(\\S+)");

    @Test
    @DisplayName("the outbox exchange is the §4 exchange, and the platform topology declares it")
    void outboxDrainsToTheDeclaredExchange() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> entry : SECTION_4_EXCHANGES.entrySet()) {
            String service = entry.getKey();
            String expected = entry.getValue();
            Path config = BACKEND_ROOT.resolve(service).resolve("src/main/resources/application.yml");
            String body = withoutComments(Files.readString(config));

            Matcher outbox = OUTBOX_EXCHANGE.matcher(body);
            if (!outbox.find()) {
                problems.add(service + " configures no platform.outbox.exchange, so nothing it stages is drained");
                continue;
            }
            String exchange = outbox.group(1);
            if (!exchange.equals(expected)) {
                problems.add("%s drains its outbox to %s; §4 declares %s".formatted(service, exchange, expected));
            }
            if (!com.pml.shared.event.EventTopology.EXCHANGES.contains(exchange)) {
                problems.add("%s drains to %s, which the platform topology does not declare"
                        .formatted(service, exchange));
            }
        }

        assertThat(problems)
                .as("a drain that sends to an undeclared exchange stages, claims and loses every message")
                .isEmpty();
    }

    @Test
    @DisplayName("the matcher finds an exchange inside an outbox block and ignores one outside it")
    void theMatcherHasTeeth() {
        String aligned = """
                platform:
                  outbox:
                    collection: x_outbox
                    exchange: x-events
                """;
        String elsewhere = """
                platform:
                  events:
                    exchange: x-events
                """;

        Matcher found = OUTBOX_EXCHANGE.matcher(aligned);
        assertThat(found.find()).isTrue();
        assertThat(found.group(1)).isEqualTo("x-events");
        assertThat(OUTBOX_EXCHANGE.matcher(elsewhere).find()).isFalse();
    }

    private static String withoutComments(String yaml) {
        return yaml.replaceAll("(?m)^\\s*#.*\\n", "");
    }
}
