package com.pml.identity.auth.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R5 · contact notices are fixed words with no personal data, one per kind")
class ContactNoticeTest {

    @Test
    @DisplayName("no notice carries an address, a number, a digit or a placeholder, and each tells the person what to do if it was not them")
    void fixedWords() {
        for (ContactNotice notice : ContactNotice.values()) {
            assertThat(notice.subject()).isNotBlank().doesNotContain("@").doesNotContain("{").doesNotContain("%");
            assertThat(notice.text()).doesNotContain("@").doesNotContainPattern("\\d").doesNotContain("{").doesNotContain("%")
                    .contains("If this was not you");
        }
        assertThat(ContactNotice.values()).extracting(Enum::name)
                .containsExactly("CONTACT_ADDED", "CONTACT_CHANGED", "CONTACT_REMOVED", "PRIMARY_CHANGED");
    }
}
