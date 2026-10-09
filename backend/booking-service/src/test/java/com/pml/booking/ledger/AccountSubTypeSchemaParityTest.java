package com.pml.booking.ledger;

import com.pml.booking.domain.enums.AccountSubType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every sub-type the ledger can store must exist in the GraphQL enum. The chart of accounts is
 * seeded and posted from the Java enum; a value the schema does not know serialises as
 * "Invalid input for enum" and takes the whole chartOfAccounts query down with it.
 */
@Tag("L4")
@Tag("ET-FIN-001")
@DisplayName("ET-FIN-001 · every AccountSubType the ledger stores is in the GraphQL enum")
class AccountSubTypeSchemaParityTest {

    @Test
    void schemaEnumCoversEveryStoredSubType() throws Exception {
        String schema;
        try (InputStream in = getClass().getResourceAsStream("/graphql/schema.graphqls")) {
            schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher body = Pattern.compile("enum AccountSubType[^{]*\\{([^}]*)\\}").matcher(schema);
        assertThat(body.find()).isTrue();
        List<String> declared = Arrays.stream(body.group(1).split("\\R"))
                .map(line -> line.replaceAll("#.*", "").trim())
                .filter(line -> !line.isEmpty())
                .toList();
        assertThat(declared).containsAll(Arrays.stream(AccountSubType.values()).map(Enum::name).toList());
    }
}
