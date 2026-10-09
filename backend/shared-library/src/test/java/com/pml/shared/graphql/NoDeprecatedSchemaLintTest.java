package com.pml.shared.graphql;

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
 * No subgraph schema carries {@code @deprecated}.
 *
 * <p>Every deprecated element was removed at once, so the router's schema shows none. A field
 * kept "for now" is what lets a client keep selecting it, and the supergraph then advertises
 * an API nobody intends to support. A change that must be phased in belongs in a branch, not
 * in the schema.</p>
 */
@Tag("L4")
@Tag("ET-PLT-010")
@DisplayName("ET-PLT-010 · the subgraph schemas carry no @deprecated")
class NoDeprecatedSchemaLintTest {

    private static final List<String> SERVICES =
            List.of("identity-service", "catalog-service", "booking-service");

    private static Path backendRoot() {
        Path here = Path.of("").toAbsolutePath();
        return here.endsWith("shared-library") ? here.getParent() : here;
    }

    @Test
    @DisplayName("no field, argument, input field or enum value is deprecated")
    void noDeprecatedElements() throws IOException {
        List<String> found = new ArrayList<>();
        for (String service : SERVICES) {
            Path schema = backendRoot().resolve(service).resolve("src/main/resources/graphql/schema.graphqls");
            assertThat(schema).as("%s schema exists", service).exists();
            List<String> lines = Files.readAllLines(schema);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // A comment may still say the word; only the directive counts.
                if (line.contains("@deprecated") && !line.stripLeading().startsWith("#")) {
                    found.add(service + ":" + (i + 1) + " " + line.strip());
                }
            }
        }
        assertThat(found).as("remove the element instead of deprecating it").isEmpty();
    }
}
