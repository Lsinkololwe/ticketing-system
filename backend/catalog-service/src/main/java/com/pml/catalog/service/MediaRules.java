package com.pml.catalog.service;

import com.pml.shared.error.FieldViolation;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What makes an upload an image this platform will store. Pure: bytes and names in, violations out.
 *
 * <p>The bytes are checked, never the name or the declared type alone: a file called {@code a.png}
 * that begins with a script is refused, and so is a JPEG declared as a PNG.
 */
public final class MediaRules {

    /** The largest picture accepted: 5 MB. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** The base64 text of {@link #MAX_BYTES}, plus padding, which bounds what is decoded. */
    public static final int MAX_BASE64_CHARS = (MAX_BYTES / 3 + 1) * 4;

    /** Declared type → extensions that agree with it. */
    private static final Map<String, List<String>> TYPES = Map.of(
            "image/jpeg", List.of("jpg", "jpeg"),
            "image/png", List.of("png"),
            "image/webp", List.of("webp"));

    private MediaRules() {
    }

    public static boolean accepts(String contentType) {
        return contentType != null && TYPES.containsKey(contentType.toLowerCase(Locale.ROOT));
    }

    /** What the leading bytes say the file is, whatever it is called. */
    public static Optional<String> sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return Optional.of("image/png");
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    /**
     * Decodes the request's base64, refusing before it allocates when the text is too long to be
     * within {@link #MAX_BYTES}. Empty when the text is not base64.
     */
    public static Optional<byte[]> decode(String base64) {
        if (base64 == null || base64.length() > MAX_BASE64_CHARS) {
            return Optional.empty();
        }
        String text = base64.startsWith("data:") && base64.indexOf(',') > 0
                ? base64.substring(base64.indexOf(',') + 1)
                : base64;
        try {
            return Optional.of(Base64.getDecoder().decode(text.replaceAll("\\s", "")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Every reason {@code bytes} cannot be stored as {@code fileName} of {@code contentType}. */
    public static List<FieldViolation> check(String fileName, String contentType, byte[] bytes, String base64Path) {
        List<FieldViolation> violations = new ArrayList<>();
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            violations.add(new FieldViolation("input.fileName", "must be 1 to 255 characters"));
        }
        if (!accepts(contentType)) {
            violations.add(new FieldViolation("input.contentType", "must be image/jpeg, image/png or image/webp"));
        }
        if (bytes == null) {
            violations.add(new FieldViolation(base64Path, "must be base64 of at most 5 MB"));
            return violations;
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            violations.add(new FieldViolation(base64Path, "must be between 1 byte and 5 MB"));
            return violations;
        }
        Optional<String> actual = sniff(bytes);
        if (actual.isEmpty()) {
            violations.add(new FieldViolation(base64Path, "is not a JPEG, PNG or WebP image"));
        } else if (accepts(contentType) && !actual.get().equals(contentType.toLowerCase(Locale.ROOT))) {
            violations.add(new FieldViolation("input.contentType", "says " + contentType + " but the bytes are " + actual.get()));
        }
        return violations;
    }
}
