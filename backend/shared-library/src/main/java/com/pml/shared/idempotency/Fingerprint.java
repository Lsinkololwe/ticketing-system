package com.pml.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A SHA-256 digest of a request's canonical form, used to tell a genuine retry from a different
 * request that happens to reuse a key.
 *
 * <p>Canonical means two requests that say the same thing digest identically whatever the order
 * their fields were written in: map entries are sorted by name at every depth, list order is kept
 * (it is meaningful), and every scalar is tagged with its type so the number {@code 1} and the
 * string {@code "1"} cannot collide.</p>
 *
 * <p>The idempotency key itself is always left out, and so is every name in the caller's
 * {@code varying} set. A client may legitimately resend a retry with a fresh {@code clientTimestamp}
 * or {@code deviceId}; those fields are listed explicitly by the operation instead of being
 * dropped by omission, so a field that should change the fingerprint cannot be forgotten into
 * the exclusion list by accident.</p>
 */
public final class Fingerprint {

    /** The explicit allowlist of fields a client may legitimately change between retries of one request. */
    public static final Set<String> CLIENT_VARYING = Set.of("clientTimestamp", "deviceId");

    private static final String KEY_FIELD = "idempotencyKey";

    private Fingerprint() {
    }

    @SuppressWarnings("unchecked")
    public static String of(ObjectMapper mapper, Object request, Set<String> varying) {
        Object tree = request instanceof Map<?, ?> ? request : mapper.convertValue(request, Map.class);
        StringBuilder canonical = new StringBuilder();
        append(canonical, tree, varying, true);
        return digest(canonical.toString());
    }

    private static void append(StringBuilder out, Object value, Set<String> varying, boolean root) {
        if (value == null) {
            out.append("n;");
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((name, member) -> sorted.put(String.valueOf(name), member));
            out.append("{");
            sorted.forEach((name, member) -> {
                if (root && (KEY_FIELD.equals(name) || varying.contains(name))) {
                    return;
                }
                out.append(name.length()).append(':').append(name).append('=');
                append(out, member, varying, false);
            });
            out.append("}");
        } else if (value instanceof List<?> list) {
            out.append("[");
            list.forEach(member -> append(out, member, varying, false));
            out.append("]");
        } else {
            String text = String.valueOf(value);
            out.append(value.getClass().getSimpleName().charAt(0)).append(text.length()).append(':').append(text).append(';');
        }
    }

    private static String digest(String canonical) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", impossible);
        }
    }
}
