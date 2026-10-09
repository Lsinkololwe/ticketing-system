package com.pml.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The presentation rule on its own: which keys it refuses, and that the seed obeys it. */
@Tag("L1")
@Tag("ET-PLT-014")
@DisplayName("Reference metadata may not describe how a value looks")
class ReferencePresentationRuleTest {

    private final ReferenceMetadataValidator validator = new ReferenceMetadataValidator();

    @Test
    @DisplayName("every presentation key is refused by name, whatever the type")
    void presentationKeysAreRefused() {
        ReferenceMetadataValidator.PRESENTATION_KEYS.forEach(key ->
                assertThatThrownBy(() -> validator.validate(ReferenceType.EVENT_CATEGORY, Map.of(key, "x")))
                        .isInstanceOfSatisfying(ValidationRefusal.class, refused -> assertThat(refused.violations())
                                .containsExactly(new FieldViolation("input.metadata." + key, "NotPresentation"))));
    }

    @Test
    @DisplayName("a type's own metadata contract is unaffected")
    void dataKeysPass() {
        assertThatCode(() -> validator.validate(ReferenceType.MOBILE_MONEY_OPERATOR,
                Map.of("providerCode", "MTN_MOMO_ZMB", "msisdnPrefixes", java.util.List.of("26096"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the seed carries no presentation key on any row")
    void seedIsPlain() throws Exception {
        try (InputStream seed = new ClassPathResource("seed/reference-data.json").getInputStream()) {
            JsonNode rows = new ObjectMapper().readTree(seed);
            rows.forEach(row -> ReferenceMetadataValidator.PRESENTATION_KEYS.forEach(key ->
                    assertThat(row.path("metadata").has(key)).as(row.path("code").asText() + " " + key).isFalse()));
        }
    }
}
