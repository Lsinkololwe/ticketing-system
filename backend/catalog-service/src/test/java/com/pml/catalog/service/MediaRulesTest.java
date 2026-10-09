package com.pml.catalog.service;

import com.pml.shared.error.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What makes an upload an image the platform will store: the bytes decide, not the name. */
@Tag("L1")
@Tag("ET-CAT-004")
@DisplayName("ET-CAT-004-R8 · an upload is checked by its bytes")
public class MediaRulesTest {

    public static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1);
    public static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D);
    public static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P');

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static List<String> paths(List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::path).toList();
    }

    @Test
    @DisplayName("JPEG, PNG and WebP are recognised from their first bytes")
    void sniffs() {
        assertThat(MediaRules.sniff(JPEG)).contains("image/jpeg");
        assertThat(MediaRules.sniff(PNG)).contains("image/png");
        assertThat(MediaRules.sniff(WEBP)).contains("image/webp");
    }

    @Test
    @DisplayName("a script called picture.png is not an image")
    void aScriptIsNotAnImage() {
        byte[] script = "<script>alert(1)</script>".getBytes();

        assertThat(MediaRules.sniff(script)).isEmpty();
        assertThat(paths(MediaRules.check("picture.png", "image/png", script, "input.contentBase64")))
                .containsExactly("input.contentBase64");
    }

    @Test
    @DisplayName("a JPEG declared as a PNG is refused, naming the type")
    void declaredTypeMustAgree() {
        List<FieldViolation> violations = MediaRules.check("a.png", "image/png", JPEG, "input.contentBase64");

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.path()).isEqualTo("input.contentType");
            assertThat(v.constraint()).contains("image/jpeg");
        });
    }

    @Test
    @DisplayName("an unsupported declared type is refused")
    void unsupportedType() {
        assertThat(paths(MediaRules.check("a.gif", "image/gif", JPEG, "input.contentBase64")))
                .contains("input.contentType");
        assertThat(MediaRules.accepts("IMAGE/PNG")).isTrue();
        assertThat(MediaRules.accepts("image/svg+xml")).isFalse();
    }

    @Test
    @DisplayName("an empty file and a file over 5 MB are refused")
    void sizeBounds() {
        assertThat(paths(MediaRules.check("a.png", "image/png", new byte[0], "p"))).containsExactly("p");
        assertThat(paths(MediaRules.check("a.png", "image/png", new byte[MediaRules.MAX_BYTES + 1], "p"))).containsExactly("p");
    }

    @Test
    @DisplayName("a missing or overlong file name is refused")
    void fileName() {
        assertThat(paths(MediaRules.check(" ", "image/png", PNG, "p"))).containsExactly("input.fileName");
        assertThat(paths(MediaRules.check("x".repeat(256), "image/png", PNG, "p"))).containsExactly("input.fileName");
    }

    @Test
    @DisplayName("base64 decodes strictly, tolerates whitespace and a data URL prefix, and refuses text too long to be 5 MB")
    void decoding() {
        String encoded = Base64.getEncoder().encodeToString(PNG);

        assertThat(MediaRules.decode(encoded)).hasValue(PNG);
        assertThat(MediaRules.decode(encoded.substring(0, 4) + "\n" + encoded.substring(4))).hasValue(PNG);
        assertThat(MediaRules.decode("data:image/png;base64," + encoded)).hasValue(PNG);
        assertThat(MediaRules.decode("not base64 !!")).isEmpty();
        assertThat(MediaRules.decode("A".repeat(MediaRules.MAX_BASE64_CHARS + 4))).isEmpty();
        assertThat(MediaRules.decode(null)).isEmpty();
    }

    @Test
    @DisplayName("undecodable input is reported at the field, once")
    void undecodableIsReported() {
        assertThat(paths(MediaRules.check("a.png", "image/png", null, "input.contentBase64")))
                .containsExactly("input.contentBase64");
    }
}
