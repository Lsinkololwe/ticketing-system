package com.pml.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ET-PLT-014-R9 · a reference list that mirrors an API enum cannot drift from it.
 *
 * <p>Organizer types, legal business types, roles, notification channels and ticket categories are lists the
 * frontend used to hardcode. They are reference rows now, so labels and descriptions are the platform's to
 * edit, but the CODES are the API contract: identity and catalog accept only the enum's members. These tests
 * read the enums from the services' own schemas and the document requirements from identity's source, so a
 * member added to one side and not the other fails here rather than at an organizer's submit.
 */
@Tag("L4")
@Tag("ET-PLT-014")
@DisplayName("ET-PLT-014-R9 · reference rows that mirror an API enum carry exactly its members")
class ReferenceSeedContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path IDENTITY = Path.of("../identity-service/src/main");
    private static final Path CATALOG = Path.of("src/main");

    private static JsonNode v2Rows() throws Exception {
        return JSON.readTree(Files.readString(CATALOG.resolve("resources/seed/reference-data-v2.json"))).path("rows");
    }

    private static Set<String> seeded(String type) throws Exception {
        Set<String> codes = new LinkedHashSet<>();
        v2Rows().forEach(row -> {
            if (row.path("type").asText().equals(type)) {
                codes.add(row.path("code").asText());
            }
        });
        return codes;
    }

    private static Set<String> enumMembers(Path schema, String name) throws Exception {
        Matcher block = Pattern.compile("enum " + name + " \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(Files.readString(schema));
        assertThat(block.find()).as("enum " + name + " in " + schema).isTrue();
        Set<String> members = new LinkedHashSet<>();
        for (String line : block.group(1).split("\n")) {
            String member = line.replaceAll("#.*", "").trim();
            if (!member.isEmpty()) {
                members.add(member);
            }
        }
        return members;
    }

    private static Path identitySchema() {
        return IDENTITY.resolve("resources/graphql/schema.graphqls");
    }

    private static void requireIdentity() {
        assumeTrue(Files.isRegularFile(identitySchema()), "identity-service sources not beside catalog-service");
    }

    @Test
    @DisplayName("ORGANIZER_TYPE, BUSINESS_TYPE, ORGANIZATION_ROLE, EVENT_ROLE and NOTIFICATION_CHANNEL equal identity's enums")
    void identityEnums() throws Exception {
        requireIdentity();
        assertThat(seeded("ORGANIZER_TYPE")).isEqualTo(enumMembers(identitySchema(), "OrganizationType"));
        assertThat(seeded("BUSINESS_TYPE")).isEqualTo(enumMembers(identitySchema(), "BusinessType"));
        assertThat(seeded("ORGANIZATION_ROLE")).isEqualTo(enumMembers(identitySchema(), "OrganizationRole"));
        assertThat(seeded("EVENT_ROLE")).isEqualTo(enumMembers(identitySchema(), "EventRole"));
        assertThat(seeded("NOTIFICATION_CHANNEL")).isEqualTo(enumMembers(identitySchema(), "NotificationChannel"));
    }

    @Test
    @DisplayName("TICKET_TIER_CATEGORY equals the TicketCategory enum of the catalog schema")
    void ticketCategories() throws Exception {
        assertThat(seeded("TICKET_TIER_CATEGORY"))
                .isEqualTo(enumMembers(CATALOG.resolve("resources/graphql/schema.graphqls"), "TicketCategory"));
    }

    @Test
    @DisplayName("every notification preference key names a field of identity's NotificationPreferences")
    void preferenceKeys() throws Exception {
        requireIdentity();
        Matcher block = Pattern.compile("type NotificationPreferences \\{(.*?)\\n\\}", Pattern.DOTALL)
                .matcher(Files.readString(identitySchema()));
        assertThat(block.find()).isTrue();
        Set<String> fields = new LinkedHashSet<>();
        Matcher field = Pattern.compile("(?m)^\\s+(\\w+):").matcher(block.group(1));
        while (field.find()) {
            fields.add(field.group(1));
        }
        List<String> keys = new ArrayList<>();
        v2Rows().forEach(row -> {
            String type = row.path("type").asText();
            if (type.equals("NOTIFICATION_CHANNEL") || type.equals("NOTIFICATION_CATEGORY")) {
                keys.add(row.path("metadata").path("preferenceKey").asText());
            }
        });
        assertThat(keys).hasSize(12);
        assertThat(fields).containsAll(keys);
    }

    @Test
    @DisplayName("KYB_DOCUMENT_TYPE and each legal type's requiredDocuments equal identity's RequiredDocuments table")
    void requiredDocuments() throws Exception {
        requireIdentity();
        String source = Files.readString(IDENTITY.resolve("java/com/pml/identity/domain/valueobject/RequiredDocuments.java"));

        Set<String> constants = new LinkedHashSet<>();
        Matcher constant = Pattern.compile("public static final String (\\w+) = \"(\\w+)\";").matcher(source);
        while (constant.find()) {
            constants.add(constant.group(2));
        }
        assertThat(seeded("KYB_DOCUMENT_TYPE")).isEqualTo(constants);

        Map<String, List<String>> table = new LinkedHashMap<>();
        Matcher put = Pattern.compile("REQUIRED\\.put\\(BusinessType\\.(\\w+),\\s*List\\.of\\(([^)]*)\\)\\)").matcher(source);
        while (put.find()) {
            table.put(put.group(1), List.of(put.group(2).replaceAll("\\s+", "").split(",")));
        }
        assertThat(table).as("the table was not parsed").hasSize(6);
        Map<String, List<String>> seededTable = new LinkedHashMap<>();
        v2Rows().forEach(row -> {
            if (row.path("type").asText().equals("BUSINESS_TYPE")) {
                List<String> docs = new ArrayList<>();
                row.path("metadata").path("requiredDocuments").forEach(d -> docs.add(d.asText()));
                seededTable.put(row.path("code").asText(), docs);
            }
        });
        // identity names constants (NATIONAL_ID = "NATIONAL_ID"); the seed names the values, the same strings
        assertThat(seededTable.keySet()).isEqualTo(table.keySet());
        seededTable.forEach((type, docs) -> assertThat(docs).as(type).containsExactlyElementsOf(table.get(type)));
    }
}
