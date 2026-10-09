package com.pml.identity.organization;

import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphQL binds an input to a record by field name, and a record component with no matching schema
 * field is silently null. This compares the settings input record with the schema's declaration.
 */
@Tag("L4")
@Tag("ET-ORG-003")
@DisplayName("The settings input record matches the schema input field for field")
class SettingsInputBindingTest {

    @Test
    @DisplayName("Every schema field has a record component of the same name, and nothing else does")
    void recordMatchesTheSchemaInput() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));
        Matcher block = Pattern.compile("input UpdateOrganizationSettingsInput \\{(.*?)\\}", Pattern.DOTALL).matcher(schema);
        assertThat(block.find()).isTrue();
        Set<String> schemaFields = new LinkedHashSet<>();
        Matcher field = Pattern.compile("^\\s*(\\w+)\\s*:", Pattern.MULTILINE).matcher(block.group(1));
        while (field.find()) {
            schemaFields.add(field.group(1));
        }
        Set<String> recordFields = new LinkedHashSet<>();
        Arrays.stream(UpdateOrganizationSettingsInput.class.getRecordComponents()).forEach(c -> recordFields.add(c.getName()));

        assertThat(recordFields).containsExactlyInAnyOrderElementsOf(schemaFields);
    }
}
