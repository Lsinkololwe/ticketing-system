package com.pml.catalog.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cursor encoding/decoding utilities for Relay pagination.
 * Uses Base64 encoding for opaque cursors.
 */
public final class CursorUtils {

    private static final String CURSOR_PREFIX = "cursor:";

    private CursorUtils() {
        // Utility class
    }

    /**
     * Encode an ID to a cursor string
     *
     * @param id the document ID to encode
     * @return Base64 encoded cursor string
     */
    public static String encodeCursor(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String value = CURSOR_PREFIX + id;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode a cursor string to an ID
     *
     * @param cursor the Base64 encoded cursor
     * @return the decoded document ID, or null if invalid
     */
    public static String decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith(CURSOR_PREFIX)) {
                return decoded.substring(CURSOR_PREFIX.length());
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
