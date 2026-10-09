package com.pml.catalog.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * An opaque cursor that names a position by {@code (createdAt, id)}, for a feed ordered newest
 * first. Unlike an offset it does not move when a row is added at the front while the caller pages.
 *
 * <p>A cursor a caller forges reaches nothing they could not page to: it is a position in their own
 * scoped query, never a key to anyone else's rows.
 */
public final class KeysetCursor {

    private static final String PREFIX = "keyset:";

    private KeysetCursor() {
    }

    public record Position(Instant createdAt, String id) {
    }

    public static String encode(Instant createdAt, String id) {
        String raw = PREFIX + (createdAt == null ? 0L : createdAt.toEpochMilli()) + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** The position, or empty when the text is not a cursor this class issued. */
    public static Optional<Position> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith(PREFIX)) {
                return Optional.empty();
            }
            String body = raw.substring(PREFIX.length());
            int colon = body.indexOf(':');
            if (colon <= 0 || colon == body.length() - 1) {
                return Optional.empty();
            }
            return Optional.of(new Position(Instant.ofEpochMilli(Long.parseLong(body.substring(0, colon))),
                    body.substring(colon + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
