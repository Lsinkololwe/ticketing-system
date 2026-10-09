package com.pml.identity.auth.web;

import com.pml.identity.account.ProofRecord;
import com.pml.identity.domain.enums.ContactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · request and proof types never print a contact, code, proof or handle")
class AuthDtosRedactionTest {

    @Test
    @DisplayName("toString of the auth DTOs leaves out every secret")
    void dtosAreRedacted() {
        String text = List.of(
                new AuthDtos.ContactInput("+260971234567", "WHATSAPP"),
                new AuthDtos.ChallengeRequest(new AuthDtos.ContactInput("jane@example.com", null), "ZM", "10.0.0.1", "dev-1", null),
                new AuthDtos.VerifyRequest("challenge-id", "123456"),
                new AuthDtos.VerifyResponse("proof-secret", "EMAIL", "j***@example.com", 120),
                new AuthDtos.EnsureRequest("proof-secret", "myticketzm-web", true, "Jane", List.of()),
                new AuthDtos.RedeemRequest("handle-secret", "myticketzm-web"))
                .toString();

        assertThat(text).doesNotContain("+260971234567", "jane@example.com", "123456", "proof-secret", "handle-secret", "10.0.0.1");
    }

    @Test
    @DisplayName("a proof prints its mask, never the encrypted contact")
    void proofIsRedacted() {
        ProofRecord proof = new ProofRecord("p-1", "contact-key", ContactType.EMAIL, "ENCRYPTED-BLOB", "j***@example.com", "NEW", null);
        assertThat(proof.toString()).contains("j***@example.com").doesNotContain("ENCRYPTED-BLOB", "contact-key");
    }
}
