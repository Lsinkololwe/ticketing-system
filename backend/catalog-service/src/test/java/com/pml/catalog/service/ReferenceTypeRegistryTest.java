package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ET-PLT-014-R9 · the vocabularies the frontends used to hardcode are compiled reference types with a contract. */
@Tag("L1")
@Tag("ET-PLT-014")
@DisplayName("ET-PLT-014-R9 · onboarding, access, communication and reporting vocabularies are reference types")
class ReferenceTypeRegistryTest {

    private final ReferenceMetadataValidator validator = new ReferenceMetadataValidator();

    @Test
    @DisplayName("each new type exists, is grouped for the admin picker, and is not a workflow")
    void newTypesExist() {
        for (String name : new String[] {"ORGANIZER_TYPE", "BUSINESS_TYPE", "TICKET_TIER_CATEGORY", "ORGANIZATION_ROLE",
                "EVENT_ROLE", "NOTIFICATION_CHANNEL", "NOTIFICATION_CATEGORY", "REPORT_PERIOD"}) {
            ReferenceType type = ReferenceType.valueOf(name);
            assertThat(type.isWorkflow()).as(name).isFalse();
            assertThat(type.getGroup().getLabel()).as(name).isNotBlank();
        }
    }

    @Test
    @DisplayName("a legal business type without its document list is refused; a role needs `invitable`")
    void metadataContracts() {
        assertThat(validator.requiredKeys(ReferenceType.BUSINESS_TYPE)).containsExactly("requiredDocuments");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> validator.validate(ReferenceType.BUSINESS_TYPE, java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requiredDocuments");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> validator.validate(ReferenceType.ORGANIZATION_ROLE, java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invitable");
        validator.validate(ReferenceType.ORGANIZATION_ROLE, java.util.Map.of("invitable", false));
    }
}
