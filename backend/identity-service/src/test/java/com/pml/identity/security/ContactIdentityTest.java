package com.pml.identity.security;

import com.pml.identity.domain.enums.ContactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contact hashing, masking and encryption (CONTRACT 3). Pure functions: L1. */
@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · contacts are hashed, masked and encrypted, never stored plain")
class ContactIdentityTest {

    private static final String AES_KEY = FieldEncryptionService.generateKey();

    private final ContactHasher hasher = new ContactHasher("unit-test-hash-key");

    @Test
    @DisplayName("every spelling of a Zambian number gives the same contact key; a different type does not")
    void sameNumberSameKey() {
        var a = hasher.normalize("0977123456", null, null, List.of("ZM")).orElseThrow();
        var b = hasher.normalize("+260 977 123 456", ContactType.WHATSAPP, null, List.of("ZM")).orElseThrow();
        var c = hasher.normalize("260977123456", null, null, null).orElseThrow();
        assertThat(a.key()).isEqualTo(b.key()).isEqualTo(c.key()).matches("[0-9a-f]{64}");
        assertThat(a.type()).isEqualTo(ContactType.WHATSAPP);
        assertThat(a.value()).isEqualTo("+260977123456");
        assertThat(a.masked()).isEqualTo("+260 97* ***456");
        assertThat(a.region()).isEqualTo("ZM");
    }

    @Test
    @DisplayName("emails are lower-cased before hashing, and a value with @ is an email when no type is given")
    void emailKey() {
        var a = hasher.normalize("  Jane.Doe@Example.COM ", null, null, null).orElseThrow();
        var b = hasher.normalize("jane.doe@example.com", ContactType.EMAIL, null, null).orElseThrow();
        assertThat(a.type()).isEqualTo(ContactType.EMAIL);
        assertThat(a.key()).isEqualTo(b.key());
        assertThat(a.masked()).isEqualTo("j***@example.com");
        assertThat(a.key()).isNotEqualTo(hasher.hash(ContactType.WHATSAPP, "jane.doe@example.com"));
    }

    @Test
    @DisplayName("the key depends on the secret")
    void keyDependsOnSecret() {
        String one = hasher.hash(ContactType.EMAIL, "a@b.co");
        assertThat(new ContactHasher("another-key").hash(ContactType.EMAIL, "a@b.co")).isNotEqualTo(one);
        assertThatThrownBy(() -> new ContactHasher("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuses invalid values, landlines and countries outside the allowlist")
    void refusals() {
        assertThat(hasher.normalize("not a contact", null, null, null)).isEmpty();
        assertThat(hasher.normalize("a@b", ContactType.EMAIL, null, null)).isEmpty();
        assertThat(hasher.normalize("+260211123456", null, null, null)).isEmpty();
        assertThat(hasher.normalize("+14155552671", null, null, List.of("ZM"))).isEmpty();
        assertThat(hasher.normalize(null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("a normalised contact never prints its value")
    void toStringHidesValue() {
        var n = hasher.normalize("+260977123456", null, null, null).orElseThrow();
        assertThat(n.toString()).doesNotContain("977123456").doesNotContain(n.key());
    }

    @Test
    @DisplayName("encrypt then decrypt returns the value; the ciphertext carries the key id and not the value")
    void cryptoRoundTrip() {
        ContactCrypto crypto = new ContactCrypto(new FieldEncryptionService(AES_KEY, "k7"));
        String stored = crypto.encrypt("+260977123456").block();
        assertThat(stored).startsWith("k7.").doesNotContain("977123456");
        assertThat(crypto.decrypt(stored).block()).isEqualTo("+260977123456");
        // a fresh IV every time
        assertThat(crypto.encrypt("+260977123456").block()).isNotEqualTo(stored);
    }

    @Test
    @DisplayName("tampering, a foreign key, another key id and garbage all fail without echoing the input")
    void cryptoFailures() {
        ContactCrypto crypto = new ContactCrypto(new FieldEncryptionService(AES_KEY, "k7"));
        String stored = crypto.encrypt("jane@example.com").block();

        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "k7." + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> crypto.decrypt(tampered).block()).isInstanceOf(FieldEncryptionService.EncryptionException.class);

        ContactCrypto otherKey = new ContactCrypto(new FieldEncryptionService(FieldEncryptionService.generateKey(), "k7"));
        assertThatThrownBy(() -> otherKey.decrypt(stored).block()).isInstanceOf(FieldEncryptionService.EncryptionException.class);

        ContactCrypto otherId = new ContactCrypto(new FieldEncryptionService(AES_KEY, "k8"));
        assertThatThrownBy(() -> otherId.decrypt(stored).block())
                .isInstanceOf(FieldEncryptionService.EncryptionException.class)
                .hasMessageNotContaining("jane");

        assertThatThrownBy(() -> crypto.decrypt("no-dot-here").block()).isInstanceOf(FieldEncryptionService.EncryptionException.class);
        assertThat(crypto.decrypt(null).block()).isNull();
    }

    @Test
    @DisplayName("FieldEncryptionService decrypts what it encrypts and exposes its key id")
    void fieldEncryptionDecrypt() {
        FieldEncryptionService service = new FieldEncryptionService(AES_KEY);
        assertThat(service.keyId()).isEqualTo("v1");
        assertThat(service.decrypt(service.encrypt("secret").block()).block()).isEqualTo("secret");
        assertThatThrownBy(() -> new FieldEncryptionService(AES_KEY, "bad.id")).isInstanceOf(IllegalArgumentException.class);
    }
}
