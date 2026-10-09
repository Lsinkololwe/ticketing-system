package com.pml.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.service.referencedata.CountryCatalogue;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-PLT-014-R12 · the reference data the platform ships is well formed before it reaches a database:
 * every row names a compiled type, satisfies that type's metadata contract, carries no presentation, and
 * no code appears twice. The countries come from libphonenumber, not a file.
 */
@Tag("L1")
@Tag("ET-PLT-014")
@DisplayName("ET-PLT-014-R12 · the shipped reference data satisfies each type's contract")
class ReferenceSeedReleaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_./+-]{1,60}$");
    private final ReferenceMetadataValidator validator = new ReferenceMetadataValidator();

    private static JsonNode read(String resource) throws Exception {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return JSON.readTree(in);
        }
    }

    private static List<JsonNode> rows(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        node.forEach(out::add);
        return out;
    }

    @Test
    @DisplayName("every v2 row names a compiled type, a valid code, and passes the type's metadata validator")
    void v2RowsAreValid() throws Exception {
        JsonNode v2 = read("seed/reference-data-v2.json");
        assertThat(v2.path("version").asInt()).isEqualTo(2);
        for (JsonNode row : rows(v2.path("rows"))) {
            String label = row.path("type").asText() + ":" + row.path("code").asText();
            ReferenceType type = ReferenceType.valueOf(row.path("type").asText());
            assertThat(CODE.matcher(row.path("code").asText()).matches()).as(label + " code").isTrue();
            assertThat(row.path("name").asText()).as(label + " name").isNotBlank();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = JSON.convertValue(row.path("metadata"), Map.class);
            validator.validate(type, metadata == null ? Map.of() : metadata);
        }
    }

    @Test
    @DisplayName("no (type, code) appears twice across the two seed files")
    void noDuplicates() throws Exception {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (JsonNode row : rows(read("seed/reference-data.json"))) {
            if (!seen.add(row.path("type").asText() + ":" + row.path("code").asText())) {
                duplicates.add(row.path("type").asText() + ":" + row.path("code").asText());
            }
        }
        for (JsonNode row : rows(read("seed/reference-data-v2.json").path("rows"))) {
            if (!seen.add(row.path("type").asText() + ":" + row.path("code").asText())) {
                duplicates.add(row.path("type").asText() + ":" + row.path("code").asText());
            }
        }
        assertThat(duplicates).as("a row owned by two seed files flips on every boot").isEmpty();
    }

    @Test
    @DisplayName("the types moved to v2 are not also in the boot-time seed")
    void movedTypesAreNotReseededOnEveryBoot() throws Exception {
        Set<String> v1Types = rows(read("seed/reference-data.json")).stream()
                .map(row -> row.path("type").asText()).collect(Collectors.toSet());
        assertThat(v1Types).doesNotContain("COUNTRY", "KYB_DOCUMENT_TYPE", "MOBILE_MONEY_OPERATOR");
    }

    @Test
    @DisplayName("each legal business type requires only documents that exist, and the retired document codes are gone from the rows")
    void requiredDocumentsExist() throws Exception {
        JsonNode v2 = read("seed/reference-data-v2.json");
        Set<String> documents = new HashSet<>();
        Map<String, List<String>> required = new HashMap<>();
        for (JsonNode row : rows(v2.path("rows"))) {
            if (row.path("type").asText().equals("KYB_DOCUMENT_TYPE")) {
                documents.add(row.path("code").asText());
            }
            if (row.path("type").asText().equals("BUSINESS_TYPE")) {
                List<String> docs = new ArrayList<>();
                row.path("metadata").path("requiredDocuments").forEach(d -> docs.add(d.asText()));
                required.put(row.path("code").asText(), docs);
            }
        }
        assertThat(required).hasSize(6);
        required.forEach((type, docs) -> assertThat(documents).as(type).containsAll(docs));
        for (JsonNode retired : rows(v2.path("retire"))) {
            assertThat(documents).doesNotContain(retired.path("code").asText());
        }
    }

    @Test
    @DisplayName("Zambia's ten provinces and 116 districts are seeded, every district under a seeded province")
    void zambianGeography() throws Exception {
        Set<String> provinces = rows(read("seed/reference-data.json")).stream()
                .filter(r -> r.path("type").asText().equals("PROVINCE")).map(r -> r.path("code").asText())
                .collect(Collectors.toSet());
        assertThat(provinces).hasSize(10);
        Set<String> cities = new HashSet<>();
        List<JsonNode> all = new ArrayList<>(rows(read("seed/reference-data.json")));
        all.addAll(rows(read("seed/reference-data-v2.json").path("rows")));
        for (JsonNode row : all) {
            if (row.path("type").asText().equals("CITY")) {
                cities.add(row.path("code").asText());
                assertThat(provinces).as(row.path("code").asText() + " parent").contains(row.path("parentCode").asText());
            }
        }
        assertThat(cities).hasSizeGreaterThanOrEqualTo(116);
        assertThat(cities).contains("LUSAKA", "KITWE", "LIVINGSTONE", "MPIKA", "SIOMA", "SHIWANGANDU", "ITEZHI_TEZHI");
    }

    @Test
    @DisplayName("mobile money prefixes are the real Zambian ranges: MTN 096/076, Airtel 097/077, Zamtel 095/055")
    void mobileMoneyPrefixes() throws Exception {
        Map<String, List<String>> prefixes = new HashMap<>();
        for (JsonNode row : rows(read("seed/reference-data-v2.json").path("rows"))) {
            if (row.path("type").asText().equals("MOBILE_MONEY_OPERATOR")) {
                List<String> p = new ArrayList<>();
                row.path("metadata").path("msisdnPrefixes").forEach(x -> p.add(x.asText()));
                prefixes.put(row.path("code").asText(), p);
            }
        }
        assertThat(prefixes.get("MTN")).containsExactlyInAnyOrder("26096", "26076");
        assertThat(prefixes.get("AIRTEL")).containsExactlyInAnyOrder("26097", "26077");
        assertThat(prefixes.get("ZAMTEL")).containsExactlyInAnyOrder("26095", "26055");
    }

    @Test
    @DisplayName("the country list is libphonenumber's: every supported region with a calling code, Zambia first")
    void countriesComeFromLibphonenumber() {
        List<Map<String, Object>> countries = CountryCatalogue.rows();
        PhoneNumberUtil phones = PhoneNumberUtil.getInstance();
        Set<String> codes = countries.stream().map(c -> (String) c.get("code")).collect(Collectors.toSet());

        assertThat(codes).hasSize(countries.size());
        assertThat(codes).contains("ZM", "ZW", "US", "GB", "ZA", "IN", "KE");
        assertThat(phones.getSupportedRegions()).containsAll(codes);
        assertThat(countries.size()).as("about 245 supported regions").isGreaterThan(230);
        assertThat(countries.get(0).get("code")).isEqualTo("ZM");
        @SuppressWarnings("unchecked")
        Map<String, Object> zambia = (Map<String, Object>) countries.get(0).get("metadata");
        assertThat(zambia).containsEntry("dialCode", "+260").containsEntry("iso3", "ZMB");
        assertThat(countries.get(0).get("name")).isEqualTo("Zambia");
        countries.forEach(c -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> md = (Map<String, Object>) c.get("metadata");
            validator.validate(ReferenceType.COUNTRY, md);
            assertThat((String) md.get("dialCode")).matches("\\+\\d{1,3}");
        });
    }
}
