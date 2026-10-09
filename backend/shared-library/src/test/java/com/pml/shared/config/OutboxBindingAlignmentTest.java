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
 * Every service's outbox drains to the binding the event registry declares for it.
 *
 * <h2>The defect class</h2>
 * {@code platform.outbox.binding} and {@code spring.cloud.stream.bindings} are two strings in two
 * places of one file. When they differ, every piece still starts: the outbox stages rows, the drain
 * claims them, and {@code StreamBridge} sends to a name with no configured destination. Each part
 * is individually correct and the message never reaches the topic. {@code EventPublicationLintTest}
 * checks that the registry's binding is declared; this checks that the outbox actually uses it.
 */
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · each service's outbox publishes on its declared §4 binding")
class OutboxBindingAlignmentTest {

    private static final Path BACKEND_ROOT = Path.of("..");

    private static final Map<String, String> SECTION_4_BINDINGS = Map.of(
            "catalog-service", "catalogEvents-out-0",
            "booking-service", "bookingEvents-out-0",
            "identity-service", "identityEvents-out-0");

    private static final Pattern OUTBOX_BINDING = Pattern.compile(
            "(?m)^\\s{2}outbox:\\s*\\n(?:\\s{4,}.*\\n)*?\\s{4}binding:\\s*(\\S+)");

    @Test
    @DisplayName("the outbox binding is the §4 binding, and that binding has a destination")
    void outboxDrainsToTheDeclaredBinding() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, String> entry : SECTION_4_BINDINGS.entrySet()) {
            String service = entry.getKey();
            String expected = entry.getValue();
            Path config = BACKEND_ROOT.resolve(service).resolve("src/main/resources/application.yml");
            String body = withoutComments(Files.readString(config));

            Matcher outbox = OUTBOX_BINDING.matcher(body);
            if (!outbox.find()) {
                problems.add(service + " configures no platform.outbox.binding, so nothing it stages is drained");
                continue;
            }
            String binding = outbox.group(1);
            if (!binding.equals(expected)) {
                problems.add("%s drains its outbox to %s; §4 declares %s".formatted(service, binding, expected));
            }

            int declared = body.indexOf("\n        " + binding + ":");
            if (declared < 0) {
                problems.add("%s drains to %s, which spring.cloud.stream.bindings does not declare"
                        .formatted(service, binding));
                continue;
            }
            String following = body.substring(declared, Math.min(body.length(), declared + 160));
            if (!following.contains("destination:")) {
                problems.add("%s declares %s without a destination".formatted(service, binding));
            }
        }

        assertThat(problems)
                .as("a drain that sends to an undeclared binding stages, claims and loses every message")
                .isEmpty();
    }

    @Test
    @DisplayName("the matcher finds a binding inside an outbox block and ignores one outside it")
    void theMatcherHasTeeth() {
        String aligned = """
                platform:
                  outbox:
                    collection: x_outbox
                    binding: xEvents-out-0
                """;
        String elsewhere = """
                platform:
                  events:
                    binding: xEvents-out-0
                """;

        Matcher found = OUTBOX_BINDING.matcher(aligned);
        assertThat(found.find()).isTrue();
        assertThat(found.group(1)).isEqualTo("xEvents-out-0");
        assertThat(OUTBOX_BINDING.matcher(elsewhere).find()).isFalse();
    }

    private static String withoutComments(String yaml) {
        return yaml.replaceAll("(?m)^\\s*#.*\\n", "");
    }
}
