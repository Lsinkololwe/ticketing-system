package com.pml.identity.account;

import com.pml.identity.web.graphql.dto.ResendContactCodeInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004-R3 · the change kinds and resend targets the schema names")
class ContactChangeKindTest {

    @Test
    @DisplayName("the four change kinds and the two resend targets are exactly those of the GraphQL enums")
    void enumsMatchTheSchema() {
        assertThat(ContactChangeKind.values()).extracting(Enum::name).containsExactly("ADD", "CHANGE", "REMOVE", "PRIMARY");
        assertThat(ResendContactCodeInput.ContactCodeTarget.values()).extracting(Enum::name).containsExactly("NEW", "CURRENT");
    }

    @Test
    @DisplayName("an input's toString hides the contact value and the code")
    void inputsHideSecrets() {
        assertThat(new com.pml.identity.web.graphql.dto.RequestContactAddInput(
                com.pml.identity.domain.enums.ContactType.EMAIL, "someone@example.com", null).toString()).doesNotContain("someone");
        assertThat(new com.pml.identity.web.graphql.dto.ConfirmContactAddInput("chal-1", "123456").toString()).doesNotContain("123456");
    }
}
