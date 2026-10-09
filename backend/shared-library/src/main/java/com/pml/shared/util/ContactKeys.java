package com.pml.shared.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code contactKey = HMAC-SHA256(hashKey, TYPE + ":" + normalized)} as lower-case hex (CONTRACT 3).
 * Used for uniqueness, Redis keys and workflow ids. The raw value never appears in the key.
 */
public final class ContactKeys {

    private final byte[] key;

    public ContactKeys(String hashKey) {
        if (hashKey == null || hashKey.isBlank()) {
            throw new IllegalArgumentException("contact hash key must not be blank");
        }
        this.key = hashKey.getBytes(StandardCharsets.UTF_8);
    }

    /** @param type WHATSAPP or EMAIL (case-insensitive); @param normalized an already-normalised value */
    public String contactKey(String type, String normalized) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(normalized, "normalized");
        return hmacHex(key, type.toUpperCase(Locale.ROOT) + ":" + normalized);
    }

    /** HMAC-SHA256 of {@code data} under {@code key}, lower-case hex. */
    public static String hmacHex(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String hmacHex(String key, String data) {
        return hmacHex(key.getBytes(StandardCharsets.UTF_8), data);
    }
}
