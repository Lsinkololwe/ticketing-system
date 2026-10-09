package com.pml.catalog.web.graphql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The tier mutations' Java return types agree with the subgraph schema. */
@Tag("L4")
@Tag("ET-CAT-002")
@DisplayName("Tier mutations declare the TicketTier the schema promises")
class TicketTierSchemaParityTest {

    @Test
    @DisplayName("every tier mutation the schema types as TicketTier! is declared as one in the resolver")
    void resolverMatchesTheSchema() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));
        String resolverSource = Files.readString(Path.of(
                "src/main/java/com/pml/catalog/web/graphql/mutation/TicketTierMutationResolver.java"));
        for (String op : new String[]{"createTicketTier", "updateTicketTier", "activateTicketTier", "deactivateTicketTier"}) {
            assertThat(Pattern.compile(op + "\\([^)]*\\): TicketTier!").matcher(schema).find()).as(op).isTrue();
            assertThat(Pattern.compile("Mono<TicketTier> " + op + "\\(").matcher(resolverSource).find()).as(op).isTrue();
        }
    }
}
