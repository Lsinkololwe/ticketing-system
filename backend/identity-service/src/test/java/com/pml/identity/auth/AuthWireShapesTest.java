package com.pml.identity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.auth.challenge.ChallengeKeys;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.web.AuthDtos;
import com.pml.identity.domain.enums.ContactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Wire names, key names and what the value objects print (CONTRACT 4 and 6). Pure: L1. */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · wire shapes follow the contract and value objects never print a contact or a code")
class AuthWireShapesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("the ensure answer names isNew, and leaves loginHandle out unless one was issued")
    void ensureAnswerShape() {
        JsonNode withHandle = json.valueToTree(new AuthDtos.EnsureActiveResponse("acc-1", "ACTIVE", true, "handle"));
        assertThat(withHandle.get("isNew").asBoolean()).isTrue();
        assertThat(withHandle.get("loginHandle").asText()).isEqualTo("handle");

        JsonNode without = json.valueToTree(new AuthDtos.EnsureActiveResponse("acc-1", "ACTIVE", false, null));
        assertThat(without.has("loginHandle")).isFalse();
        assertThat(without.get("isNew").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("request objects print without the contact, the code, the proof or the handle")
    void requestsDoNotPrintSecrets() {
        var contact = new AuthDtos.ContactInput("+260971234567", "WHATSAPP");
        assertThat(contact.toString()).doesNotContain("971234567");
        assertThat(new AuthDtos.ChallengeRequest(contact, "ZM", "10.0.0.1", "dev", null).toString())
                .doesNotContain("971234567").doesNotContain("10.0.0.1");
        assertThat(new AuthDtos.VerifyRequest("id", "482913").toString()).doesNotContain("482913");
        assertThat(new AuthDtos.EnsureRequest("the-proof-value", "web", true, "Name", null).toString())
                .doesNotContain("the-proof-value").doesNotContain("Name");
        assertThat(new AuthDtos.RedeemRequest("the-handle-value", "web").toString()).doesNotContain("the-handle-value");
        assertThat(new AuthDtos.VerifyResponse("the-proof-value", "EMAIL", "j***@x.com", 120).toString())
                .doesNotContain("the-proof-value");
    }

    @Test
    @DisplayName("Redis keys are exactly the contract's names and carry only hashes and ids")
    void keyNames() {
        assertThat(ChallengeKeys.challenge("abc")).isEqualTo("ch:abc");
        assertThat(ChallengeKeys.attempts("abc")).isEqualTo("ch:att:abc");
        assertThat(ChallengeKeys.lock("abc")).isEqualTo("ch:lock:abc");
        assertThat(ChallengeKeys.cooldown("abc")).isEqualTo("ch:cool:abc");
        assertThat(ChallengeKeys.challengeId("id-1")).isEqualTo("chid:id-1");
    }

    @Test
    @DisplayName("captured messages keep the code for the test and print neither it nor the recipient")
    void capturedMessages() {
        CapturedMessages captured = new CapturedMessages();
        assertThat(captured.lastCodeTo("a@example.com")).isEmpty();
        assertThat(DeliveryChannel.nativeFor(ContactType.EMAIL)).isEqualTo(DeliveryChannel.EMAIL);
        assertThat(DeliveryChannel.nativeFor(ContactType.WHATSAPP)).isEqualTo(DeliveryChannel.WHATSAPP);
        var message = new CapturedMessages.Message(DeliveryChannel.EMAIL, "a@example.com", "135790", Instant.EPOCH);
        assertThat(message.toString()).doesNotContain("135790").doesNotContain("example");
    }
}
