package com.pml.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Strict contact parsing, hashing and masking (CONTRACT 3). Pure functions: L1. */
@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · contact normalisation, keys and masks")
class ContactNormalisationTest {

    @ParameterizedTest
    @ValueSource(strings = {"0977123456", "+260977123456", "260977123456", "00260977123456",
            " +260 97 712 3456 ", "097-712-3456"})
    @DisplayName("every spelling of one Zambian mobile number yields the same E.164")
    void zambianSpellingsAgree(String raw) {
        assertThat(PhoneNumbers.parseMobile(raw, null))
                .contains(new PhoneNumbers.Parsed("+260977123456", "ZM"));
    }

    @Test
    @DisplayName("a foreign mobile with a country code is valid")
    void foreignMobile() {
        assertThat(PhoneNumbers.parseMobile("+44 7400 123456", null))
                .contains(new PhoneNumbers.Parsed("+447400123456", "GB"));
    }

    @Test
    @DisplayName("a regionHint lets a national number parse")
    void regionHint() {
        assertThat(PhoneNumbers.parseMobile("07400 123456", "GB").map(PhoneNumbers.Parsed::e164))
                .contains("+447400123456");
    }

    @Test
    @DisplayName("without country code or hint, a non-Zambian national number is refused")
    void noCountryNoHint() {
        assertThat(PhoneNumbers.parseMobile("7700900123", null)).isEmpty();
        assertThat(PhoneNumbers.parseMobile("07400123456", null)).isEmpty(); // 11 digits, not ZM local
    }

    @Test
    @DisplayName("a Guernsey mobile passes a GB allowlist (same +44 plan)")
    void sharedCallingCode() {
        assertThat(PhoneNumbers.parseMobile("+447911123456", null, List.of("GB"))).isPresent();
    }

    @Test
    @DisplayName("landlines are refused")
    void landline() {
        assertThat(PhoneNumbers.parseMobile("+260211123456", null)).isEmpty();
        assertThat(PhoneNumbers.parseMobile("+442071838750", null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "12345", "+", "+26097712", "call me", "1-800-FLOWERS",
            "john@example.com", "+260977123456789012345678901234567890123"})
    @DisplayName("garbage is refused")
    void garbage(String raw) {
        assertThat(PhoneNumbers.parseMobile(raw, null)).isEmpty();
        assertThat(PhoneNumbers.parseMobile(null, null)).isEmpty();
    }

    @Test
    @DisplayName("the caller's allowlist is applied")
    void allowlist() {
        assertThat(PhoneNumbers.parseMobile("+447400123456", null, List.of("ZM", "gb"))).isPresent();
        assertThat(PhoneNumbers.parseMobile("+447400123456", null, List.of("ZM"))).isEmpty();
    }

    @Test
    @DisplayName("emails are trimmed, lower-cased, NFC-normalised")
    void emails() {
        assertThat(Emails.normalize("  John.Doe@Gmail.COM ")).contains("john.doe@gmail.com");
        assertThat(Emails.normalize("café@example.com")).isEmpty(); // non-ASCII local part
        assertThat(Emails.normalize("a@b")).isEmpty();
        assertThat(Emails.normalize("a b@example.com")).isEmpty();
        assertThat(Emails.normalize("@example.com")).isEmpty();
        assertThat(Emails.normalize("..a@example.com")).isEmpty();
        assertThat(Emails.normalize(null)).isEmpty();
        assertThat(Emails.normalize("a".repeat(250) + "@x.co")).isEmpty();
    }

    @Test
    @DisplayName("contact keys are stable, typed, keyed and hex")
    void keys() {
        ContactKeys k = new ContactKeys("k1");
        String a = k.contactKey("EMAIL", "a@b.co");
        assertThat(a).matches("[0-9a-f]{64}");
        assertThat(k.contactKey("email", "a@b.co")).isEqualTo(a);
        assertThat(k.contactKey("WHATSAPP", "a@b.co")).isNotEqualTo(a);
        assertThat(new ContactKeys("k2").contactKey("EMAIL", "a@b.co")).isNotEqualTo(a);
        assertThat(a).doesNotContain("a@b");
        // RFC 4231 test case 2 pins the HMAC primitive.
        assertThat(ContactKeys.hmacHex("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
        assertThatThrownBy(() -> new ContactKeys(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("masks never reveal the whole contact")
    void masks() {
        assertThat(ContactMasking.maskPhone("+260977123456")).isEqualTo("+260 97* ***456");
        assertThat(ContactMasking.maskEmail("john@gmail.com")).isEqualTo("j***@gmail.com");
        assertThat(ContactMasking.mask("EMAIL", "john@gmail.com")).isEqualTo("j***@gmail.com");
        assertThat(ContactMasking.maskPhone("garbage")).isEqualTo("***");
        assertThat(ContactMasking.maskEmail("nope")).isEqualTo("***");
    }
}
